pipeline {
    environment {
        androidSDKImageName = "tworx/eud-builder:latest"
        registryCredentials = 'dockerhub-repo-credentials'
        nexusCredentials = credentials('nexus-build-agent-credentials')
        nexusRepoPath = "/nexus/repository/maven-"
        mavenRepoType = "${env.TAG_NAME == null ? 'snapshots' : 'releases'}"
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
                            withCredentials([usernamePassword(credentialsId: 'nexus-build-agent-credentials', passwordVariable: 'nexusPwd', usernameVariable: 'nexusUser')]) {
                                def ipAddr = readFile(file: 'repo-ip.txt')
                                def fileData = "tworxrepo=http://" + ipAddr + nexusRepoPath + mavenRepoType + "\ntworxrepoUser=" + nexusUser + "\ntworxrepoPwd=" + nexusPwd + "\n"
                                writeFile(file: 'local.properties', text: fileData)
                                sh "./gradlew :mqttservice:publishReleasePublicationToTworxrepoRepository"
                            }
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
                            sh "./gradlew :app:build"
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
