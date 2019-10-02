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
                sh(script: "${mvnCmd} test")
            }
            post {
				success {
					junit '**/surefire-reports/**/*.xml'
				}
            }
		}
        stage('Create Image Builder') {
            when {
                expression {
                    openshift.withCluster() {
                        return !openshift.selector("bc", "petclinic").exists();
                    }
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.newBuild("--name=petclinic", "-i=public/openjdk18-openshift:1.6", "--binary=true")
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
                        openshift.selector("bc", "petclinic").startBuild("--from-file=target/spring-petclinic.jar", "--wait=true")
                    }
                }
            }
        }
        stage('Create deployment in O') {
            when {
                expression {
                    openshift.withCluster() {
                        return !openshift.selector('dc', 'petclinic').exists()
                    }
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        def app = openshift.newApp("petclinic:latest")
                        openshift.raw('create route edge --service=petclinic --port=8080');
                        //app.narrow("svc").expose();

                        // geen triggers op redeploy wanneer het image veranderd. Jenkins is in control
                        openshift.set("triggers", "dc/petclinic", "--manual")
                        //openshift.set("probe dc/petclinic --readiness --get-url=http://:8080/health --initial-delay-seconds=30 --failure-threshold=10 --period-seconds=10")
                        //openshift.set("probe dc/petclinic --liveness  --get-url=http://:8080/health --initial-delay-seconds=30 --failure-threshold=10 --period-seconds=10")
                        def dc = openshift.selector("dc", "petclinic")
                        
                        //door newApp() wordt er gelijk al een deployment gestart, ondanks de manual triggers

                        // wachten tot alle replicas beschikbaar zijn.
                        while (dc.object().spec.replicas != dc.object().status.availableReplicas) {
                            echo "Wait for all replicas are available"
                            sleep 10
                        }
                    }
                }
            }
        }
        stage('Deploy in O') {
            steps {
                script {
                    openshift.withCluster() {
                        def dc = openshift.selector("dc", "petclinic")
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
