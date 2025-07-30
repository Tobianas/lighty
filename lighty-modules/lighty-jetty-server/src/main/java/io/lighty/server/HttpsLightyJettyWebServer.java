/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.server;

import static com.google.common.base.Preconditions.checkArgument;

import io.lighty.server.config.SecurityConfig;
import java.net.InetSocketAddress;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

public final class HttpsLightyJettyWebServer extends AbstractLightyWebServer {

    private static final int HTTP_SERVER_IDLE_TIMEOUT = 30000;

    public HttpsLightyJettyWebServer(final SecurityConfig securityConfig) {
        // automatically choose free port
        this(new InetSocketAddress("localhost", 0), securityConfig);
    }

    public HttpsLightyJettyWebServer(final InetSocketAddress address, final SecurityConfig securityConfig) {
        final SslConnectionFactory ssl = securityConfig.getSslConnectionFactory(HttpVersion.HTTP_1_1.asString());
        this.httpPort = address.getPort();
        checkArgument(httpPort >= 0, "httpPort must be positive");
        checkArgument(httpPort < 65536, "httpPort must < 65536");

        this.server = new Server();
        this.server.setStopAtShutdown(true);

        this.http = new ServerConnector(server);
        http.setHost(address.getHostName());
        http.setPort(address.getPort());
        http.setIdleTimeout(HTTP_SERVER_IDLE_TIMEOUT);
        server.addConnector(http);

        this.contextHandlerCollection = new ContextHandlerCollection();
        server.setHandler(contextHandlerCollection);
        // Add only the HTTPS connector
        final ServerConnector sslConnector = new ServerConnector(server,
            ssl, httpConfiguration(address));
        sslConnector.setPort(address.getPort());

        server.addConnector(sslConnector);
    }

    private HttpConnectionFactory httpConfiguration(final InetSocketAddress inetSocketAddress) {
        final HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecurePort(inetSocketAddress.getPort());
        httpConfig.setSendXPoweredBy(true);

        final HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());
        return new HttpConnectionFactory(httpsConfig);
    }

}
