/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.jboss.activemq.artemis.wildfly.integration.recovery;

import javax.transaction.xa.XAResource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.service.extensions.xa.recovery.ActiveMQXAResourceWrapper;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.jboss.activemq.artemis.wildfly.integration.WildFlyActiveMQXAResourceWrapper;
import org.jboss.tm.XAResourceRecovery;

/**
* <p>This class is used by the Resource Adapter to register RecoveryDiscovery, which is based on the {@link org.apache.activemq.wildfly.integration.recovery.XARecoveryConfig}</p>
* <p>Each outbound or inboud connection will pass the configuration here through by calling the method {@link WildFlyActiveMQRecoveryRegistry#register(org.apache.activemq.wildfly.integration.recovery.XARecoveryConfig)}</p>
* <p>Later the {@link WildFlyRecoveryDiscovery} will call {@link WildFlyActiveMQRecoveryRegistry#nodeUp(String, org.apache.activemq.api.core.Pair, String, String)}
* so we will keep a track of nodes on the cluster
* or nodes where this server is connected to. </p>
*
* @author clebertsuconic
*/
public class WildFlyActiveMQRecoveryRegistry implements XAResourceRecovery
{
   private static final String PRODUCT_NAME = "ActiveMQ Artemis";

   private static final WildFlyActiveMQRecoveryRegistry theInstance = new WildFlyActiveMQRecoveryRegistry();

   private final ConcurrentHashMap<XARecoveryConfig, WildFlyRecoveryDiscovery> configSet = new ConcurrentHashMap<XARecoveryConfig, WildFlyRecoveryDiscovery>();

   /**
    * The list by server id and resource adapter wrapper, what will actually be calling recovery.
    * This will be returned by getXAResources
    */
   private final ConcurrentHashMap<String, WildFlyActiveMQXAResourceWrapper> recoveries = new ConcurrentHashMap<String, WildFlyActiveMQXAResourceWrapper>();

   /**
    * In case of failures, we retry on the next getXAResources
    */
   private final Set<WildFlyRecoveryDiscovery> failedDiscoverySet = new HashSet<WildFlyRecoveryDiscovery>();

   private WildFlyActiveMQRecoveryRegistry()
   {
   }

   /**
    * This will be called periodically by the Transaction Manager
    */
   public XAResource[] getXAResources()
   {
      try
      {
         checkFailures();

         WildFlyActiveMQXAResourceWrapper[] resourceArray = new WildFlyActiveMQXAResourceWrapper[recoveries.size()];
         resourceArray = recoveries.values().toArray(resourceArray);

         if (WildFlyActiveMQLogger.LOGGER.isDebugEnabled())
         {
            WildFlyActiveMQLogger.LOGGER.debug("\n=======================================================================================");
            WildFlyActiveMQLogger.LOGGER.debug("Returning the following list on getXAREsources:");
            for (Map.Entry<String, WildFlyActiveMQXAResourceWrapper> entry : recoveries.entrySet())
            {
               WildFlyActiveMQLogger.LOGGER.debug("server-id=" + entry.getKey() + ", value=" + entry.getValue());
            }
            WildFlyActiveMQLogger.LOGGER.debug("=======================================================================================\n");
         }

         return resourceArray;
      }
      catch (Throwable e)
      {
         WildFlyActiveMQLogger.LOGGER.warn(e.getMessage(), e);
         return new XAResource[]{};
      }
   }

   public static WildFlyActiveMQRecoveryRegistry getInstance()
   {
      return theInstance;
   }

   /**
    * This will be called by then resource adapters, to register a new discovery
    *
    * @param resourceConfig
    */
   public void register(final XARecoveryConfig resourceConfig)
   {
      WildFlyRecoveryDiscovery newInstance = new WildFlyRecoveryDiscovery(resourceConfig);
      WildFlyRecoveryDiscovery discoveryRecord = configSet.putIfAbsent(resourceConfig, newInstance);
      if (discoveryRecord == null)
      {
         discoveryRecord = newInstance;
         discoveryRecord.start(false);
      }
      // you could have a configuration shared with multiple MDBs or RAs
      discoveryRecord.incrementUsage();
   }

   /**
    * Reference counts and deactivate a configuration
    * Notice: this won't remove the servers since a server may have previous XIDs
    *
    * @param resourceConfig
    */
   public void unRegister(final XARecoveryConfig resourceConfig)
   {
      WildFlyRecoveryDiscovery discoveryRecord = configSet.get(resourceConfig);
      if (discoveryRecord != null && discoveryRecord.decrementUsage() == 0)
      {
         discoveryRecord = configSet.remove(resourceConfig);
         if (discoveryRecord != null)
         {
            discoveryRecord.stop();
         }
      }
   }

   /**
    * We need to make sure that all resources are closed, we don't actually do this when a resourceConfig is closed but
    * maybe we should.
    */
   public void stop()
   {
      for (WildFlyRecoveryDiscovery recoveryDiscovery : configSet.values())
      {
         recoveryDiscovery.stop();
      }
      for (WildFlyActiveMQXAResourceWrapper wrapper : recoveries.values())
      {
         ((ActiveMQXAResourceWrapper) wrapper.getResource()).close();
      }
      recoveries.clear();
      configSet.clear();
   }

   /**
    * in case of a failure the Discovery will register itslef to retry
    *
    * @param failedDiscovery
    */
   public void failedDiscovery(WildFlyRecoveryDiscovery failedDiscovery)
   {
      WildFlyActiveMQLogger.LOGGER.debug("RecoveryDiscovery being set to restart:" + failedDiscovery);
      synchronized (failedDiscoverySet)
      {
         failedDiscoverySet.add(failedDiscovery);
      }
   }

   /**
    * @param listeningConfig the connection factory where we are listening topology updates.
    * @param nodeID the node id coming from the topology update
    * @param networkConfiguration the network configuration
    * @param username the user name
    * @param password the password
    * @param properties the properties passed to the configuration
    */
   public void nodeUp(XARecoveryConfig listeningConfig,
                      String nodeID,
                      Pair<TransportConfiguration, TransportConfiguration> networkConfiguration,
                      String username,
                      String password,
                      Map<String, String> properties)
   {

      if (recoveries.get(nodeID) == null)
      {
         if (WildFlyActiveMQLogger.LOGGER.isDebugEnabled())
         {
            WildFlyActiveMQLogger.LOGGER.debug(nodeID + " being registered towards " + networkConfiguration);
         }

         XARecoveryConfig config = new XARecoveryConfig(true,
                                                        extractTransportConfiguration(listeningConfig.getTransportConfig(), networkConfiguration),
                                                        username,
                                                        password,
                                                        properties,
                                                        listeningConfig.getClientProtocolManager());

         ActiveMQXAResourceWrapper xaResource = new ActiveMQXAResourceWrapper(config);

         Map<String, Object> xaResourceProperties = new HashMap<String, Object>();
         xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_JNDI_NAME, properties.get(XARecoveryConfig.JNDI_NAME_PROPERTY_KEY));
         xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_NODE_ID, nodeID);
         xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_NAME, PRODUCT_NAME);
         xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_VERSION, VersionLoader.getVersion().getFullVersion());

         WildFlyActiveMQXAResourceWrapper wrapper = new WildFlyActiveMQXAResourceWrapper(xaResource, xaResourceProperties);
         recoveries.putIfAbsent(nodeID, wrapper);
      }
   }

   public void nodeDown(String nodeID)
   {
   }

   /**
    * this will go through the list of retries
    */
   private void checkFailures()
   {
      final HashSet<WildFlyRecoveryDiscovery> failures = new HashSet<WildFlyRecoveryDiscovery>();

      // it will transfer all the discoveries to a new collection
      synchronized (failedDiscoverySet)
      {
         failures.addAll(failedDiscoverySet);
         failedDiscoverySet.clear();
      }

      if (failures.size() > 0)
      {
         // This shouldn't happen on a regular scenario, however when this retry happens this needs
         // to be done on a new thread
         Thread t = new Thread("ActiveMQ Recovery Discovery Reinitialization")
         {
            @Override
            public void run()
            {
               for (WildFlyRecoveryDiscovery discovery : failures)
               {
                  try
                  {
                     WildFlyActiveMQLogger.LOGGER.debug("Retrying discovery " + discovery);
                     discovery.start(true);
                  }
                  catch (Throwable e)
                  {
                     WildFlyActiveMQLogger.LOGGER.warn(e.getMessage(), e);
                  }
               }
            }
         };

         t.start();
      }
   }

   /**
    * @param networkConfiguration
    * @return
    */
   private TransportConfiguration[] extractTransportConfiguration(TransportConfiguration[] configurations, Pair<TransportConfiguration, TransportConfiguration> networkConfiguration)
   {
//      SSLContext sslContext = null;
//      for(int i =0; i < configurations.length && sslContext == null; i++) {
//         sslContext = configurations[i].getSslContext();
//      }
//      if (networkConfiguration.getB() != null)
//      {
//         return new TransportConfiguration[]{networkConfiguration.getA().setSslContext(sslContext), networkConfiguration.getB().setSslContext(sslContext)};
//      }
//      return new TransportConfiguration[]{networkConfiguration.getA().setSslContext(sslContext)};
       if (networkConfiguration.getB() != null)
      {
         return new TransportConfiguration[]{networkConfiguration.getA(), networkConfiguration.getB()};
      }
      return new TransportConfiguration[]{networkConfiguration.getA()};
   }

}
