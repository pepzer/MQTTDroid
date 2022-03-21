pipeline {
    environment {
        androidSDKImageName = "tworx/eud-builder:2021-06-17"
        registryCredentials = 'dockerhub-repo-credentials'
        nexusCredentials = credentials('nexus-build-agent-credentials')
        nexusRepo = 'http://nginx/nexus/repository/raw/tworx/atak-plugin'
    }
    agent any
    stages {
        stage('Build AIDL library') {
            steps {
                script {                    
                    docker.withRegistry("${TWORX_DOCKER_REPO}", "${registryCredentials}") {
                        docker.image("${androidSDKImageName}").inside {
                            sh "./gradlew :mqttservice:bundleDebugAar :mqttservice:bundleReleaseAar"
                        }
                    }
                }
            }
        }
        stage('Publish AIDL library') {
            steps {
                script {
                    // need to save the host name of the repo as we are using docker compose network which does not resolve inside docker image
                    sh "getent hosts nginx | awk '{ print \$1 }' > repo-ip.txt"
                    docker.withRegistry("${TWORX_DOCKER_REPO}", "${registryCredentials}") {
                        docker.image("${androidSDKImageName}").inside {
                            sh "echo tworxrepo=http://\$(cat repo-ip.txt) >> ./local.properties"
                            sh "./gradlew :mqttservice:publishReleasePublicationToTworxrepoRepository"
                        }
                    }
                 }
            }
        }
        stage('Build sample app') {
            steps {
                script {                    
                    docker.withRegistry("${TWORX_DOCKER_REPO}", "${registryCredentials}") {
                        docker.image("${androidSDKImageName}").inside {
                            sh "./gradlew :app:assemble"
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            sendNotifications currentBuild.result, (currentBuild.getPreviousBuild() ? currentBuild.getPreviousBuild().result : null)
        }
    }
}
