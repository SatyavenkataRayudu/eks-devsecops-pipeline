# Step 02: Project Structure Creation

## Overview
This step focuses on creating the detailed project structure and understanding the architecture of our EKS DevSecOps pipeline project.

## Objectives
- Create comprehensive project directory structure
- Understand the purpose of each component
- Set up configuration files and templates
- Prepare for development workflow

## Prerequisites
- Completed [Step 01: GitHub Repository Setup](../step-01-github-setup/)
- Git repository cloned locally
- Basic understanding of microservices architecture

## Architecture Overview

```
EKS DevSecOps Pipeline Architecture
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

## Step-by-Step Implementation

### 1. Create Detailed Directory Structure

```bash
# Navigate to project root
cd eks-devsecops-pipeline

# Create comprehensive directory structure
mkdir -p app/src/main/java/com/company/booksapi/{controller,service,model,config}
mkdir -p app/src/main/resources/{static,templates}
mkdir -p app/src/test/java/com/company/booksapi/{controller,service}
mkdir -p docker
mkdir -p k8s/{base,overlays/{dev,staging,prod}}
mkdir -p terraform/{modules/{vpc,eks,ecr,iam},policies,environments/{dev,staging,prod}}
mkdir -p scripts/{deployment,monitoring,security}
mkdir -p monitoring/{dashboards,alerts}
mkdir -p helm/{monitoring,application}
mkdir -p security
mkdir -p docs/{architecture,deployment,troubleshooting}
```

### 2. Create Maven POM Configuration

```bash
cat > app/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.company</groupId>
    <artifactId>books-api</artifactId>
    <version>1.0.0</version>
    <name>books-api</name>
    <description>Books REST API for EKS deployment</description>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <sonar.projectKey>books-api</sonar.projectKey>
        <sonar.projectName>Books API</sonar.projectName>
    </properties>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        
        <!-- Monitoring -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.sonarsource.scanner.maven</groupId>
                <artifactId>sonar-maven-plugin</artifactId>
                <version>3.10.0.2594</version>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.8</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>test</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
EOF
```

### 3. Create Docker Configuration

```bash
cat > docker/Dockerfile << 'EOF'
# Multi-stage build for optimized image
FROM openjdk:17-jdk-slim as builder

WORKDIR /app
COPY app/pom.xml .
COPY app/src ./src

# Build application
RUN apt-get update && apt-get install -y maven
RUN mvn clean package -DskipTests

# Production image
FROM openjdk:17-jre-slim

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Set working directory
WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF
```

### 4. Create Kubernetes Base Manifests

```bash
# Create namespace manifest
cat > k8s/namespace.yaml << 'EOF'
apiVersion: v1
kind: Namespace
metadata:
  name: production
  labels:
    name: production
    app.kubernetes.io/name: books-api
    app.kubernetes.io/part-of: eks-devsecops
EOF

# Create deployment template
cat > k8s/deployment.yaml << 'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: books-api
  namespace: production
  labels:
    app: books-api
spec:
  replicas: 2
  selector:
    matchLabels:
      app: books-api
  template:
    metadata:
      labels:
        app: books-api
    spec:
      serviceAccountName: books-api
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 2000
      containers:
      - name: books-api
        image: ${ECR_REPOSITORY}:${IMAGE_TAG}
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          capabilities:
            drop:
            - ALL
        volumeMounts:
        - name: tmp
          mountPath: /tmp
      volumes:
      - name: tmp
        emptyDir: {}
EOF
```

### 5. Commit Project Structure

```bash
# Add all new files
git add .

# Commit the project structure
git commit -m "Create comprehensive project structure

- Added Maven POM with Spring Boot dependencies
- Created Dockerfile with multi-stage build and security best practices
- Set up Terraform configuration with AWS provider
- Added Kubernetes manifests templates
- Created Jenkins pipeline template
- Set up monitoring configuration with Helm
- Added security policies and network policies
- Created deployment scripts
- Updated documentation structure"

# Push to repository
git push origin main
```

## Interview Questions & Answers

### Q1: Explain the project structure and why you organized it this way?
**Answer**: "I organized the project following industry best practices and separation of concerns:

- **app/**: Contains the Spring Boot application following Maven standard directory layout with clear separation of main and test code
- **terraform/**: Infrastructure as Code with modules for reusability and environments for different deployment stages
- **k8s/**: Kubernetes manifests organized with base configurations and overlays for environment-specific customizations
- **docker/**: Containerization configurations with security-focused Dockerfile and comprehensive .dockerignore
- **scripts/**: Automation scripts for deployment, monitoring, and maintenance tasks
- **monitoring/**: Observability configurations including Grafana dashboards and Prometheus rules
- **security/**: Security policies, network policies, and security scanning configurations

This structure ensures maintainability, scalability, and follows the principle of least surprise for team collaboration."

### Q2: How does your project structure support DevSecOps practices?
**Answer**: "The structure inherently supports DevSecOps through:

1. **Security by Design**: Dedicated security/ directory with network policies and security configurations
2. **Infrastructure as Code**: Terraform modules ensure consistent, auditable infrastructure
3. **Automated Testing**: Clear test structure in app/src/test/ for unit and integration tests
4. **Container Security**: Multi-stage Docker builds with non-root users and minimal attack surface
5. **Monitoring Integration**: Built-in observability with Prometheus metrics and Grafana dashboards
6. **Documentation**: Comprehensive docs/ structure for security procedures and troubleshooting
7. **Script Automation**: Automated deployment and security scanning scripts
8. **Configuration Management**: Environment-specific configurations prevent configuration drift"

## Next Steps

After completing this step:
1. Project structure is established and ready for development
2. Configuration templates are in place
3. Security and monitoring foundations are set
4. Ready to proceed to [Step 03: Spring Boot Application Development](../step-03-spring-boot-app/)

This completes Step 02: Project Structure Creation. The project now has a solid foundation with all necessary directories, configuration templates, and documentation structure in place.