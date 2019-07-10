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

/*
    Create the kubernetes namespace
 */
def createNamespace (namespace) {
    echo "Creating namespace ${namespace} if needed"

    sh "[ ! -z \"\$(kubectl get ns ${namespace} -o name 2>/dev/null)\" ] || kubectl create ns ${namespace}"
}

/*
    Helm install
 */
def helmInstall (namespace, release) {
    echo "Installing ${release} in ${namespace}"

    script {
        release = "${release}-${namespace}"
        sh "helm repo update"
        sh "helm upgrade --install --namespace ${namespace} ${release}  --set image.repository=${DOCKER_REG}/library/${IMAGE_NAME},image.tag=${DOCKER_TAG} acme/acme"
        sh "sleep 5"
    }
}

/*
    Helm delete (if exists)
 */
def helmDelete (namespace, release) {
    echo "Deleting ${release} in ${namespace} if deployed"

    script {
        release = "${release}-${namespace}"
        sh "[ -z \"\$(helm ls --short ${release} 2>/dev/null)\" ] || helm delete --purge ${release}"
    }
}

/*
    Run a curl against a given url
 */
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

/*
    Test with a simple curl and check we get 200 back
 */
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

/*
    This is the main pipeline section with the stages of the CI/CD
 */
pipeline {

    options {
        // Build auto timeout
        timeout(time: 60, unit: 'MINUTES')
    }

    // Some global default variables
    environment {
        IMAGE_NAME = 'acme'
        TEST_LOCAL_PORT = 8817
        DEPLOY_PROD = false
        DOCKER_REG = 'harbor.gustine.cf-app.com'
        GIT_BRANCH = 'master'
        DEPLOY_TO_PROD = true
        DOCKER_TAG =  'latest'
        KUBECONFIG = "$WORKSPACE/.kubeconfig"
        HELM_REPO = "https://harbor.gustine.cf-app.com/chartrepo/acme"
        DOCKER_TLS_VERIFY="1"
        
        DOCKER_HOST="tcp://35.193.242.125:2376"
        DOCKER_CERT_PATH="/tmp/docker-machine/machines/docker-build"
        DOCKER_MACHINE_NAME="docker-build"
        DOCKER_HOST_IP="35.193.242.125"
    
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
                        url: 'https://github.com/yogendra/acme-ci-cd.git'

                
                // Setup kubectl
                sh '''
                curl -sLO https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl
                chmod a+x kubectl
                mv kubectl /usr/local/bin/kubectl
                '''
                // Setup helm
                sh '''
                curl -sL https://get.helm.sh/helm-v2.10.0-linux-amd64.tar.gz | tar -xzv linux-amd64/helm
                chmod a+x linux-amd64/helm
                mv linux-amd64/helm /usr/local/bin/helm
                helm init --client-only
                [[ `helm plugin list | grep push | wc -l ` -eq 0  ]] && helm plugin install https://github.com/chartmuseum/helm-push
                rm -rf linux-amd64
                helm repo update
                '''

                // Setup dockers
                withCredentials([file(credentialsId: 'docker-machine', variable: 'DOCKER_MACHINE_CONFIG')]) {
                sh "tar -C /tmp -xzvf ${DOCKER_MACHINE_CONFIG}"
                sh '''
                    curl -sL https://download.docker.com/linux/static/stable/x86_64/docker-18.09.7.tgz | tar -xzv docker/docker
                    mv docker/docker /usr/local/bin/docker
                    docker ps
                    '''
                
                }    
                // Validate kubectl
                withCredentials([file(credentialsId: 'kubernetes-config', variable: 'KUBECONFIG_SRC')]) {
                  sh "cp ${KUBECONFIG_SRC} ${KUBECONFIG}"                    
                  sh "kubectl config use-context non-prod"
                  sh "helm repo update"
                }

                

                sh "kubectl cluster-info"



                echo "DOCKER_REG is ${DOCKER_REG}"
                echo "HELM_REPO  is ${HELM_REPO}"

                // Define a unique name for the tests container and helm release
                script {
                    branch = GIT_BRANCH.replaceAll('/', '-').replaceAll('\\*', '-')
                    ID = "${IMAGE_NAME}-${DOCKER_TAG}-${branch}"

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
                sh "docker run --detach --name ${ID} --rm --publish ${TEST_LOCAL_PORT}:80 ${DOCKER_REG}/library/${IMAGE_NAME}:${DOCKER_TAG}"

                // script {
                //     host_ip = "${DOCKER_HOST_IP}"
                // }
            }
        }

        // Run the 3 tests on the currently running ACME Docker container
        stage('Local tests') {
            parallel {
                stage('Curl http_code') {
                    steps {
                        echo "Starting Local tests"
                    }
                }
                /*stage('Curl http_code') {
                    steps {
                        curlRun ("http://${host_ip}", 'http_code')
                    }
                }
                stage('Curl total_time') {
                    steps {
                        curlRun ("http://${host_ip}", 'total_time')
                    }
                }
                stage('Curl size_download') {
                    steps {
                        curlRun ("http://${host_ip}", 'size_download')
                    }
                }
                */
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
                    echo "Deploying"
                    helmInstall(namespace, "${ID}")
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

        // ////////// Step 5 //////////
        // stage('Deploy to staging') {
        //     steps {
        //         script {
        //             namespace = 'staging'

        //             echo "Deploying application ${IMAGE_NAME}:${DOCKER_TAG} to ${namespace} namespace"
        //             createNamespace (namespace)

        //             // Remove release if exists
        //             helmDelete (namespace, "${ID}")

        //             // Deploy with helm
        //             echo "Deploying"
        //             helmInstall (namespace, "${ID}")
        //         }
        //     }
        // }

        // // Run the 3 tests on the deployed Kubernetes pod and service
        // stage('Staging tests') {
        //     parallel {
        //         stage('Curl http_code') {
        //             steps {
        //                 echo "Staging tests"
        //             }
        //         }
        //         /*stage('Curl http_code') {
        //             steps {
        //                 curlTest (namespace, 'http_code')
        //             }
        //         }
        //         stage('Curl total_time') {
        //             steps {
        //                 curlTest (namespace, 'time_total')
        //             }
        //         }
        //         stage('Curl size_download') {
        //             steps {
        //                 curlTest (namespace, 'size_download')
        //             }
        //         }*/
        //     }
        // }

        // stage('Cleanup staging') {
        //     steps {
        //         script {
        //             // Remove release if exists
        //             helmDelete (namespace, "${ID}")
        //         }
        //     }
        // }

        ////////// Step 6 //////////
        // Waif for user manual approval, or proceed automatically if DEPLOY_TO_PROD is true
        // stage('Go for Production?') {
        //     when {
        //         allOf {
        //             environment name: 'GIT_BRANCH', value: 'master'
        //             environment name: 'DEPLOY_TO_PROD', value: 'false'
        //         }
        //     }

        //     steps {
        //         // Prevent any older builds from deploying to production
        //         milestone(1)
        //         input 'Proceed and deploy to Production?'
        //         milestone(2)

        //         script {
        //             DEPLOY_PROD = true
        //         }
        //     }
        // }

        stage('Deploy to Production') {
            when {
                anyOf {
                    expression { DEPLOY_PROD == true }
                    // environment name: 'DEPLOY_TO_PROD', value: 'true'
                }
            }

            steps {
                script {
                    sh "kubectl config use-context non-prod"
                    sh "helm repo update"

                    DEPLOY_PROD = true
                    namespace = 'production'
                    sh 'kubectl config use-context prod'

                    echo "Deploying application ${IMAGE_NAME}:${DOCKER_TAG} to ${namespace} namespace"
                    createNamespace (namespace)

                    // Deploy with helm
                    echo "Deploying"
                    helmInstall (namespace, "${ID}")
                }
            }
        }

        // Run the 3 tests on the deployed Kubernetes pod and service
        stage('Production tests') {
            when {
                expression { DEPLOY_PROD == true }
            }

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
