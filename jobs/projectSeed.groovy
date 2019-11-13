import tools

println "\n### Getting project branches ###"
def apiBaseUrl = "${GITEA_URL}/api/v1/repos"
def apiBranchesUrl = [apiBaseUrl, giteaOrg, giteaProject, "branches"].join("/")
println "Connecting to ${apiBranchesUrl}"
def branchesJson = new tools().doGetRequest(apiBranchesUrl)
println "Data received:\n${branchesJson}"
println "### Received project branches ###\n"

println "\n### Validating webhook trigger ###"
def payloadJson
if(binding.variables?.PAYLOAD){
    println "Hook payload received"
    payloadJson = new groovy.json.JsonSlurperClassic().parseText(PAYLOAD)
} else {
    println "Hook payload not received - manual execution"
    payloadJson = null
}
println "### Finished validating webhook trigger ###\n"

println "\n### Processing branches ###"
branchesJson.each { branch_item->
    def branchName = branch_item.get('name')
    println "Processing branch: ${branchName}"
    
    def branchTmp = branchName
    if(branchTmp.contains('/')){
        def values = branchTmp.split('/')
        prefix = values[0]
    } else {
        prefix = branchTmp
    }
    if(prefix=="develop"||prefix=="feature"||prefix=="release"||prefix=="hotfix"){
        branchNameDisplay = branchName.replaceAll('/','-')
        createBuildJob(folderName, "${branchNameDisplay}_buildJob", projectGitUrl, branchName, giteaProject)
        if(payloadJson!=null && payloadJson.get(1)=="refs/heads/${branchName}"){
            println "!!!!! ${branchNameDisplay}_buildJob queued by hook !!!!!\n"
            queue("${folderName}/${branchNameDisplay}_buildJob")
        }
    } else {
        if(prefix!="master"){
            println "Branch not suported by gitflow"
        }
    }
}
println "### Seed processing completed ###\n"

def createBuildJob(folderName, jobName, projectGitUrl, branchName, giteaProject){
    println "\n** Creating job ${folderName}/${jobName} **"
    switch(projectType){
        case "dockerApp":
            pipelineJob("${folderName}/${jobName}") {
                definition {
                    cps {
                        script("""
                            @Library('jenkins-automation') _
                            dockerAppBuildPipeline {
                                scmUrl = "${projectGitUrl}"
                                branch = "${branchName}"
                                project = "${giteaProject}"
                            }""".stripIndent())
                        sandbox()
                    }
                }
            }
        break
    }
    println "** Job ${folderName}/${jobName} was created **\n"
}
