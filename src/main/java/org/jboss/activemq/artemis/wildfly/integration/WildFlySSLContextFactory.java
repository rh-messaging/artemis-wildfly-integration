/*
 * Copyright 2020 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.activemq.artemis.wildfly.integration;

import static org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextFactory.log;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.remoting.impl.ssl.DefaultSSLContextFactory;
import org.apache.activemq.artemis.utils.ConfigurationHelper;

/**
 *
 * @author Emmanuel Hugonnet (c) 2019 Red Hat, Inc.
 */
public class WildFlySSLContextFactory extends DefaultSSLContextFactory {

    private static final Map<String, SSLContext> SSL_CONTEXTS = Collections.synchronizedMap(new HashMap<>());

    public static void registerElytronSSLContext(String name, SSLContext context) {
        SSL_CONTEXTS.put(name, context);
        log.debugf("Injecting Elytron SSLContext %s", name);
    }

    public static void unregisterElytronSSLContext(String name) {
        SSL_CONTEXTS.remove(name);
    }

    @Override
    public void clearSSLContexts() {
        SSL_CONTEXTS.clear();
    }

    @Override
    public SSLContext getSSLContext(Map<String, Object> configuration,
            String keystoreProvider, String keystorePath, String keystorePassword,
            String truststoreProvider, String truststorePath, String truststorePassword,
            String crlPath, String trustManagerFactoryPlugin, boolean trustAll) throws Exception {
        boolean useDefaultSslContext = ConfigurationHelper.getBooleanProperty(TransportConstants.USE_DEFAULT_SSL_CONTEXT_PROP_NAME, TransportConstants.DEFAULT_USE_DEFAULT_SSL_CONTEXT, configuration);

        String sslContextName = ConfigurationHelper.getStringProperty(TransportConstants.SSL_CONTEXT_PROP_NAME, ConfigurationHelper.getStringProperty(TransportConstants.KEYSTORE_PATH_PROP_NAME, TransportConstants.DEFAULT_KEYSTORE_PATH, configuration), configuration);
        if (useDefaultSslContext) {
            return SSLContext.getDefault();
        }
        if (!SSL_CONTEXTS.containsKey(sslContextName)) {
            SSL_CONTEXTS.put(sslContextName, super.createSSLContext(configuration, keystoreProvider, keystorePath,
                    keystorePassword, truststoreProvider, truststorePath, truststorePassword, crlPath,
                    trustManagerFactoryPlugin, trustAll));
        }
        return SSL_CONTEXTS.get(sslContextName);
    }

    @Override
    public int getPriority() {
        return 20;
    }

}
