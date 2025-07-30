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

import java.net.InetSocketAddress;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

public final class HttpLightyJettyWebServer extends AbstractLightyWebServer {

    private static final int HTTP_SERVER_IDLE_TIMEOUT = 30000;

    public HttpLightyJettyWebServer() {
        // automatically choose free port
        this(new InetSocketAddress("localhost", 0));
    }

    public HttpLightyJettyWebServer(final InetSocketAddress address) {
        this.httpPort = address.getPort();
        checkArgument(httpPort >= 0, "httpPort must be positive");
        checkArgument(httpPort < 65536, "httpPort must < 65536");

        this.server = new Server();
        server.setStopAtShutdown(true);

        this.http = new ServerConnector(server);
        http.setHost(address.getHostName());
        http.setPort(address.getPort());
        http.setIdleTimeout(HTTP_SERVER_IDLE_TIMEOUT);
        server.addConnector(http);

        this.contextHandlerCollection = new ContextHandlerCollection();
        server.setHandler(contextHandlerCollection);
    }

}
