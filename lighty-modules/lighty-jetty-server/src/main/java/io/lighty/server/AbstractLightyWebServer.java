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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import java.util.EnumSet;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLightyWebServer implements WebServer {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractLightyWebServer.class);
    protected static final int HTTP_SERVER_IDLE_TIMEOUT = 30000;

    private final int httpPort;
    private final ContextHandlerCollection contextHandlerCollection;
    protected final Server server;

    public AbstractLightyWebServer(final int httpPort) {
        this.httpPort = httpPort;
        checkArgument(httpPort >= 0, "httpPort must be positive");
        checkArgument(httpPort < 65536, "httpPort must < 65536");

        server = new Server();
        server.setStopAtShutdown(true);

        contextHandlerCollection = new ContextHandlerCollection();
        server.setHandler(contextHandlerCollection);
    }

    @Override
    public String getBaseURL() {
        if (httpPort == 0) {
            throw new IllegalStateException("must start() before getBaseURL()");
        }
        return "http://localhost:" + httpPort;
    }

    @PostConstruct
    public void start() throws Exception {
        server.start();
        LOG.info("Started Jetty 12-based HTTP web server on port {} ({}).", httpPort, hashCode());
    }

    @PreDestroy
    public void stop() throws Exception {
        LOG.info("Stopping Jetty-based web server...");
        server.stop();
        LOG.info("Stopped Jetty-based web server.");
    }

    @Override
    public synchronized Registration registerWebContext(final WebContext webContext) throws ServletException {
        // Create ServletContextHandler specifically for EE11 (Jakarta EE 11)
        ServletContextHandler handler = new ServletContextHandler();
        handler.setContextPath(webContext.contextPath());

        if (webContext.supportsSessions()) {
            handler.insertHandler(new org.eclipse.jetty.ee11.servlet.SessionHandler());
        }

        // 1. Context parameters
        webContext.contextParams().forEach(handler::setAttribute);

        // 2. Listeners
        webContext.listeners().forEach(handler::addEventListener);

        // 3. Filters
        webContext.filters().forEach(filter -> {
            FilterHolder filterHolder = new FilterHolder(filter.filter());
            filterHolder.setInitParameters(filter.initParams());
            filter.urlPatterns().forEach(
                urlPattern -> handler.addFilter(filterHolder, urlPattern, EnumSet.allOf(DispatcherType.class))
            );
        });

        // 4. Servlets
        webContext.servlets().forEach(servlet -> {
            ServletHolder servletHolder = new ServletHolder(servlet.name(), servlet.servlet());
            servletHolder.setInitParameters(servlet.initParams());
            servletHolder.setAsyncSupported(servlet.asyncSupported());
            servletHolder.setInitOrder(1);
            servlet.urlPatterns().forEach(urlPattern -> handler.addServlet(servletHolder, urlPattern));
        });

        contextHandlerCollection.addHandler(handler);
        restart(handler);

        return () -> close(handler);
    }

    private static void restart(final AbstractLifeCycle lifecycle) throws ServletException {
        try {
            lifecycle.start();
        } catch (Exception e) {
            throw new ServletException("Lifecycle start failed", e);
        }
    }

    private void close(final ServletContextHandler handler) {
        try {
            handler.stop();
        } catch (Exception e) {
            LOG.error("close() failed", e);
        } finally {
            handler.destroy();
        }
        contextHandlerCollection.removeHandler(handler);
    }

    public Server getServer() {
        return server;
    }
}