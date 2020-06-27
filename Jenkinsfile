/* Create the kubernetes namespace */
def createNamespace (namespace) {
    echo "Creating namespace ${namespace} if needed"

    sh "[ ! -z \"\$(kubectl get ns ${namespace} -o name 2>/dev/null)\" ] || kubectl create ns ${namespace}"
}


/* Helm add remo */
def helmAddrepo () {
    echo "Adding helm repo"

    script {
        withCredentials([file(credentialsId: 'harbor-ca-cert', variable: 'HELM_CA_CERT')]) {
            withCredentials([usernamePassword(credentialsId: 'harbor-admin', passwordVariable: 'HELM_PSW', usernameVariable: 'HELM_USR')]) {
                sh "helm repo add --ca-file ca.crt --username ${HELM_USR} --password ${HELM_PSW} helm ${HELM_REPO}"
            }
        }
        sh "helm repo update"
    }
}


/* Helm install */
def helmInstall (namespace, release, url) {
    echo "Installing ${release} in ${namespace}"

    script {
        release = "${release}-${namespace}"
        sh "helm upgrade --install --namespace ${namespace} ${release}  --set image.repository=${DOCKER_REG}/library/${IMAGE_NAME},image.tag=${GIT_HEAD} --set ingress.hosts[0].host=${url} --set ingress.tls[0].hosts[0]=${url} --set ingress.hosts[0].paths[0]= helm/acme --wait"
        sh "sleep 5"
    }
}

/* Helm delete (if exists) */
def helmDelete (namespace, release) {
    echo "Deleting ${release} in ${namespace} if deployed"

    script {
        release = "${release}-${namespace}"
        sh "[ -z \"\$(helm ls --short ${release} 2>/dev/null)\" ] || helm delete --purge ${release}"
    }
}

/* Run a curl against a given url */
def curlRun (url, out) {
    echo "Running curl on ${url}"

    script {
        if (out.equals('')) {
            out = 'http_code'
        }
        echo "Getting ${out}"
        def result = sh (
            returnStdout: true,
            script: "curl --output /dev/null --silent --connect-timeout 5 --max-time 5 --retry 5 --retry-delay 5 --retry-max-time 30 --write-out \"%{${out}}\" ${url}"
            )
        echo "Result (${out}): ${result}"
    }
}

/* Test with a simple curl and check we get 200 back */
def curlTest (namespace, out) {
    echo "Running tests in ${namespace}"

    script {
        if (out.equals('')) {
            out = 'http_code'
        }

        // Get deployment's service IP
        def svc_ip = sh (
            returnStdout: true,
            script: "kubectl get svc -n ${namespace} | grep ${ID} | awk '{print \$3}'"
            )

        if (svc_ip.equals('')) {
            echo "ERROR: Getting service IP failed"
            sh 'exit 1'
        }

        echo "svc_ip is ${svc_ip}"
        url = 'http://' + svc_ip

        curlRun (url, out)
    }
}

/* This is the main pipeline section with the stages of the CI/CD */
pipeline {

    options {
        // Build auto timeout
        timeout(time: 60, unit: 'MINUTES')
    }

    // Some global default variables
    environment {
        IMAGE_NAME = 'acme'
        DEPLOY_PROD = false
        DOCKER_REG = 'harbor.tkg.pkhamdee.com'
        GIT_BRANCH = 'master'
        DEPLOY_TO_PROD = false
        KUBECONFIG = "$WORKSPACE/.kubeconfig"
        HELM_REPO = "https://harbor.tkg.pkhamdee.com/chartrepo/library"

        BUILD_DIR="$WORKSPACE/build"    
        DOCKER_REPO="library/acme"

        //PARAMETERS_FILE = "${WORKSPACE}/parameters.groovy"
    }

    // Restrict where this project can be run
    agent { node { label 'jenkins-jenkins-slave' } }

    // Pipeline stages
    stages {

        stage('Git clone and setup') {
            steps {

                //load ${PARAMETERS_FILE}    

                echo "validate kubectl"
                echo "HOME:  ${HOME}"
                withCredentials([file(credentialsId: 'dev-cluster-config', variable: 'KUBECONFIG_SRC')]) { 
                  sh "cp ${KUBECONFIG_SRC} ${KUBECONFIG}"
                  sh "kubectl config --kubeconfig=${KUBECONFIG} use-context dev-cluster-admin@dev-cluster"
                  sh "kubectl cluster-info"
                }

                echo "Checkout acme code"
                git branch: "master",
                url: 'https://github.com/pkhamdee/acme-ci-cd.git'

                withCredentials([file(credentialsId: 'harbor-ca-cert', variable: 'CA_CERT')]) {
                  sh "cp ${CA_CERT} ${WORKSPACE}/ca.crt"
                }

                // git HEAD
                script {
                    GIT_HEAD = sh(returnStdout: true, script: 'git rev-parse --short HEAD')
                    GIT_HEAD = GIT_HEAD.replaceAll('\n', '') //replace newline
                    echo "GIT_HEAD is ${GIT_HEAD}"
                }

                //add helm repo
                script {
                    helmAddrepo ()
                }

                //list helm repo
                sh '''
                helm repo list
                helm repo update
                '''

                echo "DOCKER_REG is ${DOCKER_REG}"
                echo "HELM_REPO  is ${HELM_REPO}"

                // Define a unique name for the tests container and helm release
                script {
                    branch = GIT_BRANCH.replaceAll('/', '-').replaceAll('\\*', '-')
                    ID = "${IMAGE_NAME}-${GIT_HEAD}-${branch}"

                    echo "Global ID set to ${ID}"
                }

                script {
                    currentBuild.displayName = "${GIT_HEAD}"
                    currentBuild.description = "${ID}"
                }

            }
        }


        stage('Build and tests') {

            steps {

                podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: docker
    image: docker:19.03.1
    command:
    - sleep
    args:
    - 99d
    env:
      - name: DOCKER_HOST
        value: tcp://localhost:2375
  - name: docker-daemon
    image: docker:19.03.1-dind
    securityContext:
      privileged: true
    env:
      - name: DOCKER_TLS_CERTDIR
        value: ""
'''
                    ) {

                    node(POD_LABEL) {

                        git branch: "master", url: 'https://github.com/pkhamdee/acme-ci-cd.git'
                        container('docker') {

                            echo "Building ${DOCKER_REPO}:${GIT_HEAD}"     

                            //Prepare build directory
                            echo  "Preparing files"
                            sh "mkdir -p ${BUILD_DIR}/site"
                            sh "cp -v  ${WORKSPACE}/docker/Dockerfile ${BUILD_DIR}"
                            sh "cp -rv ${WORKSPACE}/src/* ${BUILD_DIR}/site/"

                            //Embed the app version
                            echo  "Writing version ${GIT_HEAD} to files"
                            sh "sed -i.org s/__APP_VERSION__/${GIT_HEAD}/g ${BUILD_DIR}/site/*.html"
                            sh "rm -f ${BUILD_DIR}/site/*.org"

                            echo  "Building Docker image"
                            sh "docker build -t ${DOCKER_REG}/${DOCKER_REPO}:${GIT_HEAD} ${BUILD_DIR} || errorExit Building ${DOCKER_REPO}:${GIT_HEAD} failed"

                            echo "Running tests"

                            sh "[ -z \"\$(docker ps -a | grep ${ID} 2>/dev/null)\" ] || docker rm -f ${ID}"

                            echo "Starting ${IMAGE_NAME} container"

                            //sh "docker run --detach --name ${ID} --rm --publish ${TEST_LOCAL_PORT}:80 ${DOCKER_REG}/library/${IMAGE_NAME}:${GIT_HEAD}"
                            sh "docker run --detach --name ${ID} --rm -P ${DOCKER_REG}/library/${IMAGE_NAME}:${GIT_HEAD}"

                            echo "Stop and remove container"
                            sh "docker stop ${ID}"

                            echo "Pushing ${DOCKER_REG}/${IMAGE_NAME}:${GIT_HEAD} image to registry"
                            withCredentials([usernamePassword(credentialsId: 'harbor-admin', passwordVariable: 'DOCKER_PSW', usernameVariable: 'DOCKER_USR')]) {

                                sh "docker login ${DOCKER_REG} -u ${DOCKER_USR} -p ${DOCKER_PSW} || errorExit Docker login to ${DOCKER_REG} failed"   

                                sh "docker push ${DOCKER_REG}/${DOCKER_REPO}:${GIT_HEAD} || errorExit Pushing ${DOCKER_REPO}:${GIT_HEAD} failed" 
                            }
                        }
                    }

                }
            }
        }


        stage('Publish Docker and Helm') {
            steps {

                withCredentials([file(credentialsId: 'dev-cluster-config', variable: 'KUBECONFIG_SRC')]) { 
                  sh "cp ${KUBECONFIG_SRC} ${KUBECONFIG}"


                  echo "Packing helm chart"
                  sh "${WORKSPACE}/build.sh --pack_helm --push_helm --helm_repo ${HELM_REPO}"  
                }  
            }
        }



        stage('Deploy to dev') {
            steps {
                script {
                    namespace = 'development'

                    echo "Deploying application ${ID} to ${namespace} namespace"
                    createNamespace (namespace)

                    // Remove release if exists
                    helmDelete (namespace, "${ID}")

                    // Deploy with helm
                    echo "Deploying dev"
                    helmInstall(namespace, "${ID}","${ID}-dev.tkg.pkhamdee.com")
                }
            }
        }

        // Run the 3 tests on the deployed Kubernetes pod and service
        stage('Dev tests') {
            parallel {
                stage('Curl http_code') {
                    steps {
                        //curlTest (namespace, 'http_code')
                        curlRun ("http://${ID}-dev.tkg.pkhamdee.com", 'http_code')
                    }
                }
                stage('Curl total_time') {
                    steps {
                        //curlTest (namespace, 'time_total')
                        curlRun ("http://${ID}-dev.tkg.pkhamdee.com", 'time_total')
                    }
                }
                stage('Curl size_download') {
                    steps {
                        //curlTest (namespace, 'size_download')
                        curlRun ("http://${ID}-dev.tkg.pkhamdee.com", 'size_download')
                    }
                }
            }
        }

        stage('Cleanup dev') {
            steps {
                script {
                    // Remove release if exists
                    helmDelete (namespace, "${ID}")
                }
            }
        }


        stage('Deploy to staging') {
            steps {
                script {
                    namespace = 'staging'

                    echo "Deploying application ${IMAGE_NAME}:${GIT_HEAD} to ${namespace} namespace"
                    createNamespace (namespace)

                    // Remove release if exists
                    helmDelete (namespace, "${ID}")

                    // Deploy with helm
                    echo "Deploying stage"
                    helmInstall (namespace, "${ID}","${IMAGE_NAME}-stage.tkg.pkhamdee.com")
                }
            }
        }

        // Run the 3 tests on the deployed Kubernetes pod and service
        stage('Staging tests') {
            parallel {
                stage('Curl http_code') {
                    steps {
                        //curlTest (namespace, 'http_code')
                        curlRun ("http://${IMAGE_NAME}-stage.tkg.pkhamdee.com", 'http_code')
                    }
                }
                stage('Curl total_time') {
                    steps {
                        //curlTest (namespace, 'time_total')
                        curlRun ("http://${IMAGE_NAME}-stage.tkg.pkhamdee.com", 'time_total')
                    }
                }
                stage('Curl size_download') {
                    steps {
                        //curlTest (namespace, 'size_download')
                        curlRun ("http://${IMAGE_NAME}-stage.tkg.pkhamdee.com", 'size_download')
                    }
                }
            }
        }

        //Waif for user manual approval, or proceed automatically if DEPLOY_TO_PROD is true
        stage('Go for Production?') {
            when {
                allOf {
                    environment name: 'GIT_BRANCH', value: 'master'
                    environment name: 'DEPLOY_TO_PROD', value: 'false'
                }
            }

            steps {
                // Prevent any older builds from deploying to production
                milestone(1)
                input 'Proceed and deploy to Production?'
                milestone(2)

                script {
                    DEPLOY_PROD = true
                }
            }
        }

        stage('Deploy to Production') {
            steps {
                script {

                    //sh 'kubectl config use-context dev-cluster-admin@dev-cluster'
                    //sh "helm repo update"

                    DEPLOY_PROD = true
                    namespace = 'production'

                    echo "Deploying application ${IMAGE_NAME}:${GIT_HEAD} to ${namespace} namespace"
                    createNamespace (namespace)

                    // Deploy with helm
                    echo "Deploying prod"
                    helmInstall (namespace, "${ID}","acme.tkg.pkhamdee.com")
                }
            }
        }

        // Run the 3 tests on the deployed Kubernetes pod and service
        stage('Production tests') {

            parallel {
                stage('Curl http_code') {
                    steps {
                        //curlTest (namespace, 'http_code')
                        curlRun ("http://acme.tkg.pkhamdee.com", 'size_download')
                    }
                }
                stage('Curl total_time') {
                    steps {
                        //curlTest (namespace, 'time_total')
                        curlRun ("http://acme.tkg.pkhamdee.com", 'size_download')
                    }
                }
                stage('Curl size_download') {
                    steps {
                        //curlTest (namespace, 'size_download')
                        curlRun ("http://acme.tkg.pkhamdee.com", 'size_download')
                    }
                }
            }
        }
    }
}
