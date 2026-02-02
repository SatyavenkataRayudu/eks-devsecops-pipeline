# Step 04: AWS Prerequisites Setup

## Overview
This step covers setting up AWS prerequisites including IAM users, roles, and initial configurations required for the EKS DevSecOps pipeline.

## Objectives
- Set up AWS CLI and configure credentials
- Create IAM users and roles for EKS
- Configure S3 bucket for Terraform state
- Set up DynamoDB for state locking
- Verify AWS permissions and access

## Prerequisites
- AWS Account with administrative access
- Completed [Step 03: Spring Boot Application Development](../step-03-spring-boot-app/)
- Basic understanding of AWS IAM

## Step-by-Step Implementation

### 1. Install and Configure AWS CLI

#### Install AWS CLI v2
```bash
# Windows (using winget)
winget install Amazon.AWSCLI

# macOS (using Homebrew)
brew install awscli

# Linux (using curl)
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
```

#### Configure AWS CLI
```bash
# Configure AWS credentials
aws configure

# Enter the following when prompted:
# AWS Access Key ID: [Your Access Key]
# AWS Secret Access Key: [Your Secret Key]
# Default region name: us-west-2
# Default output format: json

# Verify configuration
aws sts get-caller-identity
```

### 2. Create IAM User for EKS Operations

#### Create IAM User via AWS CLI
```bash
# Create IAM user for EKS operations
aws iam create-user --user-name eks-admin

# Create access key for the user
aws iam create-access-key --user-name eks-admin

# Attach necessary policies
aws iam attach-user-policy --user-name eks-admin --policy-arn arn:aws:iam::aws:policy/AmazonEKSClusterPolicy
aws iam attach-user-policy --user-name eks-admin --policy-arn arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy
aws iam attach-user-policy --user-name eks-admin --policy-arn arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy
aws iam attach-user-policy --user-name eks-admin --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
```

#### Create Custom IAM Policy for EKS Admin
```bash
cat > eks-admin-policy.json << 'EOF'
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "eks:*",
                "ec2:*",
                "iam:*",
                "cloudformation:*",
                "autoscaling:*",
                "elasticloadbalancing:*",
                "ecr:*",
                "logs:*",
                "s3:*",
                "dynamodb:*"
            ],
            "Resource": "*"
        }
    ]
}
EOF

# Create the policy
aws iam create-policy --policy-name EKSAdminPolicy --policy-document file://eks-admin-policy.json

# Attach the policy to the user
aws iam attach-user-policy --user-name eks-admin --policy-arn arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):policy/EKSAdminPolicy
```

### 3. Set Up S3 Bucket for Terraform State

```bash
# Set variables
export AWS_REGION="us-west-2"
export BUCKET_NAME="eks-devsecops-terraform-state-$(date +%s)"
export DYNAMODB_TABLE="terraform-state-lock"

# Create S3 bucket for Terraform state
aws s3 mb s3://$BUCKET_NAME --region $AWS_REGION

# Enable versioning on the bucket
aws s3api put-bucket-versioning \
    --bucket $BUCKET_NAME \
    --versioning-configuration Status=Enabled

# Enable server-side encryption
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

echo "S3 Bucket created: $BUCKET_NAME"
```

### 4. Create DynamoDB Table for State Locking

```bash
# Create DynamoDB table for Terraform state locking
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

echo "DynamoDB table created: $DYNAMODB_TABLE"
```

### 5. Create ECR Repository

```bash
# Create ECR repository for the application
aws ecr create-repository \
    --repository-name books-api \
    --region $AWS_REGION

# Get ECR login token and login to Docker
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com

echo "ECR repository created: books-api"
```

### 6. Update Terraform Backend Configuration

```bash
# Update terraform backend configuration
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
```

### 7. Create Environment Variables File

```bash
# Create environment variables file
cat > .env << EOF
# AWS Configuration
AWS_REGION=$AWS_REGION
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Terraform State
TERRAFORM_BUCKET=$BUCKET_NAME
TERRAFORM_DYNAMODB_TABLE=$DYNAMODB_TABLE

# ECR Configuration
ECR_REPOSITORY=$(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com/books-api

# EKS Configuration
EKS_CLUSTER_NAME=eks-devsecops-cluster
EKS_NODE_GROUP_NAME=eks-devsecops-nodes

# Application Configuration
APP_NAME=books-api
K8S_NAMESPACE=production
EOF

echo "Environment variables saved to .env file"
```

### 8. Install Required Tools

#### Install kubectl
```bash
# Windows
curl -LO "https://dl.k8s.io/release/v1.28.0/bin/windows/amd64/kubectl.exe"

# macOS
curl -LO "https://dl.k8s.io/release/v1.28.0/bin/darwin/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# Linux
curl -LO "https://dl.k8s.io/release/v1.28.0/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# Verify installation
kubectl version --client
```

#### Install Terraform
```bash
# Windows (using Chocolatey)
choco install terraform

# macOS (using Homebrew)
brew install terraform

# Linux
wget https://releases.hashicorp.com/terraform/1.6.0/terraform_1.6.0_linux_amd64.zip
unzip terraform_1.6.0_linux_amd64.zip
sudo mv terraform /usr/local/bin/

# Verify installation
terraform version
```

#### Install Helm
```bash
# Windows (using Chocolatey)
choco install kubernetes-helm

# macOS (using Homebrew)
brew install helm

# Linux
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Verify installation
helm version
```

### 9. Verify AWS Setup

```bash
# Create verification script
cat > scripts/verify-aws-setup.sh << 'EOF'
#!/bin/bash

echo "=== AWS Setup Verification ==="

# Check AWS CLI
echo "1. AWS CLI Version:"
aws --version

# Check AWS Identity
echo "2. AWS Identity:"
aws sts get-caller-identity

# Check S3 bucket
echo "3. S3 Bucket:"
aws s3 ls | grep terraform-state

# Check DynamoDB table
echo "4. DynamoDB Table:"
aws dynamodb describe-table --table-name terraform-state-lock --query 'Table.TableStatus'

# Check ECR repository
echo "5. ECR Repository:"
aws ecr describe-repositories --repository-names books-api --query 'repositories[0].repositoryName'

# Check required tools
echo "6. Required Tools:"
echo "   kubectl: $(kubectl version --client --short 2>/dev/null || echo 'Not installed')"
echo "   terraform: $(terraform version -json 2>/dev/null | jq -r '.terraform_version' || echo 'Not installed')"
echo "   helm: $(helm version --short 2>/dev/null || echo 'Not installed')"
echo "   docker: $(docker --version 2>/dev/null || echo 'Not installed')"

echo "=== Verification Complete ==="
EOF

chmod +x scripts/verify-aws-setup.sh
./scripts/verify-aws-setup.sh
```

### 10. Create AWS Resource Cleanup Script

```bash
cat > scripts/cleanup-aws-resources.sh << 'EOF'
#!/bin/bash

echo "=== AWS Resource Cleanup ==="

# Load environment variables
source .env

# Delete ECR repository
echo "Deleting ECR repository..."
aws ecr delete-repository --repository-name books-api --force --region $AWS_REGION

# Delete DynamoDB table
echo "Deleting DynamoDB table..."
aws dynamodb delete-table --table-name $TERRAFORM_DYNAMODB_TABLE --region $AWS_REGION

# Empty and delete S3 bucket
echo "Emptying and deleting S3 bucket..."
aws s3 rm s3://$TERRAFORM_BUCKET --recursive
aws s3 rb s3://$TERRAFORM_BUCKET

# Delete IAM user and policies
echo "Deleting IAM resources..."
aws iam detach-user-policy --user-name eks-admin --policy-arn arn:aws:iam::aws:policy/AmazonEKSClusterPolicy
aws iam detach-user-policy --user-name eks-admin --policy-arn arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy
aws iam detach-user-policy --user-name eks-admin --policy-arn arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy
aws iam detach-user-policy --user-name eks-admin --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
aws iam detach-user-policy --user-name eks-admin --policy-arn arn:aws:iam::$AWS_ACCOUNT_ID:policy/EKSAdminPolicy

aws iam delete-access-key --user-name eks-admin --access-key-id $(aws iam list-access-keys --user-name eks-admin --query 'AccessKeyMetadata[0].AccessKeyId' --output text)
aws iam delete-user --user-name eks-admin
aws iam delete-policy --policy-arn arn:aws:iam::$AWS_ACCOUNT_ID:policy/EKSAdminPolicy

echo "=== Cleanup Complete ==="
EOF

chmod +x scripts/cleanup-aws-resources.sh
```

### 11. Commit AWS Configuration

```bash
# Add environment file to .gitignore (contains sensitive info)
echo ".env" >> .gitignore
echo "eks-admin-policy.json" >> .gitignore

# Add other files
git add terraform/backend.tf scripts/verify-aws-setup.sh scripts/cleanup-aws-resources.sh

git commit -m "Add AWS prerequisites setup

- Created Terraform backend configuration with S3 and DynamoDB
- Added AWS setup verification script
- Created cleanup script for AWS resources
- Configured ECR repository for container images
- Set up IAM roles and policies for EKS operations"

git push origin main
```

## Verification Steps

### 1. Verify AWS CLI Configuration
```bash
aws sts get-caller-identity
aws configure list
```

### 2. Verify S3 Bucket
```bash
aws s3 ls | grep terraform-state
aws s3api get-bucket-versioning --bucket $BUCKET_NAME
```

### 3. Verify DynamoDB Table
```bash
aws dynamodb describe-table --table-name terraform-state-lock
```

### 4. Verify ECR Repository
```bash
aws ecr describe-repositories --repository-names books-api
```

### 5. Verify Tools Installation
```bash
aws --version
kubectl version --client
terraform version
helm version
docker --version
```

## Common Issues & Troubleshooting

### Issue 1: AWS CLI Not Configured
**Problem**: AWS CLI returns "Unable to locate credentials"
**Solution**:
```bash
aws configure
# Or set environment variables
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_DEFAULT_REGION=us-west-2
```

### Issue 2: Insufficient Permissions
**Problem**: Access denied errors when creating resources
**Solution**: Ensure your AWS user has administrative permissions or the specific permissions listed in the EKSAdminPolicy

### Issue 3: S3 Bucket Name Already Exists
**Problem**: Bucket name is not globally unique
**Solution**: Use a unique suffix like timestamp:
```bash
BUCKET_NAME="eks-devsecops-terraform-state-$(date +%s)"
```

## Interview Questions & Answers

### Q1: Why do you use S3 and DynamoDB for Terraform state management?
**Answer**: "I use S3 and DynamoDB for Terraform state management because:

1. **S3 for State Storage**: Provides durable, highly available storage for Terraform state files with versioning enabled for rollback capabilities
2. **DynamoDB for State Locking**: Prevents concurrent Terraform operations that could corrupt the state file
3. **Security**: S3 encryption at rest and proper IAM policies ensure state file security
4. **Team Collaboration**: Centralized state allows multiple team members to work on the same infrastructure
5. **Backup and Recovery**: S3 versioning provides automatic backup of state file changes"

### Q2: What IAM permissions are required for EKS operations?
**Answer**: "For EKS operations, I configure these key permissions:

1. **EKS Cluster Management**: AmazonEKSClusterPolicy for creating and managing EKS clusters
2. **Worker Node Management**: AmazonEKSWorkerNodePolicy for managing worker nodes
3. **Networking**: AmazonEKS_CNI_Policy for pod networking
4. **Container Registry**: ECR permissions for pushing/pulling container images
5. **Infrastructure**: EC2, VPC, IAM permissions for underlying infrastructure
6. **Monitoring**: CloudWatch permissions for logging and monitoring

I follow the principle of least privilege, granting only necessary permissions for each role."

### Q3: How do you ensure security in your AWS setup?
**Answer**: "I implement multiple security layers:

1. **IAM Best Practices**: Separate users for different functions, minimal required permissions
2. **S3 Security**: Bucket encryption, versioning, and public access blocking
3. **State File Protection**: DynamoDB locking prevents concurrent modifications
4. **Access Control**: Proper IAM policies and roles for service-to-service communication
5. **Audit Trail**: CloudTrail logging for all API calls
6. **Network Security**: VPC configuration with private subnets for worker nodes
7. **Secrets Management**: Using AWS Secrets Manager for sensitive data"

## Next Steps

After completing this step:
1. AWS environment is configured with proper permissions
2. Terraform backend is set up for state management
3. ECR repository is ready for container images
4. All required tools are installed and verified
5. Ready to proceed to [Step 05: Terraform Infrastructure](../step-05-terraform-infrastructure/)

## Commands Summary

```bash
# AWS CLI setup
aws configure
aws sts get-caller-identity

# Create S3 bucket and DynamoDB table
aws s3 mb s3://$BUCKET_NAME --region $AWS_REGION
aws dynamodb create-table --table-name terraform-state-lock ...

# Create ECR repository
aws ecr create-repository --repository-name books-api

# Verify setup
./scripts/verify-aws-setup.sh

# Commit changes
git add terraform/backend.tf scripts/
git commit -m "Add AWS prerequisites setup"
git push origin main
```

This completes Step 04: AWS Prerequisites Setup. The AWS environment is now ready for Terraform infrastructure deployment.