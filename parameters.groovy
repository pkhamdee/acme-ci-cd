parameters {
        string (name: 'GIT_BRANCH',           defaultValue: 'master',  description: 'Git branch to build')
        booleanParam (name: 'DEPLOY_TO_PROD', defaultValue: false,     description: 'If build and tests are good, proceed and deploy to production without manual approval')


        // The commented out parameters are for optionally using them in the pipeline.
        // In this example, the parameters are loaded from file ${JENKINS_HOME}/parameters.groovy later in the pipeline.
        // The ${JENKINS_HOME}/parameters.groovy can be a mounted secrets file in your Jenkins container.

        string (name: 'DOCKER_REG',       defaultValue: 'harbor.pks.pkhamdee.com',                   description: 'Docker registry')
        string (name: 'DOCKER_TAG',       defaultValue: 'latest',                                     description: 'Docker tag')
        string (name: 'DOCKER_USR',       defaultValue: 'admin',                                   description: 'Your helm repository user')
        string (name: 'DOCKER_PSW',       defaultValue: 'password',                                description: 'Your helm repository password')
        string (name: 'IMG_PULL_SECRET',  defaultValue: 'docker-reg-secret',                       description: 'The Kubernetes secret for the Docker registry (imagePullSecrets)')
        string (name: 'HELM_REPO',        defaultValue: 'https://raw.githubusercontent.com/pkhamdee/helm-example/master/', description: 'Your helm repository')
        string (name: 'HELM_USR',         defaultValue: 'pkhamdee',                                   description: 'Your helm repository user')
        string (name: 'HELM_PSW',         defaultValue: 'Khuntao332',                                description: 'Your helm repository password')

    }
