//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//REPOS mavencentral,LocalMaven
//DEPS io.mats3.examples:mats-jbangkit:RC0-1.0.0

import static io.mats3.matsbrokermonitor.htmlgui.MatsBrokerMonitorHtmlGui.ACCESS_CONTROL_ALLOW_ALL;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.Collectors;

import javax.jms.ConnectionFactory;

import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerService;
import org.slf4j.Logger;

import io.mats3.MatsFactory;
import io.mats3.examples.MatsJbangJettyServer;
import io.mats3.examples.MatsJbangKit;
import io.mats3.impl.jms.JmsMatsFactory;
import io.mats3.matsbrokermonitor.activemq.ActiveMqMatsBrokerMonitor;
import io.mats3.matsbrokermonitor.api.MatsBrokerBrowseAndActions;
import io.mats3.matsbrokermonitor.api.MatsBrokerMonitor;
import io.mats3.matsbrokermonitor.htmlgui.MatsBrokerMonitorHtmlGui;
import io.mats3.matsbrokermonitor.jms.JmsMatsBrokerBrowseAndActions;
import io.mats3.test.MatsTestHelp;
import io.mats3.test.broker.MatsTestBroker;
import io.mats3.test.broker.MatsTestBroker.ActiveMq;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Starts an ActiveMQ instance on standard port 61616. "Mats3 optimized", but you can also use clean distro. Configured
 * features, ordered with most important first: Include Stats plugin, Individual DLQs, Prioritized messages, GC inactive
 * destinations, lesser prefetch, split memory, DLQ expired msgs.
 * <p>
 * Also starts a Jetty HTTP server instance, which provides access to the embeddable MatsBrokerMonitor, to inspect the
 * Mats3-relevants queues and DLQs, as well as ability to inspect messages, and reissue messages on DLQs.
 */
public class ActiveMqRun {

    private static final Logger log = MatsTestHelp.getClassLogger();

    public static void main(String[] args) {
        MatsJbangKit.configureLogbackToConsole_Info();

        // :: Create the Jetty instance running and displaying the MatsBrokerMonitor
        // Notice: It is the webapp that also brings up the ActiveMQ instance itself, to get good shutdown ordering.
        MatsJbangJettyServer.create(8000, ActiveMqRun.class)
                .setRootHtlm("""
                        <html><body>
                        <h1>ActiveMQ instance, with HTTP server.</h1>
                        <h3>Mats Broker Monitor</h3>
                        <a href="matsbrokermonitor">MatsBrokerMonitor</a> for monitoring queues and DLQs,
                         reissue messages<p>
                        </body></html>
                        """)
                // Start Jetty, running all SCLs and Servlets in this class.
                .start();
    }

    @WebListener
    public static class MatsBrokerMonitor_SCL implements ServletContextListener {

        private BrokerService _brokerService;
        private MatsFactory _matsFactory;
        private MatsBrokerMonitor _matsBrokerMonitor;
        private MatsBrokerBrowseAndActions _matsBrokerBrowseAndActions;

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            log.info("ContextInitialized - setting up MatsBrokerMonitor + infrastructure.");

            // :: Create the ActiveMQ broker "inside the webapp" to get nice shutdown order upon Ctrl-C/shutdown.
            BrokerService brokerService;
            // ?: Do we want persistent broker?
            if (System.getProperty("persistent") != null) {
                // -> Yes, persistent broker, so ask for KahaDB to be installed.
                brokerService = MatsTestBroker.newActiveMqBroker(ActiveMq.LOCALHOST, ActiveMq.PERSISTENT);
            }
            else {
                // -> No, not persistent broker.
                brokerService = MatsTestBroker.newActiveMqBroker(ActiveMq.LOCALHOST);
            }

            // :: Create the MatsBrokerMonitor
            // NOTE: This DOES NOT need to be done on the ActiveMQ instance, as all interaction is done over messaging.
            // The only requirement is that the ActiveMQ instance has the StatisticsPlugin installed.
            // The MatsBrokerMonitor service and GUI can be run on e.g. a common system-wide monitor solution.
            // The only reason we do it alongside the ActiveMQ process is that this effectively is our common monitor.

            // .. Create a ConnectionFactory to the ActiveMQ, going over TCP to the broker created above
            ConnectionFactory jmsConnectionFactory = MatsJbangKit.createActiveMqConnectionFactory();

            // .. Create MatsFactory (for broadcasting of the stats to other MatsFactories)
            JmsMatsFactory<String> matsFactory = MatsJbangKit
                    .createMatsFactory(jmsConnectionFactory, "ActiveMqRun");

            // .. Create the ActiveMQ MatsBrokerMonitor
            MatsBrokerMonitor matsBrokerMonitor = ActiveMqMatsBrokerMonitor
                    .create(jmsConnectionFactory, 15_000);
            // .. start it.
            matsBrokerMonitor.start();

            // .. Create the JMS MatsBrokerBrowseAndActions
            MatsBrokerBrowseAndActions matsBrokerBrowseAndActions = JmsMatsBrokerBrowseAndActions
                    .create(jmsConnectionFactory);
            // .. start it.
            matsBrokerBrowseAndActions.start();

            // .. Stitch this together for the MatsBrokerMonitorHtmlGui
            MatsBrokerMonitorHtmlGui matsBrokerMonitorHtmlGui = MatsBrokerMonitorHtmlGui
                    .create(matsBrokerMonitor, matsBrokerBrowseAndActions, null, matsFactory.getMatsSerializer());

            // .. put the MBM in ServletContext, so that the monitor servlet can find it
            sce.getServletContext().setAttribute(MatsBrokerMonitorHtmlGui.class.getName(), matsBrokerMonitorHtmlGui);

            // :: Store the closable refs for clean shutdown
            _brokerService = brokerService;
            _matsFactory = matsFactory;
            _matsBrokerMonitor = matsBrokerMonitor;
            _matsBrokerBrowseAndActions = matsBrokerBrowseAndActions;
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            // Clean up in good order, broker last (otherwise we'll get connectivity exceptions on takedown).
            _matsBrokerBrowseAndActions.close();
            _matsBrokerMonitor.close();
            _matsFactory.stop(30_000);
            try {
                _brokerService.stop();
            }
            catch (Exception e) {
                log.info("Got unexpected problem shutting down ActiveMQ broker", e);
            }
        }
    }

    @WebServlet("/matsbrokermonitor/*")
    public static class MatsBrokerMonitorServlet extends HttpServlet {

        @Override
        protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
            doJson(req, res);
        }

        @Override
        protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
            doJson(req, res);
        }

        protected void doJson(HttpServletRequest req, HttpServletResponse res) throws IOException {
            // :: MatsBrokerMonitorHtmlGUI instance
            MatsBrokerMonitorHtmlGui brokerMonitorHtmlGui = (MatsBrokerMonitorHtmlGui) req.getServletContext()
                    .getAttribute(MatsBrokerMonitorHtmlGui.class.getName());

            String body = req.getReader().lines().collect(Collectors.joining("\n"));
            res.setContentType("application/json; charset=utf-8");
            PrintWriter out = res.getWriter();
            brokerMonitorHtmlGui.json(out, req.getParameterMap(), body, ACCESS_CONTROL_ALLOW_ALL);
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
            MatsBrokerMonitorHtmlGui brokerMonitorHtmlGui = (MatsBrokerMonitorHtmlGui) req.getServletContext()
                    .getAttribute(MatsBrokerMonitorHtmlGui.class.getName());

            res.setContentType("text/html; charset=utf-8");

            PrintWriter out = res.getWriter();
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("  <body>");
            out.println("    <style>");
            brokerMonitorHtmlGui.outputStyleSheet(out); // Include just once, use the first.
            out.println("    </style>");
            out.println("    <script>");
            brokerMonitorHtmlGui.outputJavaScript(out); // Include just once, use the first.
            out.println("    </script>");
            out.println(" <a href=\".\">Back to root</a><br><br>");

            out.println("<h1>ActiveMQ - MatsBrokerMonitor HTML embedded GUI</h1>");
            brokerMonitorHtmlGui.html(out, req.getParameterMap(), ACCESS_CONTROL_ALLOW_ALL);
            out.println("  </body>");
            out.println("</html>");
        }
    }
}