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

import java.util.Arrays;
import javax.transaction.xa.XAResource;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.service.extensions.xa.recovery.ActiveMQXAResourceWrapper;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.jboss.activemq.artemis.wildfly.integration.WildFlyActiveMQXAResourceWrapper;
import org.jboss.tm.XAResourceRecovery;

/**
 * <p>
 * This class is used by the Resource Adapter to register RecoveryDiscovery, which is based on the
 * {@link org.apache.activemq.wildfly.integration.recovery.XARecoveryConfig}</p>
 * <p>
 * Each outbound or inboud connection will pass the configuration here through by calling the method
 * {@link WildFlyActiveMQRecoveryRegistry#register(org.apache.activemq.wildfly.integration.recovery.XARecoveryConfig)}</p>
 * <p>
 * Later the {@link WildFlyRecoveryDiscovery} will call
 * {@link WildFlyActiveMQRecoveryRegistry#nodeUp(String, org.apache.activemq.api.core.Pair, String, String)}
 * so we will keep a track of nodes on the cluster
 * or nodes where this server is connected to. </p>
 *
 * @author clebertsuconic
 */
public class WildFlyActiveMQRecoveryRegistry implements XAResourceRecovery {

    private static final String PRODUCT_NAME = "ActiveMQ Artemis";

    private static final WildFlyActiveMQRecoveryRegistry theInstance = new WildFlyActiveMQRecoveryRegistry();

    private final ConcurrentHashMap<XARecoveryConfig, WildFlyRecoveryDiscovery> configSet = new ConcurrentHashMap<>();

    /**
     * The list by server id and resource adapter wrapper, what will actually be calling recovery.
     * This will be returned by getXAResources
     */
    private final ConcurrentHashMap<String, WildFlyActiveMQXAResourceWrapper> recoveries = new ConcurrentHashMap<>();

    /**
     * Recovery wrappers built directly from the registered connections, keyed by their configuration.
     * Used as a fallback when no topology {@code nodeUp} has populated {@link #recoveries} (e.g. an
     * in-vm or non-clustered connection whose broker never advertises a topology update).
     */
    private final ConcurrentHashMap<XARecoveryConfig, WildFlyActiveMQXAResourceWrapper> fallbackRecoveries = new ConcurrentHashMap<>();

    /**
     * In case of failures, we retry on the next getXAResources
     */
    private final Set<WildFlyRecoveryDiscovery> failedDiscoverySet = new HashSet<WildFlyRecoveryDiscovery>();

    private WildFlyActiveMQRecoveryRegistry() {
    }

    /**
     * This will be called periodically by the Transaction Manager
     *
     * @return the XAResources managed by this registry
     */
    @Override
    public XAResource[] getXAResources() {
        try {
            checkFailures();

            for (WildFlyActiveMQXAResourceWrapper resource : recoveries.values()) {
                if (resource.isObsolete()) {
                    resource.update();
                }
            }

            // Topology nodeUp() is the primary source of recovery resources. However a connection
            // whose broker never advertises a topology nodeUp (e.g. an in-vm or non-clustered
            // connection) leaves 'recoveries' empty and would expose no XAResource at all, so its
            // in-doubt branches could never be recovered. In that case fall back to recovery wrappers
            // built from the connections registered directly by the resource adapter.
            Collection<WildFlyActiveMQXAResourceWrapper> resources;
            if (recoveries.isEmpty()) {
                resources = fallbackRecoveryResources();
            } else {
                closeFallbackRecoveries();
                resources = recoveries.values();
            }

            if (WildFlyActiveMQLogger.LOGGER.isDebugEnabled()) {
                WildFlyActiveMQLogger.LOGGER.debug("\n=======================================================================================");
                WildFlyActiveMQLogger.LOGGER.debug("Returning the following list on getXAREsources:");
                if (!recoveries.isEmpty()) {
                    for (Map.Entry<String, WildFlyActiveMQXAResourceWrapper> entry : recoveries.entrySet()) {
                        WildFlyActiveMQLogger.LOGGER.debug("server-id=" + entry.getKey() + ", value=" + entry.getValue());
                    }
                } else {
                    for (WildFlyActiveMQXAResourceWrapper wrapper : resources) {
                        WildFlyActiveMQLogger.LOGGER.debug("value=" + wrapper);
                    }
                }
                WildFlyActiveMQLogger.LOGGER.debug("=======================================================================================\n");
            }

            return resources.toArray(new XAResource[0]);
        } catch (Throwable e) {
            WildFlyActiveMQLogger.LOGGER.warn(e.getMessage(), e);
            return new XAResource[]{};
        }
    }

    /**
     * Fallback used when no topology {@code nodeUp} has populated {@link #recoveries}: builds (and
     * caches) one recovery wrapper per directly-registered connection, from the connection's own
     * transport configuration, so its in-doubt branches remain recoverable without a topology
     * advertisement. Wrappers are cached in {@link #fallbackRecoveries} to avoid opening a fresh
     * recovery connection on every scan, and dropped once their connection is no longer registered.
     */
    private Collection<WildFlyActiveMQXAResourceWrapper> fallbackRecoveryResources() {
        for (XARecoveryConfig config : configSet.keySet()) {
            fallbackRecoveries.computeIfAbsent(config, WildFlyActiveMQRecoveryRegistry::createRecoveryWrapper);
        }
        fallbackRecoveries.entrySet().removeIf(entry -> {
            if (!configSet.containsKey(entry.getKey())) {
                ((ActiveMQXAResourceWrapper) entry.getValue().getResource()).close();
                return true;
            }
            return false;
        });
        return fallbackRecoveries.values();
    }

    private void closeFallbackRecoveries() {
        if (!fallbackRecoveries.isEmpty()) {
            for (WildFlyActiveMQXAResourceWrapper wrapper : fallbackRecoveries.values()) {
                ((ActiveMQXAResourceWrapper) wrapper.getResource()).close();
            }
            fallbackRecoveries.clear();
        }
    }

    private static WildFlyActiveMQXAResourceWrapper createRecoveryWrapper(XARecoveryConfig config) {
        ActiveMQXAResourceWrapper xaResource = new ActiveMQXAResourceWrapper(config);
        Map<String, String> configProperties = config.getProperties();
        String jndiName = configProperties == null ? null : configProperties.get(XARecoveryConfig.JNDI_NAME_PROPERTY_KEY);
        Map<String, Object> xaResourceProperties = new HashMap<>();
        xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_JNDI_NAME, jndiName);
        xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_NODE_ID, jndiName);
        xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_NAME, PRODUCT_NAME);
        xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_VERSION, VersionLoader.getVersion().getFullVersion());
        return new WildFlyActiveMQXAResourceWrapper(xaResource, xaResourceProperties);
    }

    public static WildFlyActiveMQRecoveryRegistry getInstance() {
        return theInstance;
    }

    /**
     * This will be called by then resource adapters, to register a new discovery
     *
     * @param resourceConfig
     */
    public void register(final XARecoveryConfig resourceConfig) {
        WildFlyRecoveryDiscovery newInstance = new WildFlyRecoveryDiscovery(resourceConfig);
        WildFlyRecoveryDiscovery discoveryRecord = configSet.putIfAbsent(resourceConfig, newInstance);
        if (discoveryRecord == null) {
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
    public void unRegister(final XARecoveryConfig resourceConfig) {
        WildFlyRecoveryDiscovery discoveryRecord = configSet.get(resourceConfig);
        if (discoveryRecord != null && discoveryRecord.decrementUsage() == 0) {
            discoveryRecord = configSet.remove(resourceConfig);
            if (discoveryRecord != null) {
                discoveryRecord.stop();
            }
            WildFlyActiveMQXAResourceWrapper fallback = fallbackRecoveries.remove(resourceConfig);
            if (fallback != null) {
                ((ActiveMQXAResourceWrapper) fallback.getResource()).close();
            }
        }
    }

    /**
     * We need to make sure that all resources are closed, we don't actually do this when a resourceConfig is closed but
     * maybe we should.
     */
    public void stop() {
        for (WildFlyRecoveryDiscovery recoveryDiscovery : configSet.values()) {
            recoveryDiscovery.stop();
        }
        for (WildFlyActiveMQXAResourceWrapper wrapper : recoveries.values()) {
            ((ActiveMQXAResourceWrapper) wrapper.getResource()).close();
        }
        for (WildFlyActiveMQXAResourceWrapper wrapper : fallbackRecoveries.values()) {
            ((ActiveMQXAResourceWrapper) wrapper.getResource()).close();
        }
        recoveries.clear();
        fallbackRecoveries.clear();
        configSet.clear();
    }

    /**
     * in case of a failure the Discovery will register itself to retry
     *
     * @param failedDiscovery
     */
    public void failedDiscovery(WildFlyRecoveryDiscovery failedDiscovery) {
        synchronized (failedDiscoverySet) {
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
            TransportConfiguration[] networkConfiguration,
            String username,
            String password,
            Map<String, String> properties) {

        XARecoveryConfig config = new XARecoveryConfig(true,
                networkConfiguration,
                username,
                password,
                properties,
                listeningConfig.getClientProtocolManager(),
                listeningConfig.getLocatorConfig());
        WildFlyActiveMQXAResourceWrapper wrapper = recoveries.get(nodeID);
        if (wrapper == null) {
            if (WildFlyActiveMQLogger.LOGGER.isDebugEnabled()) {
                WildFlyActiveMQLogger.LOGGER.debug(nodeID + " being registered towards " + Arrays.toString(networkConfiguration));
            }
            ActiveMQXAResourceWrapper xaResource = new ActiveMQXAResourceWrapper(config);

            Map<String, Object> xaResourceProperties = new HashMap<>();
            xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_JNDI_NAME, properties.get(XARecoveryConfig.JNDI_NAME_PROPERTY_KEY));
            xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_NODE_ID, nodeID);
            xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_NAME, PRODUCT_NAME);
            xaResourceProperties.put(org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_VERSION, VersionLoader.getVersion().getFullVersion());

            wrapper = new WildFlyActiveMQXAResourceWrapper(xaResource, xaResourceProperties);
            recoveries.put(nodeID, wrapper);
        } else {
            if (isNetworkConfigurationUpdateRequired(listeningConfig.getTransportConfig(), networkConfiguration)) {
                if (WildFlyActiveMQLogger.LOGGER.isDebugEnabled()) {
                    WildFlyActiveMQLogger.LOGGER.debug("Updating " + wrapper + " configuration");
                }
                wrapper.updateRecoveryConfig(config);
            }
        }
    }

    private boolean isNetworkConfigurationUpdateRequired(TransportConfiguration[] initialNetworkConfiguration, TransportConfiguration[] networkConfiguration) {
        if (networkConfiguration == null) {
            return false;
        }
        if (initialNetworkConfiguration == null || initialNetworkConfiguration.length < networkConfiguration.length) {
            return true;
        }
        if (initialNetworkConfiguration.length > networkConfiguration.length) {
            if (WildFlyActiveMQLogger.LOGGER.isDebugEnabled()) {
                WildFlyActiveMQLogger.LOGGER.debug("initialNetworkConfiguration has more elements then networkConfiguration");
            }
            return false;
        }
        for (int i = 0; i < initialNetworkConfiguration.length; i++) {
            if (initialNetworkConfiguration[i] == null || networkConfiguration[i] == null) {
                if (initialNetworkConfiguration[i] != networkConfiguration[i]) {
                    return true;
                }
                continue;
            }
            if (!initialNetworkConfiguration[i].isSameParams(networkConfiguration[i])) {
                if (WildFlyActiveMQLogger.LOGGER.isDebugEnabled()) {
                    WildFlyActiveMQLogger.LOGGER.debug(initialNetworkConfiguration[i] + " is different from " + networkConfiguration[i]);
                }
                return true;
            }
        }
        if (WildFlyActiveMQLogger.LOGGER.isDebugEnabled()) {
            WildFlyActiveMQLogger.LOGGER.debug(Arrays.toString(initialNetworkConfiguration) + " is the same as " + Arrays.toString(networkConfiguration));
        }
        return false;
    }

    public void nodeDown(String nodeID) {
    }

    /**
     * this will go through the list of retries
     */
    private void checkFailures() {
        final HashSet<WildFlyRecoveryDiscovery> failures = new HashSet<WildFlyRecoveryDiscovery>();

        // it will transfer all the discoveries to a new collection
        synchronized (failedDiscoverySet) {
            failures.addAll(failedDiscoverySet);
            failedDiscoverySet.clear();
        }

        if (!failures.isEmpty()) {
            // This shouldn't happen on a regular scenario, however when this retry happens this needs
            // to be done on a new thread
            Thread t = new Thread("ActiveMQ Recovery Discovery Reinitialization") {
                @Override
                public void run() {
                    for (WildFlyRecoveryDiscovery discovery : failures) {
                        try {
                            WildFlyActiveMQLogger.LOGGER.debug("Retrying discovery " + discovery);
                            discovery.start(true);
                        } catch (Throwable e) {
                            WildFlyActiveMQLogger.LOGGER.warn(e.getMessage(), e);
                        }
                    }
                }
            };

            t.start();
        }
    }

    @Override
    public String toString() {
        return "WildFlyActiveMQRecoveryRegistry{" + "configSet=" + configSet + ", recoveries=" + recoveries + ", fallbackRecoveries=" + fallbackRecoveries + ", failedDiscoverySet=" + failedDiscoverySet + '}';
    }

}
