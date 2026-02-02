# Step 01: GitHub Repository Setup

## Overview
This step covers creating a GitHub repository for the EKS DevSecOps project and setting up the initial repository structure.

## Objectives
- Create a new GitHub repository
- Configure repository settings
- Set up branch protection rules
- Initialize local development environment

## Prerequisites
- GitHub account
- Git installed locally
- SSH key configured with GitHub (recommended)

## Step-by-Step Implementation

### 1. Create GitHub Repository

#### Via GitHub Web Interface:
1. Navigate to [GitHub](https://github.com)
2. Click "New repository" or go to https://github.com/new
3. Fill in repository details:
   - **Repository name**: `eks-devsecops-pipeline`
   - **Description**: `Production-ready CI/CD pipeline for deploying Java Spring Boot applications to Amazon EKS with comprehensive security scanning and monitoring`
   - **Visibility**: Public (or Private based on requirements)
   - **Initialize with**: 
     - ✅ Add a README file
     - ✅ Add .gitignore (choose Java template)
     - ✅ Choose a license (MIT recommended)

#### Via GitHub CLI (Alternative):
```bash
# Install GitHub CLI if not already installed
# Windows: winget install GitHub.cli
# Mac: brew install gh
# Linux: See https://cli.github.com/

# Authenticate with GitHub
gh auth login

# Create repository
gh repo create eks-devsecops-pipeline \
  --description "Production-ready CI/CD pipeline for deploying Java Spring Boot applications to Amazon EKS" \
  --public \
  --add-readme \
  --gitignore Java \
  --license MIT
```

### 2. Clone Repository Locally

```bash
# Clone using HTTPS
git clone https://github.com/YOUR_USERNAME/eks-devsecops-pipeline.git

# Or clone using SSH (recommended)
git clone git@github.com:YOUR_USERNAME/eks-devsecops-pipeline.git

# Navigate to project directory
cd eks-devsecops-pipeline
```

### 3. Configure Git Settings

```bash
# Set up git configuration (if not already done)
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

# Verify configuration
git config --list
```

### 4. Set Up Branch Protection Rules

#### Via GitHub Web Interface:
1. Go to repository → Settings → Branches
2. Click "Add rule" for main branch
3. Configure protection rules:
   - ✅ Require pull request reviews before merging
   - ✅ Require status checks to pass before merging
   - ✅ Require branches to be up to date before merging
   - ✅ Include administrators
   - ✅ Allow force pushes (for initial setup only)

### 5. Create Development Branch

```bash
# Create and switch to development branch
git checkout -b develop

# Push development branch to remote
git push -u origin develop

# Switch back to main
git checkout main
```

## Verification Steps

### 1. Verify Repository Creation
- Repository is accessible at `https://github.com/YOUR_USERNAME/eks-devsecops-pipeline`
- Repository has proper description and visibility settings
- README.md is present and displays correctly

### 2. Verify Local Setup
```bash
# Check git status
git status

# Verify remote configuration
git remote -v

# Check branch setup
git branch -a
```

## Interview Questions & Answers

### Q1: Why did you choose this repository structure?
**Answer**: "I organized the repository following industry best practices with clear separation of concerns:
- `app/` contains the Spring Boot application following Maven standard directory layout
- `terraform/` holds Infrastructure as Code for reproducible deployments
- `k8s/` contains Kubernetes manifests for container orchestration
- `docker/` has containerization configurations
- `scripts/` includes automation scripts for deployment and maintenance
- `monitoring/` and `security/` separate observability and security concerns
This structure makes the project maintainable, scalable, and easy for team collaboration."

### Q2: How do you ensure code quality and security from the repository level?
**Answer**: "I implement several measures:
1. **Branch Protection Rules**: Require pull request reviews and status checks
2. **Comprehensive .gitignore**: Prevent sensitive files and build artifacts from being committed
3. **Clear Documentation**: README and DEPLOYMENT guides for team onboarding
4. **Structured Directories**: Logical organization prevents configuration drift
5. **Version Control Best Practices**: Meaningful commit messages and proper branching strategy"

## Next Steps

After completing this step:
1. Repository is ready for development
2. Team members can clone and contribute
3. CI/CD pipeline can be configured to trigger on repository events
4. Ready to proceed to [Step 02: Project Structure Creation](../step-02-project-structure/)

## Commands Summary

```bash
# Repository creation and setup
gh repo create eks-devsecops-pipeline --public --add-readme --gitignore Java --license MIT
git clone git@github.com:YOUR_USERNAME/eks-devsecops-pipeline.git
cd eks-devsecops-pipeline

# Initial structure setup
mkdir -p app/src/{main,test}/java/com/company/booksapi
mkdir -p docker k8s terraform scripts monitoring helm security

# Git configuration
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

# Initial commit
git add .
git commit -m "Initial project structure setup"
git push origin main
```

This completes Step 01: GitHub Repository Setup. The repository is now ready for development and the next phase of the project.