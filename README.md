# JBoss A-MQ on Spring Boot

JBoss A-MQ running in a Spring Boot container.

## Running the example standalone

```
$ mvn spring-boot:run
```

## Running the example in OpenShift

It is assumed that:

- OpenShift platform is already running, if not you can find details how to [Install OpenShift at your site](https://docs.openshift.com/container-platform/3.3/install_config/index.html).
- Your system is configured for Fabric8 Maven Workflow, if not you can find a [Get Started Guide](https://access.redhat.com/documentation/en/red-hat-jboss-middleware-for-openshift/3/single/red-hat-jboss-fuse-integration-services-20-for-openshift/)
- You've already created a [PersistentVolume](https://kubernetes.io/docs/concepts/storage/persistent-volumes/) of sufficient size (at least what is requested in src/main/kube/pvc.yaml).

Create a new project:

```
$ oc new-project spring-boot-amq
```

Create the [ServiceAccount](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/):

```
$ oc create -f src/main/kube/serviceaccount.yml
```

Create the [PersistentVolumeClaim](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#persistentvolumeclaims):

```
$ oc create -f src/main/kube/pvc.yml
```

Create the [ConfigMap](https://kubernetes.io/docs/user-guide/configmap/):

```
$ oc create -f src/main/kube/configmap.yml
```

Create the [Secret](https://kubernetes.io/docs/concepts/configuration/secret/):

```
$ oc create -f src/main/kube/secret.yml
```

Add the [Secret](https://kubernetes.io/docs/concepts/configuration/secret/) to the [ServiceAccount](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/) created earlier:

```
$ oc secrets add sa/spring-boot-amq-sa secret/spring-boot-amq-secret
```

Add the view role to the [ServiceAccount](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/):

```
$ oc policy add-role-to-user view system:serviceaccount:spring-boot-amq:spring-boot-amq-sa
```

The example can be built and run on OpenShift using a single goal:

```
$ cd $PROJECT_ROOT
$ mvn fabric8:deploy
```
