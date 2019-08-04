node {
    properties([
        parameters([
                string (name: 'IMAGE_NAME',            defaultValue: 'acme',    description: 'image name'),
                booleanParam (name: 'DEPLOY_PROD',            defaultValue: false,      description: 'internal logic'),
                string (name: 'DOCKER_REG',       defaultValue: 'harbor.pcfgcp.pkhamdee.com',      description: 'Docker registry'),
                string (name: 'GIT_BRANCH',       defaultValue: 'master',      description: 'Git branch to build'),
                booleanParam (name: 'DEPLOY_TO_PROD',       defaultValue: false,      description: 'If build and tests are good, proceed and deploy to production without manual approval'),
                string (name: 'KUBECONFIG',       defaultValue: '$WORKSPACE/.kubeconfig',      description: 'Kubeconfig file location'),
                string (name: 'HELM_REPO',       defaultValue: 'https://harbor.pcfgcp.pkhamdee.com/chartrepo/library',      description: 'helm repository')
        ])
    ])
}