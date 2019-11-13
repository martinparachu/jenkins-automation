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
            stage('Build image') {
                steps {
                    script {
                        tag = "init" + env.BUILD_NUMBER
                        app = docker.build("${pipelineParams.project}:${tag}") 
                    }
                }
            }
            stage('Push image') {
                steps {
                    script {
                        docker.withRegistry("https://${env.REGISTRY_SUBDOMAIN}", 'local-registry') {
                            app.push()
                            app.push('latest')
                        }
                    }
                }
            }
            stage('Deploy') {  
                steps {
                    script {
                        sh("""helm upgrade ${pipelineParams.project}-dev \
                            helm-chart --force --install --namespace dev \
                            --set image.repository=${env.REGISTRY_SUBDOMAIN} \
                            --set image.name=${pipelineParams.project} \
                            --set image.tag=latest \
                            --set ingress.host=${env.DEV_SUBDOMAIN} \
                            --set router.host=${env.DEV_SUBDOMAIN} \
                            --kubeconfig=/.kube/config""")
                        sh("""helm upgrade ${pipelineParams.project}-staging \
                            helm-chart --force --install --namespace staging \
                            --set image.repository=${env.REGISTRY_SUBDOMAIN} \
                            --set image.name=${pipelineParams.project} \
                            --set image.tag=latest \
                            --set ingress.host=${env.STAGING_SUBDOMAIN} \
                            --set router.host=${env.STAGING_SUBDOMAIN} \
                            --kubeconfig=/.kube/config""")
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
            stage('Test') { 
                steps {
                    script {
                        sh "chmod a+rx tests/test-script.sh"
                        sh "tests/test-script.sh https://${env.DEV_SUBDOMAIN}/goapp"
                        sh "tests/test-script.sh https://${env.STAGING_SUBDOMAIN}/goapp"
                        sh "tests/test-script.sh https://${env.PROD_SUBDOMAIN}/goapp"
                    }
                }
            }
        }
    }
}
