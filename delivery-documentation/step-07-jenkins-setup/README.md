# Step 07: Jenkins Setup

## Overview
This step covers setting up Jenkins on EC2 with all necessary plugins and configurations for the DevSecOps pipeline.

## Objectives
- Deploy Jenkins on EC2 instance
- Install and configure required plugins
- Set up Jenkins credentials and security
- Configure Jenkins for EKS deployment
- Create Jenkins pipeline job

## Prerequisites
- Completed [Step 05: Terraform Infrastructure](../step-05-terraform-infrastructure/)
- EKS cluster running and accessible
- AWS CLI configured
- Basic understanding of Jenkins

## Step-by-Step Implementation

### 1. Create Jenkins EC2 Instance

#### Create Jenkins Instance via Terraform
```bash
cat > terraform/jenkins.tf << 'EOF'
# Security Group for Jenkins
resource "aws_security_group" "jenkins" {
  name_prefix = "${local.cluster_name}-jenkins-sg"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Jenkins Web UI"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Jenkins Agent"
    from_port   = 50000
    to_port     = 50000
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.cluster_name}-jenkins-sg"
  })
}

# IAM Role for Jenkins
resource "aws_iam_role" "jenkins" {
  name = "${local.cluster_name}-jenkins-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = local.common_tags
}

# IAM Policy for Jenkins
resource "aws_iam_policy" "jenkins" {
  name        = "${local.cluster_name}-jenkins-policy"
  description = "IAM policy for Jenkins"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "eks:DescribeCluster",
          "eks:ListClusters",
          "ecr:GetAuthorizationToken",
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = "*"
      }
    ]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "jenkins" {
  policy_arn = aws_iam_policy.jenkins.arn
  role       = aws_iam_role.jenkins.name
}

# Instance Profile for Jenkins
resource "aws_iam_instance_profile" "jenkins" {
  name = "${local.cluster_name}-jenkins-profile"
  role = aws_iam_role.jenkins.name

  tags = local.common_tags
}

# Key Pair for Jenkins
resource "aws_key_pair" "jenkins" {
  key_name   = "${local.cluster_name}-jenkins-key"
  public_key = file("~/.ssh/id_rsa.pub") # Make sure this exists

  tags = local.common_tags
}

# Jenkins EC2 Instance
resource "aws_instance" "jenkins" {
  ami                    = "ami-0c02fb55956c7d316" # Amazon Linux 2
  instance_type          = "t3.medium"
  key_name              = aws_key_pair.jenkins.key_name
  vpc_security_group_ids = [aws_security_group.jenkins.id]
  subnet_id             = aws_subnet.public[0].id
  iam_instance_profile  = aws_iam_instance_profile.jenkins.name

  user_data = base64encode(templatefile("${path.module}/jenkins-userdata.sh", {
    cluster_name = local.cluster_name
    aws_region   = var.aws_region
  }))

  root_block_device {
    volume_type = "gp3"
    volume_size = 30
    encrypted   = true
  }

  tags = merge(local.common_tags, {
    Name = "${local.cluster_name}-jenkins"
  })
}

# Elastic IP for Jenkins
resource "aws_eip" "jenkins" {
  instance = aws_instance.jenkins.id
  domain   = "vpc"

  tags = merge(local.common_tags, {
    Name = "${local.cluster_name}-jenkins-eip"
  })
}
EOF
```

### 2. Create Jenkins User Data Script

```bash
cat > terraform/jenkins-userdata.sh << 'EOF'
#!/bin/bash
yum update -y

# Install Java 17
yum install -y java-17-amazon-corretto-devel

# Install Jenkins
wget -O /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key
yum install -y jenkins

# Install Docker
yum install -y docker
systemctl start docker
systemctl enable docker
usermod -a -G docker jenkins

# Install kubectl
curl -LO "https://dl.k8s.io/release/v1.28.0/bin/linux/amd64/kubectl"
chmod +x kubectl
mv kubectl /usr/local/bin/

# Install AWS CLI v2
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
./aws/install

# Install Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Install Trivy
curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin

# Install Maven
yum install -y maven

# Configure AWS CLI for Jenkins user
mkdir -p /var/lib/jenkins/.aws
cat > /var/lib/jenkins/.aws/config << EOL
[default]
region = ${aws_region}
output = json
EOL

# Configure kubectl for Jenkins user
mkdir -p /var/lib/jenkins/.kube
aws eks update-kubeconfig --region ${aws_region} --name ${cluster_name} --kubeconfig /var/lib/jenkins/.kube/config
chown -R jenkins:jenkins /var/lib/jenkins/.kube
chown -R jenkins:jenkins /var/lib/jenkins/.aws

# Start Jenkins
systemctl start jenkins
systemctl enable jenkins

# Install Jenkins plugins
sleep 60
JENKINS_PASSWORD=$(cat /var/lib/jenkins/secrets/initialAdminPassword)

# Create Jenkins CLI jar download script
cat > /tmp/install-plugins.sh << 'PLUGIN_SCRIPT'
#!/bin/bash
cd /var/lib/jenkins
wget http://localhost:8080/jnlpJars/jenkins-cli.jar

# Wait for Jenkins to be ready
while ! curl -s http://localhost:8080/login > /dev/null; do
  echo "Waiting for Jenkins to start..."
  sleep 10
done

# Install plugins
java -jar jenkins-cli.jar -s http://localhost:8080/ -auth admin:$JENKINS_PASSWORD install-plugin \
  blueocean \
  docker-pipeline \
  kubernetes \
  kubernetes-cli \
  pipeline-stage-view \
  git \
  github \
  maven-plugin \
  sonar \
  build-timeout \
  credentials-binding \
  timestamper \
  ws-cleanup \
  ant \
  gradle \
  workflow-aggregator \
  pipeline-github-lib \
  ssh-slaves \
  matrix-auth \
  pam-auth \
  ldap \
  email-ext \
  mailer \
  prometheus \
  htmlpublisher \
  junit \
  jacoco \
  performance \
  publish-over-ssh

# Restart Jenkins
java -jar jenkins-cli.jar -s http://localhost:8080/ -auth admin:$JENKINS_PASSWORD restart
PLUGIN_SCRIPT

chmod +x /tmp/install-plugins.sh
su - jenkins -c "JENKINS_PASSWORD=$JENKINS_PASSWORD /tmp/install-plugins.sh"

# Create Jenkins configuration as code
mkdir -p /var/lib/jenkins/casc_configs
cat > /var/lib/jenkins/casc_configs/jenkins.yaml << 'CASC_EOF'
jenkins:
  systemMessage: "EKS DevSecOps Jenkins Server"
  numExecutors: 2
  mode: NORMAL
  scmCheckoutRetryCount: 3
  
  securityRealm:
    local:
      allowsSignup: false
      users:
        - id: "admin"
          password: "admin123"
          
  authorizationStrategy:
    globalMatrix:
      permissions:
        - "Overall/Administer:admin"
        - "Overall/Read:authenticated"

  remotingSecurity:
    enabled: true

credentials:
  system:
    domainCredentials:
      - credentials:
          - usernamePassword:
              scope: GLOBAL
              id: "github-credentials"
              username: "your-github-username"
              password: "your-github-token"
              description: "GitHub credentials"

tool:
  git:
    installations:
      - name: "Default"
        home: "/usr/bin/git"
  
  maven:
    installations:
      - name: "Maven-3.9.5"
        home: "/usr/share/maven"
  
  jdk:
    installations:
      - name: "OpenJDK-17"
        home: "/usr/lib/jvm/java-17-amazon-corretto"

unclassified:
  location:
    url: "http://jenkins.example.com:8080/"
    adminAddress: "admin@example.com"
    
  sonarGlobalConfiguration:
    installations:
      - name: "SonarQube"
        serverUrl: "http://sonarqube.example.com:9000"
        credentialsId: "sonar-token"
CASC_EOF

chown -R jenkins:jenkins /var/lib/jenkins/casc_configs

# Set Jenkins configuration as code environment variable
echo 'CASC_JENKINS_CONFIG=/var/lib/jenkins/casc_configs/jenkins.yaml' >> /etc/sysconfig/jenkins

# Restart Jenkins to apply configuration
systemctl restart jenkins

echo "Jenkins installation completed!"
EOF
```

### 3. Deploy Jenkins Infrastructure

```bash
# Generate SSH key if not exists
if [ ! -f ~/.ssh/id_rsa ]; then
    ssh-keygen -t rsa -b 4096 -f ~/.ssh/id_rsa -N ""
fi

# Apply Terraform changes
cd terraform
terraform apply -var="project_name=eks-devsecops" -var="environment=dev" -auto-approve

# Get Jenkins public IP
JENKINS_IP=$(terraform output -raw jenkins_public_ip)
echo "Jenkins will be available at: http://$JENKINS_IP:8080"
```

### 4. Configure Jenkins Security

#### Create Jenkins Security Configuration Script
```bash
cat > scripts/configure-jenkins.sh << 'EOF'
#!/bin/bash

JENKINS_URL="http://$1:8080"
JENKINS_USER="admin"
JENKINS_PASSWORD="admin123"

echo "Configuring Jenkins at $JENKINS_URL"

# Wait for Jenkins to be ready
echo "Waiting for Jenkins to be ready..."
while ! curl -s "$JENKINS_URL/login" > /dev/null; do
  echo "Jenkins not ready yet, waiting..."
  sleep 10
done

echo "Jenkins is ready!"

# Create credentials for GitHub
curl -X POST "$JENKINS_URL/credentials/store/system/domain/_/createCredentials" \
  --user "$JENKINS_USER:$JENKINS_PASSWORD" \
  --data-urlencode 'json={
    "": "0",
    "credentials": {
      "scope": "GLOBAL",
      "id": "github-credentials",
      "username": "your-github-username",
      "password": "your-github-token",
      "description": "GitHub credentials",
      "$class": "com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl"
    }
  }'

# Create credentials for AWS
curl -X POST "$JENKINS_URL/credentials/store/system/domain/_/createCredentials" \
  --user "$JENKINS_USER:$JENKINS_PASSWORD" \
  --data-urlencode 'json={
    "": "0",
    "credentials": {
      "scope": "GLOBAL",
      "id": "aws-credentials",
      "accessKey": "your-aws-access-key",
      "secretKey": "your-aws-secret-key",
      "description": "AWS credentials",
      "$class": "com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl"
    }
  }'

# Create credentials for SonarQube
curl -X POST "$JENKINS_URL/credentials/store/system/domain/_/createCredentials" \
  --user "$JENKINS_USER:$JENKINS_PASSWORD" \
  --data-urlencode 'json={
    "": "0",
    "credentials": {
      "scope": "GLOBAL",
      "id": "sonar-token",
      "secret": "your-sonar-token",
      "description": "SonarQube token",
      "$class": "org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl"
    }
  }'

echo "Jenkins configuration completed!"
EOF

chmod +x scripts/configure-jenkins.sh
```

### 5. Create Jenkins Pipeline Job

#### Create Pipeline Job via Jenkins CLI
```bash
cat > jenkins-jobs/books-api-pipeline.xml << 'EOF'
<?xml version='1.1' encoding='UTF-8'?>
<flow-definition plugin="workflow-job@2.40">
  <actions/>
  <description>EKS DevSecOps Pipeline for Books API</description>
  <keepDependencies>false</keepDependencies>
  <properties>
    <org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>
      <triggers>
        <com.cloudbees.jenkins.GitHubPushTrigger plugin="github@1.34.1">
          <spec></spec>
        </com.cloudbees.jenkins.GitHubPushTrigger>
      </triggers>
    </org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>
  </properties>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps@2.92">
    <scm class="hudson.plugins.git.GitSCM" plugin="git@4.8.3">
      <configVersion>2</configVersion>
      <userRemoteConfigs>
        <hudson.plugins.git.UserRemoteConfig>
          <url>https://github.com/SatyavenkataRayudu/eks-devsecops-pipeline.git</url>
          <credentialsId>github-credentials</credentialsId>
        </hudson.plugins.git.UserRemoteConfig>
      </userRemoteConfigs>
      <branches>
        <hudson.plugins.git.BranchSpec>
          <name>*/main</name>
        </hudson.plugins.git.BranchSpec>
      </branches>
      <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
      <submoduleCfg class="list"/>
      <extensions/>
    </scm>
    <scriptPath>Jenkinsfile</scriptPath>
    <lightweight>true</lightweight>
  </definition>
  <triggers/>
  <disabled>false</disabled>
</flow-definition>
EOF
```

### 6. Update Jenkinsfile for Complete Pipeline

```bash
cat > Jenkinsfile << 'EOF'
pipeline {
    agent any
    
    environment {
        // Application
        APP_NAME = 'books-api'
        APP_VERSION = "${BUILD_NUMBER}"
        
        // AWS
        AWS_REGION = 'us-west-2'
        EKS_CLUSTER_NAME = 'eks-devsecops-dev-cluster'
        AWS_ACCOUNT_ID = sh(script: 'aws sts get-caller-identity --query Account --output text', returnStdout: true).trim()
        ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        ECR_REPOSITORY = "${ECR_REGISTRY}/${APP_NAME}"
        
        // SonarQube
        SONAR_PROJECT_KEY = 'books-api'
        
        // Kubernetes
        K8S_NAMESPACE = 'production'
        
        // Tools
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository'
    }
    
    tools {
        maven 'Maven-3.9.5'
        jdk 'OpenJDK-17'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.IMAGE_TAG = "${BUILD_NUMBER}-${GIT_COMMIT_SHORT}"
                }
            }
        }
        
        stage('Tool Verification') {
            steps {
                script {
                    sh '''
                        echo "=== Tool Versions ==="
                        java -version
                        mvn -version
                        docker --version
                        kubectl version --client
                        trivy --version
                        aws --version
                        echo "====================="
                    '''
                }
            }
        }
        
        stage('Maven Build') {
            steps {
                dir('app') {
                    sh 'mvn clean compile'
                }
            }
        }
        
        stage('Unit Tests') {
            steps {
                dir('app') {
                    sh 'mvn test'
                }
            }
            post {
                always {
                    publishTestResults testResultsPattern: 'app/target/surefire-reports/*.xml'
                    publishCoverage adapters: [jacocoAdapter('app/target/site/jacoco/jacoco.xml')]
                }
            }
        }
        
        stage('SonarQube Analysis') {
            steps {
                dir('app') {
                    withSonarQubeEnv('SonarQube') {
                        sh 'mvn sonar:sonar -Dsonar.projectKey=${SONAR_PROJECT_KEY} -Dsonar.projectName="Books API"'
                    }
                }
            }
        }
        
        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        
        stage('Package Application') {
            steps {
                dir('app') {
                    sh 'mvn package -DskipTests'
                }
            }
            post {
                success {
                    archiveArtifacts artifacts: 'app/target/*.jar', fingerprint: true
                }
            }
        }
        
        stage('Build Docker Image') {
            steps {
                script {
                    sh """
                        docker build -t ${ECR_REPOSITORY}:${IMAGE_TAG} -f docker/Dockerfile .
                        docker tag ${ECR_REPOSITORY}:${IMAGE_TAG} ${ECR_REPOSITORY}:latest
                    """
                }
            }
        }
        
        stage('Security Scan - Trivy') {
            steps {
                script {
                    sh """
                        trivy image --format json --output trivy-report.json ${ECR_REPOSITORY}:${IMAGE_TAG}
                        trivy image --format table ${ECR_REPOSITORY}:${IMAGE_TAG}
                    """
                }
            }
            post {
                always {
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: '.',
                        reportFiles: 'trivy-report.json',
                        reportName: 'Trivy Security Report'
                    ])
                }
            }
        }
        
        stage('Push to ECR') {
            steps {
                script {
                    sh """
                        aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}
                        docker push ${ECR_REPOSITORY}:${IMAGE_TAG}
                        docker push ${ECR_REPOSITORY}:latest
                    """
                }
            }
        }
        
        stage('Deploy to EKS') {
            steps {
                script {
                    sh """
                        # Update kubeconfig
                        aws eks update-kubeconfig --region ${AWS_REGION} --name ${EKS_CLUSTER_NAME}
                        
                        # Create namespace if not exists
                        kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Deploy application
                        export ECR_REPOSITORY=${ECR_REPOSITORY}
                        export IMAGE_TAG=${IMAGE_TAG}
                        export APP_NAME=${APP_NAME}
                        export K8S_NAMESPACE=${K8S_NAMESPACE}
                        
                        envsubst < k8s/deployment.yaml | kubectl apply -f -
                        kubectl apply -f k8s/service.yaml
                        
                        # Wait for deployment
                        kubectl rollout status deployment/${APP_NAME} -n ${K8S_NAMESPACE} --timeout=300s
                        
                        # Get deployment info
                        kubectl get pods -n ${K8S_NAMESPACE}
                        kubectl get services -n ${K8S_NAMESPACE}
                    """
                }
            }
        }
        
        stage('Health Check') {
            steps {
                script {
                    sh """
                        # Wait for pods to be ready
                        kubectl wait --for=condition=ready pod -l app=${APP_NAME} -n ${K8S_NAMESPACE} --timeout=300s
                        
                        # Port forward for health check
                        kubectl port-forward service/${APP_NAME}-service 8080:80 -n ${K8S_NAMESPACE} &
                        PF_PID=\$!
                        
                        sleep 10
                        
                        # Health check
                        curl -f http://localhost:8080/api/books/health || exit 1
                        
                        # Kill port forward
                        kill \$PF_PID
                    """
                }
            }
        }
    }
    
    post {
        always {
            // Clean up Docker images
            sh """
                docker rmi ${ECR_REPOSITORY}:${IMAGE_TAG} || true
                docker rmi ${ECR_REPOSITORY}:latest || true
                docker system prune -f
            """
            
            // Clean workspace
            cleanWs()
        }
        
        success {
            echo "Pipeline completed successfully!"
            emailext (
                subject: "SUCCESS: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                body: "Good news! The pipeline completed successfully.",
                to: "admin@example.com"
            )
        }
        
        failure {
            echo "Pipeline failed!"
            emailext (
                subject: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                body: "Bad news! The pipeline failed. Please check the logs.",
                to: "admin@example.com"
            )
        }
    }
}
EOF
```

### 7. Create Jenkins Job Creation Script

```bash
cat > scripts/create-jenkins-job.sh << 'EOF'
#!/bin/bash

JENKINS_URL="http://$1:8080"
JENKINS_USER="admin"
JENKINS_PASSWORD="admin123"

echo "Creating Jenkins job at $JENKINS_URL"

# Create the pipeline job
curl -X POST "$JENKINS_URL/createItem?name=books-api-pipeline" \
  --user "$JENKINS_USER:$JENKINS_PASSWORD" \
  --header "Content-Type: application/xml" \
  --data-binary @jenkins-jobs/books-api-pipeline.xml

echo "Jenkins job created successfully!"

# Trigger initial build
curl -X POST "$JENKINS_URL/job/books-api-pipeline/build" \
  --user "$JENKINS_USER:$JENKINS_PASSWORD"

echo "Initial build triggered!"
EOF

chmod +x scripts/create-jenkins-job.sh
```

### 8. Add Jenkins Outputs to Terraform

```bash
cat >> terraform/outputs.tf << 'EOF'

output "jenkins_public_ip" {
  description = "Public IP address of Jenkins server"
  value       = aws_eip.jenkins.public_ip
}

output "jenkins_private_ip" {
  description = "Private IP address of Jenkins server"
  value       = aws_instance.jenkins.private_ip
}

output "jenkins_url" {
  description = "Jenkins URL"
  value       = "http://${aws_eip.jenkins.public_ip}:8080"
}

output "jenkins_ssh_command" {
  description = "SSH command to connect to Jenkins server"
  value       = "ssh -i ~/.ssh/id_rsa ec2-user@${aws_eip.jenkins.public_ip}"
}
EOF
```

### 9. Deploy and Configure Jenkins

```bash
# Apply Terraform changes
cd terraform
terraform apply -var="project_name=eks-devsecops" -var="environment=dev" -auto-approve

# Get Jenkins IP
JENKINS_IP=$(terraform output -raw jenkins_public_ip)
echo "Jenkins URL: http://$JENKINS_IP:8080"

# Wait for Jenkins to be ready (this may take 5-10 minutes)
echo "Waiting for Jenkins to be ready..."
while ! curl -s "http://$JENKINS_IP:8080/login" > /dev/null; do
  echo "Jenkins not ready yet, waiting..."
  sleep 30
done

# Configure Jenkins
cd ..
./scripts/configure-jenkins.sh $JENKINS_IP

# Create Jenkins job
./scripts/create-jenkins-job.sh $JENKINS_IP

echo "Jenkins setup completed!"
echo "Access Jenkins at: http://$JENKINS_IP:8080"
echo "Username: admin"
echo "Password: admin123"
```

### 10. Commit Jenkins Configuration

```bash
git add terraform/jenkins.tf terraform/jenkins-userdata.sh jenkins-jobs/ scripts/configure-jenkins.sh scripts/create-jenkins-job.sh Jenkinsfile

git commit -m "Add complete Jenkins setup

- Created Jenkins EC2 instance with security groups and IAM roles
- Added comprehensive user data script with all required tools
- Configured Jenkins with plugins and security settings
- Created complete CI/CD pipeline with all stages
- Added job creation and configuration scripts
- Integrated with EKS, ECR, and security scanning
- Added email notifications and health checks"

git push origin main
```

## Verification Steps

### 1. Verify Jenkins Installation
```bash
# Check Jenkins status
ssh -i ~/.ssh/id_rsa ec2-user@$JENKINS_IP "sudo systemctl status jenkins"

# Check installed tools
ssh -i ~/.ssh/id_rsa ec2-user@$JENKINS_IP "java -version && mvn -version && docker --version && kubectl version --client"
```

### 2. Verify Jenkins Web Interface
- Access Jenkins at `http://JENKINS_IP:8080`
- Login with admin/admin123
- Verify plugins are installed
- Check job creation

### 3. Verify Pipeline Execution
- Trigger the books-api-pipeline job
- Monitor build logs
- Verify all stages complete successfully

## Interview Questions & Answers

### Q1: Explain your Jenkins setup and why you chose this architecture?
**Answer**: "I set up Jenkins on EC2 with this architecture for several reasons:

1. **Scalability**: EC2 allows easy scaling up/down based on build requirements
2. **Integration**: Direct integration with AWS services (EKS, ECR) using IAM roles
3. **Security**: Isolated in VPC with security groups, IAM roles for service access
4. **Persistence**: EBS storage ensures build history and configuration persistence
5. **Cost-Effective**: Can be stopped when not in use, unlike managed services
6. **Flexibility**: Full control over Jenkins configuration and plugin management"

### Q2: How do you handle security in your Jenkins pipeline?
**Answer**: "Security is implemented at multiple levels:

1. **Infrastructure Security**: Security groups, IAM roles with least privilege
2. **Authentication**: Jenkins security realm with user management
3. **Credentials Management**: Jenkins credential store for sensitive data
4. **Container Security**: Trivy scanning for vulnerabilities
5. **Code Quality**: SonarQube integration with quality gates
6. **Network Security**: Private subnets, controlled access
7. **Image Security**: ECR image scanning, signed images"

### Q3: Describe your CI/CD pipeline stages and their purpose?
**Answer**: "My pipeline implements comprehensive DevSecOps practices:

1. **Checkout**: Source code retrieval with commit tracking
2. **Build**: Maven compilation and dependency resolution
3. **Test**: Unit tests with coverage reporting
4. **Quality Analysis**: SonarQube static code analysis
5. **Quality Gate**: Automated quality threshold enforcement
6. **Package**: JAR file creation and artifact archiving
7. **Container Build**: Docker image creation with optimization
8. **Security Scan**: Trivy vulnerability scanning
9. **Registry Push**: ECR image storage with tagging
10. **Deploy**: EKS deployment with health checks
11. **Verification**: Application health validation

Each stage has specific failure conditions and rollback capabilities."

## Next Steps

After completing this step:
1. Jenkins is deployed and configured
2. Complete CI/CD pipeline is operational
3. Integration with AWS services is working
4. Security scanning is integrated
5. Ready to proceed to [Step 08: SonarQube Integration](../step-08-sonarqube-integration/)

## Commands Summary

```bash
# Deploy Jenkins
cd terraform
terraform apply -auto-approve

# Get Jenkins IP and configure
JENKINS_IP=$(terraform output -raw jenkins_public_ip)
./scripts/configure-jenkins.sh $JENKINS_IP
./scripts/create-jenkins-job.sh $JENKINS_IP

# Access Jenkins
echo "Jenkins URL: http://$JENKINS_IP:8080"
echo "Username: admin, Password: admin123"

# Commit changes
git add terraform/ jenkins-jobs/ scripts/ Jenkinsfile
git commit -m "Add complete Jenkins setup"
git push origin main
```

This completes Step 07: Jenkins Setup. Jenkins is now ready to execute the complete DevSecOps pipeline for your application.