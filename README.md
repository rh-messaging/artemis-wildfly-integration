artemis-wildfly-integration
============================

Repository to hold the necessary pieces to integrate ActiveMQ Artemis with WildFly.

Running tests
==============

The tests have a dependency on ActiveMQ Artemis integration-tests test-jar.  This test-jar is not currently released as part
of ActiveMQ Artemis. Therefore you must checkout the ActiveMQ Artemis source and build locally.  To do this:

```bash
  git clone https://github.com/apache/activemq-artemis
  cd activemq-artemis
  mvn clean install
```

Once the build of activemq Artemis has complete the appropriate jars will be added to your local .m2 directory and you are 
able to run the tests from the artemis-wildfly-integration directory:

```bash
  mvn clean test
```


Releasing
===========


```bash
  git checkout main
  git pull origin main
  mvn release:prepare -DpushChanges=false -DlocalCheckout=true -Darguments="-Drelease=true"
  mvn release:perform -DlocalCheckout=true -Pjboss-release -Darguments="-Drelease=true"
  git push origin main
  git push origin --tags
```
