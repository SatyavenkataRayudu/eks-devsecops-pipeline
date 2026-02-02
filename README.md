# EKS DevSecOps CI/CD Pipeline

A production-ready CI/CD pipeline for deploying Java Spring Boot applications to Amazon EKS with comprehensive security scanning, monitoring, and infrastructure as code.

## Architecture Overview

```
Developer → Git → Jenkins → Maven Build → Unit Tests → SonarQube → Quality Gate → 
Trivy Scan → Nexus → Docker Build → Image Scan → ECR → EKS Deploy → Monitoring
```

## Components

- **Application**: Java Spring Boot REST API
- **CI/CD**: Jenkins declarative pipeline
- **Infrastructure**: Terraform (VPC, EKS, ECR, ALB)
- **Security**: Trivy, SonarQube, KubeAudit
- **Monitoring**: Prometheus, Grafana
- **Registry**: Nexus (artifacts), ECR (images)

## Quick Start

1. Deploy infrastructure: `cd terraform && terraform apply`
2. Configure Jenkins with provided pipeline
3. Push code to trigger deployment
4. Access monitoring at Grafana endpoint

## Project Structure

```
eks-devsecops-pipeline/
├── app/                    # Spring Boot application
├── Jenkinsfile            # CI/CD pipeline
├── terraform/             # Infrastructure as Code
├── docker/                # Docker configurations
├── k8s/                   # Kubernetes manifests
├── helm/                  # Helm charts for monitoring
├── scripts/               # Deployment scripts
├── monitoring/            # Grafana dashboards
├── security/              # Security policies
└── delivery-documentation/ # Step-by-step delivery guide
```

## Documentation

- [Manual Delivery Guide](./delivery-documentation/README.md)
- [Deployment Guide](./DEPLOYMENT.md)

## Prerequisites

- AWS CLI v2.x
- Terraform >= 1.0
- kubectl >= 1.28
- Helm >= 3.12
- Docker >= 20.x
- Java 17
- Maven 3.9.x

## Getting Started

1. Clone the repository
2. Follow the [step-by-step delivery guide](./delivery-documentation/README.md)
3. Or use the quick deployment: `./scripts/deploy-infrastructure.sh`

## License

MIT License