package io.mats3.examples;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

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
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Endre Stølsvik 2023-03-27 18:41 - http://stolsvik.com/, endre@stolsvik.com
 */
public interface MatsExampleJettyServer {

    String CONTEXT_ATTRIBUTE_PORTNUMBER = "ServerPortNumber";

    MatsExampleJettyServer addMatsFactory(String appName);
    MatsExampleJettyServer addMatsFactory();

    MatsExampleJettyServer addMatsLocalInspect();

    MatsExampleJettyServer setRootHtlm(String html);

    WebAppContext getWebAppContext();

    Server getJettyServer();

    void start();

    // -------- IMPLEMENTATION -------------------------

    static MatsExampleJettyServer create(int port, Class<?> callingClass) {
        // Turn off LogBack's ServletContainerInitializer
        System.setProperty(CoreConstants.DISABLE_SERVLET_CONTAINER_INITIALIZER_KEY, "true");
        // Configure Logback
        MatsExampleKit.configureLogbackToConsole_Info();

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
            private static final Logger log = LoggerFactory.getLogger(MatsExampleJettyServer.class);

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
        return new MatsExampleJettyServerImpl(callingClass, server, webAppContext);
    }

    // ---------------------------------

    class MatsExampleJettyServerImpl implements MatsExampleJettyServer {
        private static final Logger log = MatsTestHelp.getClassLogger();

        private final Class<?> _callingClass;
        private final Server _server;
        private final WebAppContext _webAppContext;

        public MatsExampleJettyServerImpl(Class<?> callingClass, Server server, WebAppContext webAppContext) {
            _callingClass = callingClass;
            _server = server;
            _webAppContext = webAppContext;
        }

        private String _addMatsInit_AppName;
        private boolean _addMatsLocalInspect;
        private String _rootHtlm;


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
            if (_addMatsInit_AppName != null) {
                // The ServletContextListener creates the app MatsFactory and MatsFuturizer, for use by Servlets.
                _webAppContext.addEventListener(new ServletContextListener() {
                    private MatsFactory _matsFactory;
                    private MatsFuturizer _matsFuturizer;

                    @Override
                    public void contextInitialized(ServletContextEvent sce) {
                        // Create the MatsFactory and then MatsFuturizer
                        _matsFactory = MatsExampleKit.createMatsFactory(_addMatsInit_AppName);
                        _matsFuturizer = MatsFuturizer.createMatsFuturizer(_matsFactory);
                        // Put these in the ServletContext, so that the Servlets can get hold of it.
                        sce.getServletContext().setAttribute(MatsFactory.class.getName(), _matsFactory);
                        sce.getServletContext().setAttribute(MatsFuturizer.class.getName(), _matsFuturizer);
                    }

                    @Override
                    public void contextDestroyed(ServletContextEvent sce) {
                        // Clean up.
                        _matsFuturizer.close();
                        _matsFactory.stop(30_000);
                    }
                });
            }

            if (_addMatsLocalInspect) {
                // The ServletContextListener creates the LocalHtmlInspectForMatsFactory and puts it in ServletContext
                _webAppContext.addEventListener(new ServletContextListener() {
                    @Override
                    public void contextInitialized(ServletContextEvent sce) {
                        // Fetch MatsFactory from ServletContext
                        var matsFactory = (MatsFactory) sce.getServletContext()
                                .getAttribute(MatsFactory.class.getName());
                        // :: Sanity assert
                        if (matsFactory == null) {
                            throw new IllegalStateException("Missing MatsFactory in ServletContext. You may add one"
                                    + " using " + MatsExampleJettyServer.class.getSimpleName() + ".addMatsInit().");
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

                        out.println("<!DOCTYPE html>");
                        out.println("<html>");
                        out.println("  <body>");
                        out.println("    <style>");
                        localInspect.getStyleSheet(out); // Include just once, use the first.
                        out.println("    </style>");
                        out.println("    <script>");
                        localInspect.getJavaScript(out); // Include just once, use the first.
                        out.println("    </script>");
                        out.println(" <a href=\".\">Back to root</a><br><br>");
                        out.println("<h1>Embeddable Introspection GUI</h1>");
                        localInspect.createFactoryReport(out, true, true, true);

                        out.println("  </body>");
                        out.println("</html>");
                    }
                });
                _webAppContext.addServlet(servletHolder, "/localinspect");
            }

            if (_rootHtlm != null) {
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

            try {
                _server.start();
            }
            catch (Exception e) {
                throw new AssertionError("Could not start Jetty.", e);
            }
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

        @Override
        public MatsExampleJettyServer addMatsFactory(String appName) {
            _addMatsInit_AppName = appName;
            // .. for chaining
            return this;
        }

        @Override
        public MatsExampleJettyServer addMatsFactory() {
            return addMatsFactory(_callingClass.getSimpleName());
        }

        @Override
        public MatsExampleJettyServer addMatsLocalInspect() {
            _addMatsLocalInspect = true;
            // .. for chaining
            return this;
        }

        @Override
        public MatsExampleJettyServer setRootHtlm(String rootHtml) {
            _rootHtlm = rootHtml;
            // .. for chaining
            return this;
        }
    }
}
