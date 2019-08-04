/*
    This is an example pipeline that implement full CI/CD for a simple static web site packed in a Docker image.

    The pipeline is made up of 6 main steps
    1. Git clone and setup
    2. Build and local tests
    3. Publish Docker and Helm
    4. Deploy to dev and test
    5. Deploy to staging and test
    6. Optionally deploy to production and test
 */

/* Create the kubernetes namespace */
def createNamespace (namespace) {
    echo "Creating namespace ${namespace} if needed"

    sh "[ ! -z \"\$(kubectl get ns ${namespace} -o name 2>/dev/null)\" ] || kubectl create ns ${namespace}"
}


/* Helm add remo */
def helmAddrepo () {
    echo "Adding helm repo"

    script {
        withCredentials([file(credentialsId: 'letencrypt-ca-cert', variable: 'HELM_CA_CERT')]) {
            withCredentials([usernamePassword(credentialsId: 'harbor-admin', passwordVariable: 'HELM_PSW', usernameVariable: 'HELM_USR')]) {
                sh "helm repo add --ca-file ca.pem --username ${HELM_USR} --password ${HELM_PSW} helm ${HELM_REPO}"
            }
        }
        sh "helm repo update"
    }
}


/* Helm install */
def helmInstall (namespace, release, values) {
    echo "Installing ${release} in ${namespace}"

    script {
        release = "${release}-${namespace}"

        sh "helm upgrade --install --namespace ${namespace} ${release}  --set image.repository=${DOCKER_REG}/library/${IMAGE_NAME},image.tag=${DOCKER_TAG} -f ${values} acme/acme"
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
        DOCKER_REG = 'harbor.pcfgcp.pkhamdee.com'
        GIT_BRANCH = 'master'
        DEPLOY_TO_PROD = true
        DOCKER_TAG =  'latest'
        KUBECONFIG = "$WORKSPACE/.kubeconfig"
        HELM_REPO = "https://harbor.pcfgcp.pkhamdee.com/chartrepo/library"

        //PARAMETERS_FILE = "${WORKSPACE}/parameters.groovy"
    }

    // parameters {
    //     string (name: 'GIT_BRANCH',           defaultValue: 'master',  description: 'Git branch to build')
    //     booleanParam (name: 'DEPLOY_TO_PROD', defaultValue: true,     description: 'If build and tests are good, proceed and deploy to production without manual approval')

    //     // The commented out parameters are for optionally using them in the pipeline.
    //     // In this example, the parameters are loaded from file ${JENKINS_HOME}/parameters.groovy later in the pipeline.
    //     // The ${JENKINS_HOME}/parameters.groovy can be a mounted secrets file in your Jenkins container.

    //     string (name: 'DOCKER_TAG',       defaultValue: 'latest',                                     description: 'Docker tag') 
    // }

    // In this example, all is built and run from the master
    agent { node { label 'master' } }

    // Pipeline stages
    stages {

        ////////// Step 1 //////////
        stage('Git clone and setup') {
            steps {
                echo "Check out acme code"
                git branch: "master",                        
                        url: 'https://github.com/pivhub/acme-ci-cd.git'

                withCredentials([file(credentialsId: 'letencrypt-ca-cert', variable: 'CA_CERT')]) {
                    sh "cp ${CA_CERT} ${WORKSPACE}/ca.crt"
                }

                // git HEAD
                script {
                    GIT_HEAD = sh(returnStdout: true, script: 'git rev-parse --short HEAD')
                    GIT_HEAD = GIT_HEAD.replaceAll('\n', '') //replace newline
                    echo "GIT_HEAD is ${GIT_HEAD}"
                }  


                // Setup helm plugin
                sh '''
                    helm init --client-only
                    if [ `helm plugin list | grep push | wc -l ` -eq 0  ] ; then  helm plugin install https://github.com/chartmuseum/helm-push ; fi     
                '''

                //add helm repo
                script {
                    helmAddrepo () 
                } 

                //list helm repo
                sh '''
                    helm repo list
                    helm repo update
                '''

                // Check docker process
                sh "docker ps"

                // Validate kubectl
                withCredentials([file(credentialsId: 'kubernetes-config', variable: 'KUBECONFIG_SRC')]) {
                  sh "cp ${KUBECONFIG_SRC} ${KUBECONFIG}"                    
                  sh "kubectl config use-context dev1"
                  sh "kubectl cluster-info"
                }

                echo "DOCKER_REG is ${DOCKER_REG}"
                echo "HELM_REPO  is ${HELM_REPO}"

                // Define a unique name for the tests container and helm release
                script {
                    branch = GIT_BRANCH.replaceAll('/', '-').replaceAll('\\*', '-')
                    ID = "${IMAGE_NAME}-${DOCKER_TAG}-${branch}-${GIT_HEAD}"

                    echo "Global ID set to ${ID}"
                }

            }
        }

        ////////// Step 2 //////////
        stage('Build and tests') {
            steps {
                echo "Building application and Docker image"
                sh "${WORKSPACE}/build.sh --build --registry ${DOCKER_REG} --tag ${DOCKER_TAG}"

                echo "Running tests"

                // Kill container in case there is a leftover
                sh "[ -z \"\$(docker ps -a | grep ${ID} 2>/dev/null)\" ] || docker rm -f ${ID}"

                echo "Starting ${IMAGE_NAME} container"

                //sh "docker run --detach --name ${ID} --rm --publish ${TEST_LOCAL_PORT}:80 ${DOCKER_REG}/library/${IMAGE_NAME}:${DOCKER_TAG}"
                sh "docker run --detach --name ${ID} --rm -P ${DOCKER_REG}/library/${IMAGE_NAME}:${DOCKER_TAG}"

                script {
                    TEST_LOCAL_PORT = sh(returnStdout: true, script: "docker inspect ${ID} --format '{{ (index (index .NetworkSettings.Ports \"80/tcp\") 0).HostPort }}'")
                    TEST_LOCAL_PORT = TEST_LOCAL_PORT.replaceAll('\n', '') //replace newline
                    echo "TEST_LOCAL_PORT is ${TEST_LOCAL_PORT}"
                } 

            }
        }

        // Run the 3 tests on the currently running ACME Docker container
        stage('Local tests') {
            parallel {
                
                stage('Curl http_code') {
                    steps {
                        echo "curl http_code http://localhost:${TEST_LOCAL_PORT}"
                        curlRun ("http://localhost:${TEST_LOCAL_PORT}", 'http_code')
                    }
                }
                stage('Curl total_time') {
                    steps {
                        echo "curl total_time http://localhost:${TEST_LOCAL_PORT}"
                        curlRun ("http://localhost:${TEST_LOCAL_PORT}", 'total_time')
                    }
                }
                stage('Curl size_download') {
                    steps {
                        echo "curl size_download http://localhost:${TEST_LOCAL_PORT}"
                        curlRun ("http://localhost:${TEST_LOCAL_PORT}", 'size_download')
                    }
                }

            }
        }

        ////////// Step 3 //////////
        stage('Publish Docker and Helm') {
            steps {

                // This step should not normally be used in your script. Consult the inline help for details.
                echo "Stop and remove container"
                sh "docker stop ${ID}"

                echo "Pushing ${DOCKER_REG}/${IMAGE_NAME}:${DOCKER_TAG} image to registry"
                withCredentials([usernamePassword(credentialsId: 'harbor-admin', passwordVariable: 'DOCKER_PSW', usernameVariable: 'DOCKER_USR')]) {                
                    sh "${WORKSPACE}/build.sh --push --registry ${DOCKER_REG} --tag ${DOCKER_TAG}"
                }

                echo "Packing helm chart"
                sh "${WORKSPACE}/build.sh --pack_helm --push_helm --helm_repo ${HELM_REPO}"

                echo "docker prune images"
                sh "docker image prune -f"
            }
        }

        ////////// Step 4 //////////
        stage('Deploy to dev') {
            steps {
                script {
                    namespace = 'development'

                    echo "Deploying application ${ID} to ${namespace} namespace"
                    createNamespace (namespace)

                    // Remove release if exists
                    helmDelete (namespace, "${ID}")

                    // Deploy with helm
                    echo "Deploying dev using values-dev.yaml"
                    helmInstall(namespace, "${ID}","values-dev.yaml")
                }
            }
        }

        // Run the 3 tests on the deployed Kubernetes pod and service
        stage('Dev tests') {
            parallel {
                stage('Curl http_code') {
                    steps {
                        echo "Starting Local tests"
                    }
                }
                /*stage('Curl http_code') {
                    steps {
                        curlTest (namespace, 'http_code')
                    }
                }
                stage('Curl total_time') {
                    steps {
                        curlTest (namespace, 'time_total')
                    }
                }
                stage('Curl size_download') {
                    steps {
                        curlTest (namespace, 'size_download')
                    }
                }*/
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

        ////////// Step 5 //////////
        stage('Deploy to staging') {
            steps {
                script {
                    namespace = 'staging'

                    echo "Deploying application ${IMAGE_NAME}:${DOCKER_TAG} to ${namespace} namespace"
                    createNamespace (namespace)

                    // Remove release if exists
                    helmDelete (namespace, "${ID}")

                    // Deploy with helm
                    echo "Deploying stage using values-stage.yaml"
                    helmInstall (namespace, "${ID}","values-stage.yaml")
                }
            }
        }

        // Run the 3 tests on the deployed Kubernetes pod and service
        stage('Staging tests') {
            parallel {
                stage('Curl http_code') {
                    steps {
                        echo "Staging tests"
                    }
                }
                /*stage('Curl http_code') {
                    steps {
                        curlTest (namespace, 'http_code')
                    }
                }
                stage('Curl total_time') {
                    steps {
                        curlTest (namespace, 'time_total')
                    }
                }
                stage('Curl size_download') {
                    steps {
                        curlTest (namespace, 'size_download')
                    }
                }*/
            }
        }

        stage('Cleanup staging') {
            steps {
                script {
                    // Remove release if exists
                    helmDelete (namespace, "${ID}")
                }
            }
        }

        ////////// Step 6 //////////
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
                    //sh "kubectl config use-context prod"
                    sh "kubectl config use-context dev1"
                    sh "helm repo update"

                    DEPLOY_PROD = true
                    namespace = 'production'
                    //sh 'kubectl config use-context prod'
                    sh 'kubectl config use-context dev1'

                    echo "Deploying application ${IMAGE_NAME}:${DOCKER_TAG} to ${namespace} namespace"
                    createNamespace (namespace)

                    // Deploy with helm
                    echo "Deploying prod using values.yaml"
                    helmInstall (namespace, "${ID}","values.yaml")
                }
            }
        }

        // Run the 3 tests on the deployed Kubernetes pod and service
        stage('Production tests') {            

            parallel {
                stage('Curl http_code') {
                    steps {
                        echo "Production tests"
                    }
                }
                /*stage('Curl http_code') {
                    steps {
                        curlTest (namespace, 'http_code')
                    }
                }
                stage('Curl total_time') {
                    steps {
                        curlTest (namespace, 'time_total')
                    }
                }
                stage('Curl size_download') {
                    steps {
                        curlTest (namespace, 'size_download')
                    }
                }*/
            }
        }
    }
}
