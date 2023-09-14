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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.service.extensions.xa.recovery.ActiveMQRegistry;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;
import org.jboss.tm.XAResourceRecoveryRegistry;

/**
 * This class is a base class for the integration layer where
 * This class is used on integration points and this is just a bridge to the real registry at
 * {@link WildFlyActiveMQRecoveryRegistry}
 *
 * @author Clebert
 *
 *
 */
public class WildFlyActiveMQRegistry implements ActiveMQRegistry {

    private final AtomicBoolean started = new AtomicBoolean(false);

    private XAResourceRecoveryRegistry tmRegistry;

    public WildFlyActiveMQRegistry() {
    }

    public XAResourceRecoveryRegistry getTMRegistry() {
        return tmRegistry;
    }

    public void setTmRegistry(XAResourceRecoveryRegistry tmRegistry) {
        this.tmRegistry = tmRegistry;
    }

    @Override
    public void register(final XARecoveryConfig resourceConfig) {
        init();
        WildFlyActiveMQRecoveryRegistry.getInstance().register(resourceConfig);
    }

    @Override
    public void unRegister(final XARecoveryConfig resourceConfig) {
        init();
        WildFlyActiveMQRecoveryRegistry.getInstance().unRegister(resourceConfig);
    }

    @Override
    public void stop() {
        if (started.compareAndSet(true, false) && getTMRegistry() != null) {
            getTMRegistry().removeXAResourceRecovery(WildFlyActiveMQRecoveryRegistry.getInstance());
            WildFlyActiveMQRecoveryRegistry.getInstance().stop();
        }
    }

    @Override
    public void init() {
        if (started.compareAndSet(false, true) && getTMRegistry() != null) {
            getTMRegistry().addXAResourceRecovery(WildFlyActiveMQRecoveryRegistry.getInstance());
        }
    }

}
