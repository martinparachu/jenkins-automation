def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent {
            label "jenkins-agent-docker"
        }
        stages {
            stage('Initialize') {
                steps {
                    sh 'printenv'
                }
            }
            stage('Checkout git') {
                steps {
                    git branch: pipelineParams.branch, credentialsId: 'git-credentials', url: pipelineParams.scmUrl
                }
            }
            stage('Deploy Prod') {  
                steps {
                    script {
                        sh("""helm upgrade ${pipelineParams.project}-prod \
                            helm-chart --force --install --namespace prod \
                            --set image.repository=${env.REGISTRY_SUBDOMAIN} \
                            --set image.name=${pipelineParams.project} \
                            --set image.tag=latest \
                            --set ingress.host=${env.PROD_SUBDOMAIN} \
                            --set router.host=${env.PROD_SUBDOMAIN} \
                            --kubeconfig=/.kube/config""")
                    }
                }
            }
            stage('Test Prod') { 
                steps {
                    script {
                        sh "chmod a+rx tests/test-script.sh"
                        sh "tests/test-script.sh https://${env.PROD_SUBDOMAIN}/goapp"
                    }
                }
            }
        }
    }
}
