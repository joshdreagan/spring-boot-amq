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
$ mvn fabric8:deploy
```

## Notes

- The [Secret](https://kubernetes.io/docs/concepts/configuration/secret/) values in the src/main/kube/secret.yaml file are base64 encoded strings. So you can use the `base64` utility (or whatever is your favorite) to encode them. Just be careful of the new-lines. Here's some examples:
  - `echo -n 'password' | base64 --lines 0`
  - `base64 --lines 0 --input ./broker.ks`
- To establish the [NetworkConnector](http://activemq.apache.org/networks-of-brokers.html) inside of OpenShift, I used the `kube` discovery agent found in the `openshift-activemq-plugin` dependency. I could not find it in any of the maven repos, so you'll likely have to clone down the project [[https://github.com/jboss-openshift/openshift-ping](https://github.com/jboss-openshift/openshift-ping)], checkout the `1.2.1.Final` branch, and do a `mvn clean install` to install it into your local repo.
