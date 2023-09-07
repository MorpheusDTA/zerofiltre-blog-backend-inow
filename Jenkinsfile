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

        stage('SonarQube analysis') {
            steps {
                withSonarQubeEnv('SonarqubeServer') {
                    sh 'mvn sonar:sonar -s .m2/settings.xml'
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Package and Deploy') {
            steps {
                script {
                    // Vérifiez si le nom de la branche commence par 'release-'
                    if (env.BRANCH_NAME.startsWith('release-')) {
                        // Extrait la version du nom de la branche
                        String version = env.BRANCH_NAME.replaceAll(/^release-/, '')

                        // Définit la version du projet en utilisant Maven
                        sh "mvn versions:set -DnewVersion=${version}"
                    }

                    // Déployez le projet (toujours)
                    sh 'mvn deploy -DskipTests .m2/settings.xml'

                    // Archivez les artefacts déployés
                    archiveArtifacts artifacts: '**/target/*.jar', allowEmptyArchive: true
                }
            }
        }

    }
}