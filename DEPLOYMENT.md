# EKS DevSecOps Pipeline - Deployment Guide

## Quick Start

For a complete automated deployment, run:

```bash
chmod +x scripts/deploy-full-pipeline.sh
./scripts/deploy-full-pipeline.sh
```

## Manual Step-by-Step Deployment

Follow the detailed documentation in the `delivery-documentation/` folder:

### Phase 1: Project Setup & Repository ✅
- [Step 01: GitHub Repository Setup](./delivery-documentation/step-01-github-setup/) ✅
- [Step 02: Project Structure Creation](./delivery-documentation/step-02-project-structure/) ✅
- [Step 03: Spring Boot Application Development](./delivery-documentation/step-03-spring-boot-app/) ✅

### Phase 2: Infrastructure as Code
- [Step 04: AWS Prerequisites Setup](./delivery-documentation/step-04-aws-prerequisites/) ✅
- [Step 05: Terraform Infrastructure](./delivery-documentation/step-05-terraform-infrastructure/) ✅
- Step 06: EKS Cluster Deployment (covered in Step 05)

### Phase 3: CI/CD Pipeline
- [Step 07: Jenkins Setup](./delivery-documentation/step-07-jenkins-setup/) ✅
- Step 08: SonarQube Integration
- Step 09: Nexus Repository Setup
- Step 10: Jenkins Pipeline Configuration

### Phase 4: Security Implementation
- Step 11: Trivy Security Scanning
- Step 12: Container Security
- Step 13: Kubernetes Security

### Phase 5: Containerization & Deployment
- Step 14: Docker Configuration (covered in Step 03)
- Step 15: ECR Repository Setup (covered in Step 05)
- Step 16: Kubernetes Manifests (covered in Step 03)

### Phase 6: Monitoring & Observability
- Step 17: Prometheus Setup
- Step 18: Grafana Configuration
- Step 19: Application Monitoring

### Phase 7: Testing & Validation
- Step 20: End-to-End Testing
- Step 21: Security Validation
- Step 22: Performance Testing

### Phase 8: Production Deployment
- Step 23: Production Deployment
- Step 24: Monitoring Validation
- Step 25: Documentation & Handover

## Prerequisites

- AWS Account with administrative access
- AWS CLI v2.x configured
- Terraform >= 1.0
- kubectl >= 1.28
- Docker >= 20.x
- Helm >= 3.12
- Java 17
- Maven 3.9.x
- Git

## Architecture Overview

```
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐
│   Developer     │───▶│    GitHub    │───▶│    Jenkins      │
│   Workstation   │    │  Repository  │    │   CI/CD Server  │
└─────────────────┘    └──────────────┘    └─────────────────┘
                                                     │
                                                     ▼
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐
│   SonarQube     │◀───│    Maven     │───▶│     Nexus       │
│ Quality Gateway │    │    Build     │    │   Repository    │
└─────────────────┘    └──────────────┘    └─────────────────┘
                                                     │
                                                     ▼
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐
│     Trivy       │◀───│    Docker    │───▶│      ECR        │
│ Security Scanner│    │    Build     │    │ Container Reg.  │
└─────────────────┘    └──────────────┘    └─────────────────┘
                                                     │
                                                     ▼
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐
│   Prometheus    │◀───│     EKS      │◀───│   Terraform     │
│   Monitoring    │    │   Cluster    │    │ Infrastructure  │
└─────────────────┘    └──────────────┘    └─────────────────┘
```

## Key Components

### Infrastructure
- **VPC**: Multi-AZ setup with public and private subnets
- **EKS Cluster**: Managed Kubernetes cluster with worker nodes
- **ECR**: Container registry for Docker images
- **S3**: Terraform state storage
- **DynamoDB**: Terraform state locking

### Application
- **Spring Boot**: REST API with Books management
- **Docker**: Multi-stage containerization
- **Kubernetes**: Deployment manifests with security contexts

### CI/CD Pipeline
- **Jenkins**: Automated build and deployment
- **Maven**: Build and dependency management
- **SonarQube**: Code quality analysis
- **Trivy**: Security vulnerability scanning

### Monitoring
- **Prometheus**: Metrics collection
- **Grafana**: Visualization and dashboards
- **Spring Actuator**: Application health checks

## Security Features

- **Network Security**: Private subnets, security groups
- **Container Security**: Non-root users, read-only filesystems
- **Image Security**: ECR scanning, Trivy vulnerability checks
- **Access Control**: IAM roles, RBAC
- **Secrets Management**: Kubernetes secrets, AWS Secrets Manager
- **Code Quality**: SonarQube quality gates

## Monitoring & Observability

- **Application Metrics**: Prometheus metrics via Micrometer
- **Infrastructure Monitoring**: CloudWatch integration
- **Log Aggregation**: EKS control plane logs
- **Health Checks**: Kubernetes liveness and readiness probes
- **Alerting**: Grafana alerts and notifications

## Cost Optimization

- **Instance Types**: t3.medium for development (easily scalable)
- **Auto Scaling**: EKS managed node groups with auto scaling
- **Resource Limits**: Kubernetes resource requests and limits
- **Image Lifecycle**: ECR lifecycle policies for image cleanup
- **Spot Instances**: Can be configured for non-production workloads

## Disaster Recovery

- **Multi-AZ Deployment**: High availability across availability zones
- **Backup Strategy**: S3 versioning for Terraform state
- **Database Backup**: Application data backup strategies
- **Infrastructure as Code**: Complete infrastructure reproducibility

## Getting Started

1. **Clone the repository**:
   ```bash
   git clone https://github.com/SatyavenkataRayudu/eks-devsecops-pipeline.git
   cd eks-devsecops-pipeline
   ```

2. **Configure AWS credentials**:
   ```bash
   aws configure
   ```

3. **Run the automated deployment**:
   ```bash
   chmod +x scripts/deploy-full-pipeline.sh
   ./scripts/deploy-full-pipeline.sh
   ```

4. **Or follow manual steps**:
   Start with [Step 01: GitHub Repository Setup](./delivery-documentation/step-01-github-setup/)

## Troubleshooting

### Common Issues

1. **AWS Permissions**: Ensure your AWS user has sufficient permissions
2. **Tool Versions**: Verify all required tools are installed and up-to-date
3. **Resource Limits**: Check AWS service limits for your account
4. **Network Connectivity**: Ensure proper VPC and security group configuration

### Support

- Check the troubleshooting section in each step's documentation
- Review AWS CloudWatch logs for infrastructure issues
- Check Jenkins build logs for CI/CD pipeline issues
- Use `kubectl logs` for application debugging

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.