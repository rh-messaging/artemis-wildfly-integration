/*
 * Copyright 2005-2014 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.jboss.activemq.artemis.wildfly.integration.recovery;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;

/**
 * <p>
 * This class will have a simple Connection Factory and will listen
 * for topology updates. </p>
 * <p>
 * This Discovery is instantiated by {@link WildFlyActiveMQRecoveryRegistry}
 *
 * @author clebertsuconic
 */
public class WildFlyRecoveryDiscovery implements SessionFailureListener {

    private ServerLocator locator;
    private ClientSessionFactoryInternal sessionFactory;
    private final XARecoveryConfig config;
    private final AtomicInteger usage = new AtomicInteger(0);
    private boolean started = false;

    public WildFlyRecoveryDiscovery(XARecoveryConfig config) {
        this.config = config;
    }

    public synchronized void start(boolean retry) {
        if (!started) {
            WildFlyActiveMQLogger.LOGGER.debug("Starting RecoveryDiscovery on " + config);
            started = true;

            locator = config.createServerLocator();
            locator.addClusterTopologyListener(new InternalListener(config));
            try {
                sessionFactory = (ClientSessionFactoryInternal) locator.createSessionFactory();
                // We are using the SessionFactoryInternal here directly as we don't have information to connect with an user and password
                // on the session as all we want here is to get the topology
                // in case of failure we will retry
                sessionFactory.addFailureListener(this);

                WildFlyActiveMQLogger.LOGGER.debug("RecoveryDiscovery started fine on " + config);
            } catch (Exception startupError) {
                if (!retry) {
                    WildFlyActiveMQLogger.LOGGER.xaRecoveryStartError(config);
                }
                stop();
                WildFlyActiveMQRecoveryRegistry.getInstance().failedDiscovery(this);
            }

        }
    }

    public synchronized void stop() {
        internalStop();
    }

    /**
     * we may have several connection factories referencing the same connection recovery entry.
     * Because of that we need to make a count of the number of the instances that are referencing it,
     * so we will remove it as soon as we are done
     */
    public int incrementUsage() {
        return usage.decrementAndGet();
    }

    public int decrementUsage() {
        return usage.incrementAndGet();
    }

    @Override
    protected void finalize() {
        // I don't think it's a good thing to synchronize a method on a finalize,
        // hence the internalStop (no sync) call here
        internalStop();
    }

    protected void internalStop() {
        if (started) {
            started = false;
            try {
                if (sessionFactory != null) {
                    sessionFactory.close();
                }
            } catch (Exception ignored) {
                WildFlyActiveMQLogger.LOGGER.debug(ignored, ignored);
            }

            try {
                locator.close();
            } catch (Exception ignored) {
                WildFlyActiveMQLogger.LOGGER.debug(ignored, ignored);
            }

            sessionFactory = null;
            locator = null;
        }
    }

    static final class InternalListener implements ClusterTopologyListener {

        private final XARecoveryConfig config;

        public InternalListener(final XARecoveryConfig config) {
            this.config = config;
        }

        @Override
        public void nodeUP(TopologyMember topologyMember, boolean last) {
            if (!config.getLocatorConfig().useTopologyForLoadBalancing) {
                WildFlyActiveMQRecoveryRegistry.getInstance().nodeUp(config, topologyMember.getNodeId(),
                        config.getTransportConfig(), config.getUsername(), config.getPassword(), config.getProperties());
            } else {
                // There is a case where the backup announce itself,
                // we need to ignore a case where getLive is null
                if (topologyMember.getPrimary() != null) {
                    TransportConfiguration[] connector;
                    if (topologyMember.getBackup() != null) {
                        connector = new TransportConfiguration[]{topologyMember.getPrimary(), topologyMember.getBackup()};
                    } else {
                        connector = new TransportConfiguration[]{topologyMember.getPrimary()};
                    }
                    WildFlyActiveMQRecoveryRegistry.getInstance().nodeUp(config, topologyMember.getNodeId(), connector,
                            config.getUsername(), config.getPassword(), config.getProperties());
                }
            }
        }

        @Override
        public void nodeDown(long eventUID, String nodeID) {
            // I'm not putting any node down, since it may have previous transactions hanging, however at some point we may
            //change it have some sort of timeout for removal
        }

    }

    @Override
    public void connectionFailed(ActiveMQException exception, boolean failedOver) {
        if (exception.getType() == ActiveMQExceptionType.DISCONNECTED) {
            WildFlyActiveMQLogger.LOGGER.warn("being disconnected for server shutdown", exception);
        } else {
            WildFlyActiveMQLogger.LOGGER.warn("Notified of connection failure in xa discovery, we will retry on the next recovery",
                    exception);
        }
        internalStop();
        WildFlyActiveMQRecoveryRegistry.getInstance().failedDiscovery(this);
    }

    @Override
    public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
        connectionFailed(me, failedOver);
    }

    @Override
    public void beforeReconnect(ActiveMQException exception) {
    }

    /* (non-Javadoc)
    * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "RecoveryDiscovery [config=" + config + ", started=" + started + "]";
    }

    @Override
    public int hashCode() {
        return config.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || (!(o instanceof WildFlyRecoveryDiscovery))) {
            return false;
        }
        WildFlyRecoveryDiscovery discovery = (WildFlyRecoveryDiscovery) o;

        return config.equals(discovery.config);
    }

}
