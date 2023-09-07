pipeline {
    agent {
        dockerfile {
            filename 'Dockerfile.build'
            args '-v /root/.m2:/root/.m2'
        }
    }
    
    stages {
        stage('Test') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
    }
}