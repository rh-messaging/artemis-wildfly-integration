/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.activemq.artemis.wildfly.integration.tests;

import static javax.naming.Context.INITIAL_CONTEXT_FACTORY;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.service.extensions.ServiceUtils;
import org.jboss.activemq.artemis.wildfly.integration.fake.DummyTransactionManager;

import javax.naming.InitialContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class TransactionManagerLocatorTest {

    @Test
    public void testServiceUtilsReturnsWildFlyActiveMQXAResourceWrapperFactory() throws Exception {
        System.setProperty(INITIAL_CONTEXT_FACTORY, "org.jboss.activemq.artemis.wildfly.integration.fake.DummyInitialContext");
        InitialContext initialContext = new InitialContext();
        initialContext.unbind("java:/TransactionManager");
        initialContext.bind("java:/TransactionManager", new DummyTransactionManager());
        assertNotNull(ServiceUtils.getTransactionManager());
        assertTrue(ServiceUtils.getTransactionManager() instanceof DummyTransactionManager);
    }

    @AfterEach
    public void resetTransactionManager() {
        ServiceUtils.setTransactionManager(null);
        System.clearProperty(INITIAL_CONTEXT_FACTORY);
    }
}
