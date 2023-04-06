package io.mats3.examples;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.component.LifeCycle.Listener;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.core.CoreConstants;
import io.mats3.MatsFactory;
import io.mats3.api.intercept.MatsInterceptable;
import io.mats3.localinspect.LocalHtmlInspectForMatsFactory;
import io.mats3.localinspect.LocalStatsMatsInterceptor;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sibling to {@link MatsExampleKit} for easily setting up a Jetty server, optionally including a {@link MatsFactory}
 * and {@link MatsFuturizer} inside the ServletContext for use by Servlets.
 *
 * @author Endre Stølsvik 2023-03-27 18:41 - http://stolsvik.com/, endre@stolsvik.com
 */
public interface MatsExampleJettyServer {

    String CONTEXT_ATTRIBUTE_PORTNUMBER = "ServerPortNumber";

    /**
     * Adds a {@link ServletContextListener} which adds {@link MatsFactory} to the ServletContext, so that any added
     * Servlets may get hold of it.
     *
     * @param appName
     *         the appName of the constructed {@link MatsFactory}.
     * @return this {@link MatsExampleJettyServer} for chaining.
     */
    MatsExampleJettyServer addMatsFactory(String appName);

    /**
     * Convenience of {@link #addMatsFactory(String)}, where the appName is gotten from the calling classname (as gotten
     * from a stacktrace).
     *
     * @return this {@link MatsExampleJettyServer} for chaining.
     */
    MatsExampleJettyServer addMatsFactory();

    /**
     * Adds a {@link ServletContextListener} which adds {@link MatsFuturizer} to the ServletContext, so that any added
     * Servlets may get hold of it. Needs a {@link MatsFactory} in the ServletContext, as provided by
     * {@link #addMatsFactory(String)}.
     *
     * @return this {@link MatsExampleJettyServer} for chaining.
     */
    MatsExampleJettyServer addMatsFuturizer();

    /**
     * Adds a {@link ServletContextListener} and {@link HttpServlet} for providing the
     * {@link LocalHtmlInspectForMatsFactory} local monitoring and inspection utility - also installs the
     * {@link LocalStatsMatsInterceptor} so that the local inspect can show some rudimentary stats for the Initiators,
     * Endpoints and Stages in the {@link MatsFactory}.
     *
     * @return this {@link MatsExampleJettyServer} for chaining.
     */
    MatsExampleJettyServer addMatsLocalInspect();

    /**
     * Convenience for {@link #addMatsLocalInspect()} which adds a small HTML to root using
     * {@link #setRootHtlm(String)}.
     *
     * @return this {@link MatsExampleJettyServer} for chaining.
     */
    MatsExampleJettyServer addMatsLocalInspect_WithRootHtml();

    /**
     * Provides a way to e.g. easily add Mats3 Endpoints using the ServletContext MatsFactory.
     *
     * @param matsFactoryConsumer
     *         the Consumer which will be provided the ServletContext MatsFactory to work with.
     * @return this {@link MatsExampleJettyServer} for chaining.
     */
    MatsExampleJettyServer setupUsingMatsFactory(Consumer<MatsFactory> matsFactoryConsumer);

    /**
     * Provides a simple way to get some HTML on the "/" (root) of the Servlet Container; Installs a Servlet with path
     * spec "", i.e. root, which outputs the provided HTML, for e.g. making a small menu.
     *
     * @param html
     *         the HTML to spit out on "/" of the Servlet Container.
     * @return this {@link MatsExampleJettyServer} for chaining.
     */
    MatsExampleJettyServer setRootHtlm(String html);

    /**
     * @return the Jetty {@link WebAppContext} if you want to add more to it.
     */
    WebAppContext getWebAppContext();

    /**
     * @return the Jetty {@link Server} instance.
     */
    Server getJettyServer();

    /**
     * Starts the Jetty Server Servlet Container, adding the features specified with the builder methods above.
     */
    void start();

    // -------- IMPLEMENTATION -------------------------

    /**
     * Conveniece for {@link #create(int, Class)}, where the calling class is deduced by way of the thread stack trace.
     *
     * @param desiredPort
     *         the desired port - uses this, or a higher if this is not available.
     * @return the constructed {@link MatsExampleJettyServer}, which must be {@link #start() started} afterwards.
     */
    static MatsExampleJettyServer create(int desiredPort) {
        // Find caller class
        String callerclassname = MatsExampleKit.getCallingClassNameAndMethod()[0];
        Class<?> callingClass;
        try {
            callingClass = Class.forName(callerclassname);
        }
        catch (ClassNotFoundException e) {
            throw new AssertionError("Didn't find caller class [" + callerclassname + "].", e);
        }
        return create(desiredPort, callingClass);
    }

    /**
     * Creates a {@link MatsExampleJettyServer} for the desired port (or a higher port if this is not available). You
     * can add features using the configuration methods before invoking {@link #start()}. The calling class is added as
     * a WEB-INF resource, so that it will be scanned for annotations.
     *
     * @param desiredPort
     *         the desired port - uses this, or a higher if this is not available.
     * @param callingClass
     *         which class should be added as WEB-INF resource, so that it will be scanned for annotations.
     * @return the constructed {@link MatsExampleJettyServer}, which must be {@link #start() started} afterwards.
     */
    static MatsExampleJettyServer create(int desiredPort, Class<?> callingClass) {
        // Turn off LogBack's ServletContainerInitializer
        System.setProperty(CoreConstants.DISABLE_SERVLET_CONTAINER_INITIALIZER_KEY, "true");
        // Configure Logback
        MatsExampleKit.configureLogbackToConsole_Info();

        final Logger log = LoggerFactory.getLogger(MatsExampleJettyServer.class);

        int port = MatsExampleKit.findAvailablePortAtOrUpwardsOf(desiredPort, 10);
        if (port != desiredPort) {
            log.warn("NOTE: For JettyServer: Had to increase the port from [" + desiredPort + "] to [" + port + "].");
        }

        WebAppContext webAppContext = new WebAppContext();

        // Store the port number this server shall run under in the ServletContext.
        webAppContext.getServletContext().setAttribute(CONTEXT_ATTRIBUTE_PORTNUMBER, port);

        webAppContext.setContextPath("/");
        webAppContext.setParentLoaderPriority(true);

        webAppContext.setBaseResource(Resource.newClassPathResource("webapp"));

        // If any problems starting context, then let exception through so that we can exit.
        webAppContext.setThrowUnavailableOnStartupException(true);

        // :: Get Jetty to Scan project classes too: https://stackoverflow.com/a/26220672/39334
        List<Resource> resources = new ArrayList<>();
        // Find location for this class
        resources.add(Resource.newResource(MatsExampleKit.class.getProtectionDomain().getCodeSource().getLocation()));
        // ?: Did we get a calling class?
        if (callingClass != null) {
            // -> Yes, so add this one too.
            resources.add(Resource.newResource(callingClass.getProtectionDomain().getCodeSource().getLocation()));
        }
        // .. Set these locations to be scanned.
        webAppContext.getMetaData().setWebInfClassesResources(resources);

        // :: Create the actual Jetty Server
        Server server = new Server(port);

        // Wrap WebAppContext in a StatisticsHandler (to enable graceful shutdown), put in the WebApp Context
        StatisticsHandler stats = new StatisticsHandler();
        stats.setHandler(webAppContext);

        // Set the stats-wrapped WebAppContext on the Server
        server.setHandler(stats);

        // Add a Jetty Lifecycle Listener to cleanly shut down stuff
        server.addEventListener(new Listener() {
            @Override
            public void lifeCycleStarted(LifeCycle event) {
                log.info("######### Started server on port " + port);
            }

            @Override
            public void lifeCycleStopping(LifeCycle event) {
                log.info("===== STOP! ===========================================");
            }
        });

        // :: Graceful shutdown, and stop at shutdown
        server.setStopTimeout(5000);
        server.setStopAtShutdown(true);
        return new MatsExampleJettyServerImpl(callingClass, port, server, webAppContext);
    }

    // ---------------------------------

    class MatsExampleJettyServerImpl implements MatsExampleJettyServer {
        private static final Logger log = MatsTestHelp.getClassLogger();

        private final Class<?> _callingClass;
        private final int _serverPort;
        private final Server _server;
        private final WebAppContext _webAppContext;

        public MatsExampleJettyServerImpl(Class<?> callingClass, int serverPort, Server server,
                WebAppContext webAppContext) {
            _callingClass = callingClass;
            _serverPort = serverPort;
            _server = server;
            _webAppContext = webAppContext;
        }

        private String _addMatsFactory_AppName;

        private boolean _addMatsFuturizer;
        private boolean _addMatsLocalInspect;

        private Consumer<MatsFactory> _matsFactoryConsumer;

        private String _rootHtlm;

        @Override
        public MatsExampleJettyServer addMatsFactory(String appName) {
            _addMatsFactory_AppName = appName;
            // .. for chaining
            return this;
        }

        @Override
        public MatsExampleJettyServer addMatsFactory() {
            return addMatsFactory(_callingClass.getSimpleName());
        }

        @Override
        public MatsExampleJettyServer addMatsFuturizer() {
            _addMatsFuturizer = true;
            // .. for chaining
            return this;
        }

        @Override
        public MatsExampleJettyServer setupUsingMatsFactory(Consumer<MatsFactory> matsFactoryConsumer) {
            _matsFactoryConsumer = matsFactoryConsumer;
            // .. for chaining
            return this;
        }

        @Override
        public MatsExampleJettyServer addMatsLocalInspect() {
            _addMatsLocalInspect = true;
            // .. for chaining
            return this;
        }

        @Override
        public MatsExampleJettyServer addMatsLocalInspect_WithRootHtml() {
            addMatsLocalInspect();
            setRootHtlm("""
                    <html><body>
                    <h1>Service <i>'""" + _callingClass.getName() + """
                    '</i></h1>
                    <h3>LocalHtmlInspectForMatsFactory</h3>
                    <a href="localinspect">Monitoring/introspection GUI for the MatsFactory.</a>
                    </body></html>
                    """);
            // .. for chaining
            return this;
        }

        @Override
        public MatsExampleJettyServer setRootHtlm(String rootHtml) {
            _rootHtlm = rootHtml;
            // .. for chaining
            return this;
        }


        @Override
        public WebAppContext getWebAppContext() {
            return _webAppContext;
        }

        @Override
        public Server getJettyServer() {
            return _server;
        }

        @Override
        public void start() {
            if (_addMatsFactory_AppName != null) {
                includeMatsFactoryScl();
            }

            if (_addMatsFuturizer) {
                includeMatsFuturizerScl();
            }

            if (_matsFactoryConsumer != null) {
                setupUsingMatsFactoryScl();
            }

            if (_addMatsLocalInspect) {
                // The ServletContextListener creates the LocalHtmlInspectForMatsFactory and puts it in ServletContext
                includeMatsLocalInspectSclAndServlet();
            }

            if (_rootHtlm != null) {
                includeRootServletWithHtml();
            }

            try {
                _server.start();
            }
            catch (Exception e) {
                throw new AssertionError("Could not start Jetty.", e);
            }

            // Make a cute little line pointing out the port and URL for the server
            new Thread(() -> {
                try {
                    Thread.sleep(800);
                }
                catch (InterruptedException e) {
                    /* ignore */
                }
                System.out.println("\n### Jetty HTTP Server started for [" + _callingClass.getName()
                        + "] ==> http://localhost:" + _serverPort + "/");
            }).start();
        }

        private void includeRootServletWithHtml() {
            ServletHolder servletHolder = new ServletHolder(new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest req,
                        HttpServletResponse resp) throws IOException {
                    resp.setContentType("text/html; charset=UTF-8");
                    PrintWriter out = resp.getWriter();
                    out.println(_rootHtlm);
                }
            });
            _webAppContext.addServlet(servletHolder, "");
        }

        private void includeMatsFactoryScl() {
            // The ServletContextListener creates the app MatsFactory and MatsFuturizer, for use by Servlets.
            _webAppContext.addEventListener(new ServletContextListener() {
                private MatsFactory _matsFactory;

                @Override
                public void contextInitialized(ServletContextEvent sce) {
                    // Create the MatsFactory and then MatsFuturizer
                    _matsFactory = MatsExampleKit.createMatsFactory(_addMatsFactory_AppName);
                    // Put these in the ServletContext, so that the Servlets can get hold of it.
                    sce.getServletContext().setAttribute(MatsFactory.class.getName(), _matsFactory);
                }

                @Override
                public void contextDestroyed(ServletContextEvent sce) {
                    // Clean up.
                    _matsFactory.stop(30_000);
                }
            });
        }

        private void includeMatsFuturizerScl() {
            // The ServletContextListener creates the app MatsFactory and MatsFuturizer, for use by Servlets.
            _webAppContext.addEventListener(new ServletContextListener() {
                private MatsFuturizer _matsFuturizer;

                @Override
                public void contextInitialized(ServletContextEvent sce) {
                    // Create the MatsFactory and then MatsFuturizer
                    // Fetch MatsFactory from ServletContext
                    var matsFactory = (MatsFactory) sce.getServletContext()
                            .getAttribute(MatsFactory.class.getName());
                    // :: Sanity assert
                    if (matsFactory == null) {
                        throw new IllegalStateException("Missing MatsFactory in ServletContext. You may add one"
                                + " using '" + MatsExampleJettyServer.class.getSimpleName() + ".addMatsFactory()'.");
                    }
                    _matsFuturizer = MatsFuturizer.createMatsFuturizer(matsFactory);
                    // Put these in the ServletContext, so that the Servlets can get hold of it.
                    sce.getServletContext().setAttribute(MatsFuturizer.class.getName(), _matsFuturizer);
                }

                @Override
                public void contextDestroyed(ServletContextEvent sce) {
                    // Clean up.
                    _matsFuturizer.close();
                }
            });
        }

        private void setupUsingMatsFactoryScl() {
            // The ServletContextListener creates the app MatsFactory and MatsFuturizer, for use by Servlets.
            _webAppContext.addEventListener(new ServletContextListener() {
                @Override
                public void contextInitialized(ServletContextEvent sce) {
                    // Create the MatsFactory and then MatsFuturizer
                    // Fetch MatsFactory from ServletContext
                    var matsFactory = (MatsFactory) sce.getServletContext()
                            .getAttribute(MatsFactory.class.getName());
                    // :: Sanity assert
                    if (matsFactory == null) {
                        throw new IllegalStateException("Missing MatsFactory in ServletContext. You may add one"
                                + " using '" + MatsExampleJettyServer.class.getSimpleName() + ".addMatsFactory()'.");
                    }

                    _matsFactoryConsumer.accept(matsFactory);
                }
            });
        }

        private void includeMatsLocalInspectSclAndServlet() {
            _webAppContext.addEventListener(new ServletContextListener() {
                @Override
                public void contextInitialized(ServletContextEvent sce) {
                    // Fetch MatsFactory from ServletContext
                    var matsFactory = (MatsFactory) sce.getServletContext()
                            .getAttribute(MatsFactory.class.getName());
                    // :: Sanity assert
                    if (matsFactory == null) {
                        throw new IllegalStateException("Missing MatsFactory in ServletContext. You may add one"
                                + " using '" + MatsExampleJettyServer.class.getSimpleName() + ".addMatsFactory()'.");
                    }

                    LocalStatsMatsInterceptor.install((MatsInterceptable) matsFactory);

                    LocalHtmlInspectForMatsFactory li = LocalHtmlInspectForMatsFactory.create(matsFactory);
                    sce.getServletContext().setAttribute(LocalHtmlInspectForMatsFactory.class.getName(), li);
                }
            });

            // Add Servlet that serves the LocalInspect.
            ServletHolder servletHolder = new ServletHolder(new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest req,
                        HttpServletResponse resp) throws IOException {
                    resp.setContentType("text/html; charset=UTF-8");

                    var localInspect = (LocalHtmlInspectForMatsFactory) req.getServletContext()
                            .getAttribute(LocalHtmlInspectForMatsFactory.class.getName());

                    PrintWriter out = resp.getWriter();

                    out.println("""
                            <!DOCTYPE html>
                            <html><body>
                              <style>
                            """);
                    localInspect.getStyleSheet(out);
                    out.println("""
                              </style>
                              <script>
                            """);
                    localInspect.getJavaScript(out);
                    out.println("""
                              </script>
                              <a href='.'>Back to root</a>
                              <h1>Service <i>'\
                            """ + _callingClass.getName()
                            + "'</i> embeddable Introspection GUI</h1>");
                    localInspect.createFactoryReport(out, true, true, true);

                    out.println("</body></html>");
                }
            });
            _webAppContext.addServlet(servletHolder, "/localinspect");
        }

        /**
         * Servlet to shut down this JVM (<code>System.exit(0)</code>).
         */
        @WebServlet("/shutdown")
        public static class ShutdownServlet extends HttpServlet {
            private static final Logger log = MatsTestHelp.getClassLogger();

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                log.info("!! Got call on /shutdown - shutting down, System.exit(0);");
                resp.getWriter().println("Shutting down via System.exit(0);");

                // Shut down the process
                ForkJoinPool.commonPool().submit(() -> {
                    try {
                        Thread.sleep(250);
                    }
                    catch (InterruptedException e) {
                        /* no-op */
                    }
                    System.exit(0);
                });
            }
        }
    }

    /**
     * Adapt the Servlet {@link AsyncListener} to a bit more modern-looking {@link FunctionalInterface}.
     *
     * @author Endre Stølsvik 2023-03-30 22:48 - http://stolsvik.com/, endre@stolsvik.com
     */
    @FunctionalInterface
    interface FunctionalAsyncListener {

        void event(AsyncEvent asyncEvent) throws IOException;

        static AsyncListener onStartAsync(FunctionalAsyncListener startAsyncListener) {
            return new AsyncListener() {
                @Override
                public void onStartAsync(AsyncEvent event) throws IOException {
                    startAsyncListener.event(event);
                }

                @Override
                public void onComplete(AsyncEvent event) {
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                }

                @Override
                public void onError(AsyncEvent event) {
                }
            };
        }

        static AsyncListener onComplete(FunctionalAsyncListener timeoutListener) {
            return new AsyncListener() {
                @Override
                public void onStartAsync(AsyncEvent event) {
                }

                @Override
                public void onComplete(AsyncEvent event) throws IOException {
                    timeoutListener.event(event);
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                }

                @Override
                public void onError(AsyncEvent event) {
                }
            };
        }

        static AsyncListener onTimeout(FunctionalAsyncListener timeoutListener) {
            return new AsyncListener() {
                @Override
                public void onStartAsync(AsyncEvent event) {
                }

                @Override
                public void onComplete(AsyncEvent event) {
                }

                @Override
                public void onTimeout(AsyncEvent event) throws IOException {
                    timeoutListener.event(event);
                }

                @Override
                public void onError(AsyncEvent event) {
                }
            };
        }

        static AsyncListener onError(FunctionalAsyncListener timeoutListener) {
            return new AsyncListener() {
                @Override
                public void onStartAsync(AsyncEvent event) {
                }

                @Override
                public void onComplete(AsyncEvent event) {
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                }

                @Override
                public void onError(AsyncEvent event) throws IOException {
                    timeoutListener.event(event);
                }
            };
        }
    }
}
