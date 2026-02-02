pipeline {
    agent any
    
    environment {
        // Application
        APP_NAME = 'books-api'
        APP_VERSION = "${BUILD_NUMBER}"
        
        // AWS
        AWS_REGION = 'us-west-2'
        EKS_CLUSTER_NAME = 'eks-devsecops-cluster'
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
        stage('Tool Install & Verification') {
            steps {
                script {
                    sh '''
                        echo "=== Tool Versions ==="
                        java -version
                        mvn -version
                        docker --version
                        kubectl version --client
                        trivy --version
                        echo "====================="
                    '''
                }
            }
        }
        
        stage('Checkout') {
            steps {
                checkout scm
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
                        sh 'mvn sonar:sonar'
                    }
                }
            }
        }
        
        stage('Quality Gate') {
            steps {
                timeout(time: 1, unit: 'HOURS') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        
        stage('Package') {
            steps {
                dir('app') {
                    sh 'mvn package -DskipTests'
                }
            }
        }
        
        stage('Docker Build') {
            steps {
                script {
                    sh "docker build -t ${ECR_REPOSITORY}:${BUILD_NUMBER} -f docker/Dockerfile ."
                    sh "docker tag ${ECR_REPOSITORY}:${BUILD_NUMBER} ${ECR_REPOSITORY}:latest"
                }
            }
        }
        
        stage('Trivy Security Scan') {
            steps {
                script {
                    sh "trivy image --format json --output trivy-report.json ${ECR_REPOSITORY}:${BUILD_NUMBER}"
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
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
                    sh "docker push ${ECR_REPOSITORY}:${BUILD_NUMBER}"
                    sh "docker push ${ECR_REPOSITORY}:latest"
                }
            }
        }
        
        stage('Deploy to EKS') {
            steps {
                script {
                    sh "aws eks update-kubeconfig --region ${AWS_REGION} --name ${EKS_CLUSTER_NAME}"
                    sh """
                        envsubst < k8s/deployment.yaml | kubectl apply -f -
                        kubectl apply -f k8s/service.yaml
                        kubectl apply -f k8s/ingress.yaml
                    """
                }
            }
        }
    }
    
    post {
        always {
            cleanWs()
        }
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline failed!'
        }
    }
}