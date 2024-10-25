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
package org.jboss.activemq.artemis.wildfly.integration;

import javax.transaction.xa.XAResource;
import java.util.Map;

import org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapperImpl;
import org.apache.activemq.artemis.service.extensions.xa.recovery.ActiveMQXAResourceWrapper;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;

/**
 * @author <a href="mailto:mtaylor@redhat.com">Martyn Taylor</a>
 */
public class WildFlyActiveMQXAResourceWrapper extends ActiveMQXAResourceWrapperImpl implements org.jboss.jca.core.spi.transaction.xa.XAResourceWrapper, org.jboss.tm.XAResourceWrapper {
    private boolean obsolete = false;
    private XARecoveryConfig config;

    public WildFlyActiveMQXAResourceWrapper(XAResource xaResource, Map<String, Object> properties) {
        super(xaResource, properties);
    }

    public void updateRecoveryConfig(XARecoveryConfig config) {
        this.obsolete = true;
        this.config = config;
    }

    public void update() {
        this.obsolete = false;
        ((ActiveMQXAResourceWrapper) getResource()).updateRecoveryConfig(config);
    }

    public boolean isObsolete() {
        return obsolete;
    }

    @Override
    public String toString() {
        return "WildFlyActiveMQXAResourceWrapper{" + "resource=" + getResource() + ", productName=" + getProductName() + ", productVersion=" + getProductVersion() + ", jndiNameNodeId=" + getJndiName() +'}';
    }
}
