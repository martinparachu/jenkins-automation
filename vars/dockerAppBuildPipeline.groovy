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
        environment {
            SUBDOMAIN = ""
            NAMESPACE = ""
        }
        stages {
            stage('Initialize') {
                steps {
                    sh 'printenv'
                }
            }
            stage('Checkout git') {
                steps {
                    git url: pipelineParams.scmUrl, credentialsId: 'git-credentials', branch: pipelineParams.branch
                }
            }
            stage('Build image') {
                steps {
                    script {
                        tag = (pipelineParams.branch + "-" + env.BUILD_NUMBER).replaceAll('/','-')
                        app = docker.build("${pipelineParams.project}:${tag}") 
                    }
                }
            }
            stage('Push image') {
                steps {
                    script {
                        docker.withRegistry("https://${env.REGISTRY_SUBDOMAIN}", 'local-registry') {
                            app.push()
                        }
                    }
                }
            }
            stage('Deploy') {  
                steps {
                    script {
                        def branchTmp = pipelineParams.branch
                        if (branchTmp.contains('/')){
                            def values = branchTmp.split('/')
                            prefix = values[0]
                        } else {
                            prefix = branchTmp
                        }
                        
                        switch(prefix){
                            case "develop":
                                SUBDOMAIN = env.DEV_SUBDOMAIN
                                NAMESPACE = "dev"
                                break
                            case "feature":
                                SUBDOMAIN = env.DEV_SUBDOMAIN
                                NAMESPACE = "dev"
                                break
                            case "release":
                                docker.withRegistry("https://${env.REGISTRY_SUBDOMAIN}", 'local-registry') {
                                    app.push('latest')
                                }
                                SUBDOMAIN = env.STAGING_SUBDOMAIN
                                NAMESPACE = "staging"
                                break
                            case "hotfix":
                                docker.withRegistry("https://${env.REGISTRY_SUBDOMAIN}", 'local-registry') {
                                    app.push('latest')
                                }
                                SUBDOMAIN = env.STAGING_SUBDOMAIN
                                NAMESPACE = "staging"
                                break
                        }
                        
                        sh("""helm upgrade ${pipelineParams.project}-${NAMESPACE} \
                            helm-chart --force --install --namespace ${NAMESPACE} \
                            --set image.repository=${env.REGISTRY_SUBDOMAIN} \
                            --set image.name=${pipelineParams.project} \
                            --set image.tag=${tag} \
                            --set ingress.host=${SUBDOMAIN} \
                            --set router.host=${SUBDOMAIN} \
                            --kubeconfig=/.kube/config""")
                    }
                }
            }
            stage('Test') { 
                steps {
                    script {
                        sh "chmod a+rx tests/test-script.sh"
                        sh "tests/test-script.sh https://${SUBDOMAIN}/goapp"
                    }
                }
            }
        }
    }
}
