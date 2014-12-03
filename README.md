activemq-wildfly-integration
============================

Repository to hold the necessary pieces to integrate ActiveMQ6 with WildFly.

Running tests
==============

The tests have a dependency on ActiveMQ-6 integration-tests test-jar.  This test-jar is not currently released as part
of ActiveMQ-6.  Therefore you must checkout the ActiveMQ-6 source and build locally.  To do this:

```bash
  git clone https://github.com/apache/activemq-6
  cd activemq-6
  mvn clean install
```

Once the build of activemq-6 has complete the appropriate jars will be added to your local .m2 directory and you are 
able to run the tests from the activemq-wildfly-integration directory:

```bash
  mvn clean test
```
