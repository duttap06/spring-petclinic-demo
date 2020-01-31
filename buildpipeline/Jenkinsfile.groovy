#!groovy
def mvnCmd = "mvn -B -s buildpipeline/maven-settings.xml"
def appName = "petclinic"
def version
def project = env.PROJECT_NAME

pipeline {
    agent none
    options {
		skipDefaultCheckout()
	}
    stages {
        stage('Check out') {
            agent {
                label "jos-m3-openjdk8"
            }
            steps {
                checkout scm
                // stash workspace
				stash(name: 'ws', includes: '**', excludes: '**/.git/**')
            }
        }
        stage('Maven build') {
            agent {
                label "jos-m3-openjdk8"
            }
            steps {
                unstash 'ws'
				sh(script: "${mvnCmd} -DskipTests clean package -Ps2i" )
				stash name: 'jar', includes: '**/target/**/*'
            }    
        }
        stage('Maven unit tests') {
            agent {
                label "jos-m3-openjdk8"
            }
            steps {
                unstash 'ws'
                sh(script: "${mvnCmd} test -DskipTests")
            }
		}
        stage('Create Image Builder') {
            when {
                expression {
                    openshift.withCluster() {
                        return !openshift.selector("bc", "${appName}").exists();
                    }
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.newBuild("--name=${appName}", "--image-stream=openjdk-8-rhel8:latest", "--binary=true")
                    }
                }
            }
        }
        stage('Build Image') {
            agent {
                label "jos-m3-openjdk8"
            }
            steps {
                unstash 'jar'
                script {
                    openshift.withCluster() {
                        openshift.selector("bc", "${appName}").startBuild("--from-file=target/spring-petclinic.jar", "--wait=true")
                    }
                }
            }
        }
        stage('Create deployment in O') {
            agent {
                label "jos-m3-openjdk8"
            }
            when {
                expression {
                    openshift.withCluster() {
                        return !openshift.selector('dc', "${appName}").exists()
                    }
                }
            }
            steps {
                unstash 'ws'
                script {
                    openshift.withCluster() {
                        // ruim eerst de objecten als die zijn blijven staan
                        if (openshift.selector('dc', "${appName}").exists()) {
                            openshift.selector('dc', "${appName}").delete()
                        }
                        if (openshift.selector('svc', "${appName}").exists()) {
                            openshift.selector('svc', "${appName}").delete()
                        }
                        if (openshift.selector('route', "${appName}").exists()) {
                            openshift.selector('route', "${appName}").delete()
                        }
                        
                        result = openshift.raw("apply", "-f openshift/petclinic-template-ont.yaml")
                        // dit template moet een deployment hebben van het image met tag 'latest'
                        // er zit geen trigger in om te deployen bij image change

                        // stel dat het trigger er toch is, deze op manual zetten, Jenkins is in control
                        openshift.set("triggers", "dc/${appName}", "--manual")
                    }
                }
            }
        }
        stage('Deploy in O') {
            steps {
                script {
                    openshift.withCluster() {
                        def dc = openshift.selector("dc", "${appName}")
                        dc.rollout().latest();
                        // wachten tot alle replicas beschikbaar zijn.
                        while (dc.object().spec.replicas != dc.object().status.availableReplicas) {
                            echo "Wait for all replicas are available"
                            sleep 10
                        }
                    }
                }
            }
        }
        
    }
}
