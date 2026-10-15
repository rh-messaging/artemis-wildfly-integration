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
package org.jboss.activemq.artemis.wildfly.integration.tests.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.transaction.xa.XAResource;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;
import org.jboss.activemq.artemis.wildfly.integration.WildFlyActiveMQXAResourceWrapper;
import org.jboss.activemq.artemis.wildfly.integration.recovery.WildFlyActiveMQRecoveryRegistry;
import org.jboss.activemq.artemis.wildfly.integration.recovery.WildFlyRecoveryDiscovery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A connection registered by the resource adapter whose broker never advertises a topology
 * {@code nodeUp} (e.g. an in-vm or non-clustered connection) must still be offered a recovery
 * XAResource, otherwise its in-doubt branches stay unrecoverable
 * ({@code ARJUNA016037 ... resource:null ... eis_name=java:/JmsXA}).
 */
public class RecoveryFallbackTest {

    private static final String NETTY_CONNECTOR = "org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory";

    @BeforeEach
    @AfterEach
    public void clearRegistryState() throws Exception {
        field("configSet").clear();
        field("recoveries").clear();
        field("fallbackRecoveries").clear();
    }

    @Test
    public void getXAResourcesFallsBackToRegisteredConnectionWhenNoTopologyNodeUp() throws Exception {
        XARecoveryConfig config = inVmLikeConfig("java:/JmsXA");
        // Simulate RA registration where the broker never delivers a topology nodeUp:
        // configSet has the connection, but recoveries stays empty.
        field("configSet").put(config, new WildFlyRecoveryDiscovery(config));

        XAResource[] resources = WildFlyActiveMQRecoveryRegistry.getInstance().getXAResources();

        assertEquals(1, resources.length, "fallback must expose one recovery resource for the registered connection");
        assertTrue(resources[0] instanceof WildFlyActiveMQXAResourceWrapper, "must be the messaging recovery wrapper");
        assertTrue(resources[0].toString().contains("java:/JmsXA"),
                "the fallback wrapper must carry the connection's JNDI name so Narayana can name-match the record");
    }

    @Test
    public void fallbackWrapperIsCachedAcrossScans() throws Exception {
        XARecoveryConfig config = inVmLikeConfig("java:/JmsXA");
        field("configSet").put(config, new WildFlyRecoveryDiscovery(config));

        XAResource first = WildFlyActiveMQRecoveryRegistry.getInstance().getXAResources()[0];
        XAResource second = WildFlyActiveMQRecoveryRegistry.getInstance().getXAResources()[0];

        assertSame(first, second, "fallback wrapper must be cached, not rebuilt (and reconnected) on every recovery scan");
    }

    private static XARecoveryConfig inVmLikeConfig(String jndiName) {
        Map<String, String> properties = new HashMap<>();
        properties.put(XARecoveryConfig.JNDI_NAME_PROPERTY_KEY, jndiName);
        return new XARecoveryConfig(false,
                new TransportConfiguration[]{new TransportConfiguration(NETTY_CONNECTOR, null, "netty")},
                null, null, properties);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<Object, Object> field(String name) throws Exception {
        Field f = WildFlyActiveMQRecoveryRegistry.class.getDeclaredField(name);
        f.setAccessible(true);
        return (ConcurrentHashMap<Object, Object>) f.get(WildFlyActiveMQRecoveryRegistry.getInstance());
    }
}
