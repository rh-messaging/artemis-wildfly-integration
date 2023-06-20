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
package org.jboss.activemq.artemis.wildfly;

import javax.security.auth.Subject;
import org.apache.activemq.artemis.logs.BundleFactory;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 * 3/15/12
 *
 * Logger Code 13
 *
 * each message id must be 6 digits long starting with 13, the 3rd digit donates the level so
 *
 * INF0 1
 * WARN 2
 * DEBUG 3
 * ERROR 4
 * TRACE 5
 * FATAL 6
 *
 * so an INFO message would be 131000 to 131999
 */

@LogBundle(projectCode = "AMQ", regexID = "13[0-9]{4}")
public interface ActiveMQJBossLogger {

    /**
     * The jboss integration logger.
     */
    ActiveMQJBossLogger LOGGER = BundleFactory.newBundle(ActiveMQJBossLogger.class, ActiveMQJBossLogger.class.getPackage().getName());

    @LogMessage(id = 131001, value = "Security Context Setting Subject = {}", level = LogMessage.Level.INFO)
    void settingSecuritySubject(Subject subject);

    @LogMessage(id = 132001, value = "An error happened while setting the context", level = LogMessage.Level.WARN)
    void errorSettingSecurityContext(Throwable Throwable);
}
