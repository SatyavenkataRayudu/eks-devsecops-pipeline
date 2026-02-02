#!/bin/bash

# EKS DevSecOps Pipeline - Full Deployment Script
# This script automates the complete deployment of the EKS DevSecOps pipeline

set -e

echo "=========================================="
echo "EKS DevSecOps Pipeline - Full Deployment"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check if required tools are installed
    local tools=("aws" "terraform" "kubectl" "docker" "helm")
    local missing_tools=()
    
    for tool in "${tools[@]}"; do
        if ! command -v $tool &> /dev/null; then
            missing_tools+=($tool)
        fi
    done
    
    if [ ${#missing_tools[@]} -ne 0 ]; then
        print_error "Missing required tools: ${missing_tools[*]}"
        print_error "Please install missing tools and run again"
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        print_error "AWS credentials not configured"
        print_error "Please run 'aws configure' and try again"
        exit 1
    fi
    
    print_success "All prerequisites met"
}

# Step 1: AWS Prerequisites Setup
setup_aws_prerequisites() {
    print_status "Step 1: Setting up AWS prerequisites..."
    
    # Set variables
    export AWS_REGION=${AWS_REGION:-"us-west-2"}
    export BUCKET_NAME="eks-devsecops-terraform-state-$(date +%s)"
    export DYNAMODB_TABLE="terraform-state-lock"
    
    print_status "Creating S3 bucket for Terraform state: $BUCKET_NAME"
    
    # Create S3 bucket
    aws s3 mb s3://$BUCKET_NAME --region $AWS_REGION
    
    # Enable versioning
    aws s3api put-bucket-versioning \
        --bucket $BUCKET_NAME \
        --versioning-configuration Status=Enabled
    
    # Enable encryption
    aws s3api put-bucket-encryption \
        --bucket $BUCKET_NAME \
        --server-side-encryption-configuration '{
            "Rules": [
                {
                    "ApplyServerSideEncryptionByDefault": {
                        "SSEAlgorithm": "AES256"
                    }
                }
            ]
        }'
    
    # Block public access
    aws s3api put-public-access-block \
        --bucket $BUCKET_NAME \
        --public-access-block-configuration \
        BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
    
    print_status "Creating DynamoDB table for state locking: $DYNAMODB_TABLE"
    
    # Create DynamoDB table
    aws dynamodb create-table \
        --table-name $DYNAMODB_TABLE \
        --attribute-definitions \
            AttributeName=LockID,AttributeType=S \
        --key-schema \
            AttributeName=LockID,KeyType=HASH \
        --provisioned-throughput \
            ReadCapacityUnits=5,WriteCapacityUnits=5 \
        --region $AWS_REGION
    
    # Wait for table to be active
    aws dynamodb wait table-exists --table-name $DYNAMODB_TABLE --region $AWS_REGION
    
    # Update backend configuration
    cat > terraform/backend.tf << EOF
terraform {
  backend "s3" {
    bucket         = "$BUCKET_NAME"
    key            = "eks-devsecops/terraform.tfstate"
    region         = "$AWS_REGION"
    encrypt        = true
    dynamodb_table = "$DYNAMODB_TABLE"
  }
}
EOF
    
    # Create environment file
    cat > .env << EOF
AWS_REGION=$AWS_REGION
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
TERRAFORM_BUCKET=$BUCKET_NAME
TERRAFORM_DYNAMODB_TABLE=$DYNAMODB_TABLE
ECR_REPOSITORY=$(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com/books-api
EKS_CLUSTER_NAME=eks-devsecops-dev-cluster
APP_NAME=books-api
K8S_NAMESPACE=production
EOF
    
    print_success "AWS prerequisites setup completed"
}

# Step 2: Deploy Infrastructure
deploy_infrastructure() {
    print_status "Step 2: Deploying infrastructure with Terraform..."
    
    cd terraform
    
    # Initialize Terraform
    print_status "Initializing Terraform..."
    terraform init
    
    # Validate configuration
    print_status "Validating Terraform configuration..."
    terraform validate
    
    # Plan deployment
    print_status "Planning infrastructure deployment..."
    terraform plan -var="project_name=eks-devsecops" -var="environment=dev"
    
    # Apply deployment
    print_status "Applying infrastructure deployment..."
    terraform apply -var="project_name=eks-devsecops" -var="environment=dev" -auto-approve
    
    # Save outputs
    terraform output -json > ../terraform-outputs.json
    
    cd ..
    
    print_success "Infrastructure deployment completed"
}

# Step 3: Configure kubectl
configure_kubectl() {
    print_status "Step 3: Configuring kubectl..."
    
    # Update kubeconfig
    aws eks update-kubeconfig --region us-west-2 --name eks-devsecops-dev-cluster
    
    # Verify cluster access
    kubectl get nodes
    
    # Create namespace
    kubectl create namespace production --dry-run=client -o yaml | kubectl apply -f -
    
    print_success "kubectl configured successfully"
}

# Step 4: Build and Push Application
build_and_push_app() {
    print_status "Step 4: Building and pushing application..."
    
    # Load environment variables
    source .env
    
    # Build application
    print_status "Building Spring Boot application..."
    cd app
    mvn clean package -DskipTests
    cd ..
    
    # Build Docker image
    print_status "Building Docker image..."
    docker build -t $ECR_REPOSITORY:latest -f docker/Dockerfile .
    
    # Login to ECR
    print_status "Logging in to ECR..."
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REPOSITORY
    
    # Push image
    print_status "Pushing image to ECR..."
    docker push $ECR_REPOSITORY:latest
    
    print_success "Application built and pushed successfully"
}

# Step 5: Deploy Application to EKS
deploy_to_eks() {
    print_status "Step 5: Deploying application to EKS..."
    
    # Load environment variables
    source .env
    
    # Deploy application
    export ECR_REPOSITORY=$ECR_REPOSITORY
    export IMAGE_TAG="latest"
    export APP_NAME=$APP_NAME
    export K8S_NAMESPACE=$K8S_NAMESPACE
    
    envsubst < k8s/deployment.yaml | kubectl apply -f -
    kubectl apply -f k8s/service.yaml
    
    # Wait for deployment
    kubectl rollout status deployment/$APP_NAME -n $K8S_NAMESPACE --timeout=300s
    
    # Get deployment info
    kubectl get pods -n $K8S_NAMESPACE
    kubectl get services -n $K8S_NAMESPACE
    
    print_success "Application deployed to EKS successfully"
}

# Step 6: Verify Deployment
verify_deployment() {
    print_status "Step 6: Verifying deployment..."
    
    # Wait for pods to be ready
    kubectl wait --for=condition=ready pod -l app=$APP_NAME -n $K8S_NAMESPACE --timeout=300s
    
    # Port forward for testing
    kubectl port-forward service/${APP_NAME}-service 8080:80 -n $K8S_NAMESPACE &
    PF_PID=$!
    
    sleep 10
    
    # Test API endpoints
    print_status "Testing API endpoints..."
    
    if curl -f http://localhost:8080/api/books/health; then
        print_success "Health check passed"
    else
        print_error "Health check failed"
    fi
    
    if curl -f http://localhost:8080/api/books; then
        print_success "Books API accessible"
    else
        print_error "Books API not accessible"
    fi
    
    # Kill port forward
    kill $PF_PID
    
    print_success "Deployment verification completed"
}

# Step 7: Setup Monitoring (Optional)
setup_monitoring() {
    print_status "Step 7: Setting up monitoring (optional)..."
    
    # Add Prometheus Helm repo
    helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
    helm repo update
    
    # Install Prometheus and Grafana
    helm install prometheus prometheus-community/kube-prometheus-stack \
        --namespace monitoring \
        --create-namespace \
        --set grafana.adminPassword=admin123
    
    print_success "Monitoring setup completed"
    print_status "Grafana will be available at: kubectl port-forward service/prometheus-grafana 3000:80 -n monitoring"
}

# Main execution
main() {
    print_status "Starting EKS DevSecOps Pipeline deployment..."
    
    # Check if user wants to proceed
    read -p "This will deploy the complete EKS DevSecOps pipeline. Continue? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_warning "Deployment cancelled by user"
        exit 0
    fi
    
    # Execute deployment steps
    check_prerequisites
    setup_aws_prerequisites
    deploy_infrastructure
    configure_kubectl
    build_and_push_app
    deploy_to_eks
    verify_deployment
    
    # Ask about monitoring
    read -p "Do you want to set up monitoring (Prometheus/Grafana)? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        setup_monitoring
    fi
    
    print_success "=========================================="
    print_success "EKS DevSecOps Pipeline deployment completed!"
    print_success "=========================================="
    
    # Display access information
    echo
    print_status "Access Information:"
    echo "• EKS Cluster: eks-devsecops-dev-cluster"
    echo "• Application Namespace: production"
    echo "• ECR Repository: $ECR_REPOSITORY"
    echo "• API Health Check: kubectl port-forward service/books-api-service 8080:80 -n production"
    echo "• Then access: http://localhost:8080/api/books/health"
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "• Grafana: kubectl port-forward service/prometheus-grafana 3000:80 -n monitoring"
        echo "• Grafana Login: admin/admin123"
    fi
    
    echo
    print_status "Next steps:"
    echo "1. Set up Jenkins for CI/CD (see step-07-jenkins-setup)"
    echo "2. Configure SonarQube for code quality (see step-08-sonarqube-integration)"
    echo "3. Set up additional security scanning tools"
    echo "4. Configure production monitoring and alerting"
}

# Execute main function
main "$@"