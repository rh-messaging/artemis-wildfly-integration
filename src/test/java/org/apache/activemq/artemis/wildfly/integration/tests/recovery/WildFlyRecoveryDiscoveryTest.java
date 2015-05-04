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

package org.apache.activemq.artemis.wildfly.integration.tests.recovery;

import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;
import org.apache.activemq.artemis.wildfly.integration.recovery.WildFlyRecoveryDiscovery;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mtaylor
 */

public class WildFlyRecoveryDiscoveryTest
{
   @Test
   public void testWildFlyRecoveryDiscoveryAsKey() throws Exception
   {
      Set<WildFlyRecoveryDiscovery> discoverySet = new HashSet<WildFlyRecoveryDiscovery>();
      String factClass = "org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory";
      TransportConfiguration transportConfig = new TransportConfiguration(factClass, null, "netty");
      XARecoveryConfig config = new XARecoveryConfig(false, new TransportConfiguration[]{transportConfig},
                                                     null, null);

      WildFlyRecoveryDiscovery discovery1 = new WildFlyRecoveryDiscovery(config);
      WildFlyRecoveryDiscovery discovery2 = new WildFlyRecoveryDiscovery(config);
      assertTrue(discoverySet.add(discovery1));
      assertFalse(discoverySet.add(discovery2));
      assertEquals("should have only one in the set", 1, discoverySet.size());
   }
}
