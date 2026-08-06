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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.jboss.activemq.artemis.wildfly.integration.recovery.WildFlyActiveMQRecoveryRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class NetworkConfigurationUpdateTest {

    private static final String NETTY_CONNECTOR = "org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory";
    private static Method isUpdateRequired;

    @BeforeAll
    static void setUp() throws Exception {
        isUpdateRequired = WildFlyActiveMQRecoveryRegistry.class.getDeclaredMethod(
                "isNetworkConfigurationUpdateRequired",
                TransportConfiguration[].class, TransportConfiguration[].class);
        isUpdateRequired.setAccessible(true);
    }

    private boolean invoke(TransportConfiguration[] initial, TransportConfiguration[] network) throws Exception {
        return (boolean) isUpdateRequired.invoke(WildFlyActiveMQRecoveryRegistry.getInstance(), initial, network);
    }

    @Test
    public void testBothNull() throws Exception {
        assertFalse(invoke(null, null));
    }

    @Test
    public void testNetworkConfigNull() throws Exception {
        TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR, null, "netty");
        assertFalse(invoke(new TransportConfiguration[]{tc}, null));
    }

    @Test
    public void testInitialConfigNull() throws Exception {
        TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR, null, "netty");
        assertTrue(invoke(null, new TransportConfiguration[]{tc}));
    }

    @Test
    public void testSameConfiguration() throws Exception {
        TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR, null, "netty");
        assertFalse(invoke(new TransportConfiguration[]{tc}, new TransportConfiguration[]{tc}));
    }

    @Test
    public void testNetworkConfigLarger() throws Exception {
        TransportConfiguration tc1 = new TransportConfiguration(NETTY_CONNECTOR, null, "netty1");
        TransportConfiguration tc2 = new TransportConfiguration(NETTY_CONNECTOR, null, "netty2");
        assertTrue(invoke(new TransportConfiguration[]{tc1}, new TransportConfiguration[]{tc1, tc2}));
    }

    @Test
    public void testInitialConfigLarger() throws Exception {
        TransportConfiguration tc1 = new TransportConfiguration(NETTY_CONNECTOR, null, "netty1");
        TransportConfiguration tc2 = new TransportConfiguration(NETTY_CONNECTOR, null, "netty2");
        assertFalse(invoke(new TransportConfiguration[]{tc1, tc2}, new TransportConfiguration[]{tc1}));
    }

    @Test
    public void testNullElementInInitial() throws Exception {
        TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR, null, "netty");
        assertTrue(invoke(new TransportConfiguration[]{null}, new TransportConfiguration[]{tc}));
    }

    @Test
    public void testNullElementInNetwork() throws Exception {
        TransportConfiguration tc = new TransportConfiguration(NETTY_CONNECTOR, null, "netty");
        assertTrue(invoke(new TransportConfiguration[]{tc}, new TransportConfiguration[]{null}));
    }

    @Test
    public void testBothElementsNull() throws Exception {
        assertFalse(invoke(new TransportConfiguration[]{null}, new TransportConfiguration[]{null}));
    }
}
