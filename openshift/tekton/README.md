# OpenShift Pipelines Tutorial
## Deploy Sample Application

Create a project for the sample application that you will be using in this tutorial:

```bash
$ oc new-project roadshow-pipelines
```

Building container images using build tools such as S2I, Buildah, Kaniko, etc require privileged access to the cluster. OpenShift default security settings do not allow privileged containers unless specifically configured. Create a service account for running pipelines and enable it to run privileged pods for building images:

```
$ oc create serviceaccount pipeline
$ oc adm policy add-scc-to-user privileged -z pipeline
$ oc adm policy add-role-to-user edit -z pipeline
```

You will use the [Spring PetClinic](https://github.com/spring-projects/spring-petclinic) sample application during this tutorial, which is a simple Spring Boot application.

Create the Kubernetes objects for deploying the PetClinic app on OpenShift. The deployment will not complete since there are no container images built for the PetClinic application yet. That you will do in the following sections through a CI/CD pipeline:

```bash
$ oc create -f petclinic.yaml
```

You should be able to see the deployment in the OpenShift Web Console.

## Install Tasks

`Task`s consist of a number of steps that are executed sequentially. Each `task` is executed in a separate container within the same pod. They can also have inputs and outputs in order to interact with other tasks in the pipeline.

```bash
$ oc create -f openshift-client-task.yaml
$ oc create -f s2i-java-8-task.yaml
```
You can take a look at the list of install `task`s using the [Tekton CLI](https://github.com/tektoncd/cli/releases):

```
$ tkn task ls

NAME               AGE
openshift-client   58 seconds ago
s2i-java-8         1 minute ago
```

## Create Pipeline

A pipeline defines a number of tasks that should be executed and how they interact with each other via their inputs and outputs.

In this tutorial, you will create a pipeline that takes the source code of PetClinic application from GitHub and then builds and deploys it on OpenShift using [Source-to-Image (S2I)](https://docs.openshift.com/container-platform/4.1/builds/understanding-image-builds.html#build-strategy-s2i_understanding-image-builds).


Create the pipeline by running the following:

```bash
$ oc create -f petclinic-deploy-pipeline.yaml
```

Check the list of pipelines you have created using the CLI:

```
$ tkn pipeline ls

NAME                       AGE              LAST RUN   STARTED   DURATION   STATUS
petclinic-deploy-pipeline  25 seconds ago   ---        ---       ---        ---
```

## Trigger Pipeline

Create the above pipeline resources by running the following:

```bash
$ oc create -f petclinic-resources.yaml
```

You can see the list of resources created using the CLI:

```bash
$ tkn resource ls

NAME              TYPE    DETAILS
petclinic-git     git     url: https://github.com/spring-projects/spring-petclinic
petclinic-image   image   url: image-registry.openshift-image-registry.svc:5000/pipelines-tutorial/spring-petclinic
```

A `PipelineRun` is how you can start a pipeline and tie it to the Git and image resources that should be used for this specific invocation. You can start the pipeline using the CLI:

```bash
$ tkn pipeline start petclinic-deploy-pipeline \
        -r app-git=petclinic-git \
        -r app-image=petclinic-image \
        -s pipeline

Pipelinerun started: petclinic-deploy-pipeline-run-q62p8
```

The `-r` flag specifies the `PipelineResource`s that should be provided to the pipeline and the `-s` flag specifies the service account to be used for running the pipeline. 
