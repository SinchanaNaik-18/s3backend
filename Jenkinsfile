pipeline {
    agent any

    tools {
        maven 'Maven'
    }

    stages {
        stage('Build Backend') {
            steps {
                bat 'mvn clean package -DskipTests'
            }
        }
        stage('Docker Build') {
            steps {
                bat 'docker build -t eventsphere-backend .'
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully.'
        }
        failure {
            echo 'Pipeline failed.'
        }
    }
}
