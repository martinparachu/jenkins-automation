import tools

println "\n### Creating folder ###"

folder(GITEA_ORG) {
    description("Folder for organization ${GITEA_ORG}")
}
def folderName = [GITEA_ORG, GITEA_PROJECT].join("/")
folder(folderName) {
    description("Folder for project ${GITEA_PROJECT}")
}

println "### Folder ${folderName} created ###\n"

println "\n### Building jobs ###"

def projectGitUrl = "${GITEA_URL}/${GITEA_ORG}/${GITEA_PROJECT}.git"
println "Project git URL: ${projectGitUrl}"

def pipelineConfigFile = 'target-project/pipelineConfig.json'
def pipelineConfigString = readFileFromWorkspace(pipelineConfigFile)
def pipelineConfig = new groovy.json.JsonSlurper().parseText(pipelineConfigString)
def projectType = pipelineConfig.get('projectType')
println "Project type: ${projectType}"

createInitJob(folderName, projectGitUrl, projectType)
createProjectSeedJob(folderName, projectGitUrl, projectType)
createPromoteProdJob(folderName, projectGitUrl, projectType)

println "### Seed processing completed ###\n"

def createInitJob(folderName, projectGitUrl, projectType) {
    println "** Creating job ${folderName}/initJob **"
    switch(projectType){
        case "dockerApp":
            pipelineJob("${folderName}/initJob") {
                definition {
                    cps {
                        script("""
                            @Library('jenkins-automation') _
                            dockerAppInitPipeline {
                                branch = 'develop'
                                scmUrl = "${projectGitUrl}"
                                project = "${GITEA_PROJECT}"
                            }""".stripIndent())
                        sandbox()
                    }
                }
            }
            break
    }
    println "** Job ${folderName}/initJob was created **"
    println "!!!!! Executing job ${folderName}/initJob !!!!!\n"
    queue("${folderName}/initJob")
}

def createProjectSeedJob(folderName, projectGitUrl, projectType) {
    println "\n** Creating job ${folderName}/projectSeed **"
    job("${folderName}/projectSeed") {
        triggers {
            genericTrigger {
                genericVariables {
                    genericVariable {
                        key("PAYLOAD")
                        value("\$.*")
                    }
                }
                token("${GITEA_PROJECT}")
                printContributedVariables(true)
                printPostContent(true)
                silentResponse(false)
                regexpFilterText("")
                regexpFilterExpression("")
            }
        }
        scm {
            git {
            remote {
                url "${GITEA_URL}/devops/jenkins-automation.git"
                credentials 'git-credentials'
            }
            branch 'master'
            }
        }
        steps {
            environmentVariables {
                envs(
                    giteaOrg: "${GITEA_ORG}",
                    giteaProject: "${GITEA_PROJECT}",
                    folderName: "${folderName}",
                    projectGitUrl: "${projectGitUrl}",
                    projectType: "${projectType}"
                )
            }
            dsl{
                removeAction('DELETE')
                removeViewAction('DELETE')
                external "jobs/projectSeed.groovy"
                additionalClasspath "utils"
            }
        }
    }
    println "** Job ${folderName}/projectSeed was created **"
    println "!!!!! Executing job ${folderName}/projectSeed !!!!!\n"
    queue("${folderName}/projectSeed")
}

def createPromoteProdJob(folderName, projectGitUrl, projectType) {
    println "\n** Creating job ${folderName}/promoteProdJob **"
    switch(projectType){
        case "dockerApp":
            pipelineJob("${folderName}/promoteProdJob") {
                definition {
                    cps {
                        script("""
                            @Library('jenkins-automation') _
                            dockerAppPromoteProdPipeline {
                                branch = 'master'
                                scmUrl = "${projectGitUrl}"
                                project = "${GITEA_PROJECT}"
                            }""".stripIndent())
                        sandbox()
                    }
                }
            }
        break
    }
    println "** Job ${folderName}/promoteProdJob was created **\n"
}
