package io.mats3.examples;

import java.io.IOException;
import java.net.ServerSocket;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.broker.region.Queue;
import org.apache.activemq.store.kahadb.MessageDatabase;
import org.apache.activemq.transport.AbstractInactivityMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.util.StatusPrinter;
import io.mats3.impl.jms.JmsMatsFactory;
import io.mats3.impl.jms.JmsMatsJmsSessionHandler_Pooling;
import io.mats3.serial.json.MatsSerializerJson;
import io.mats3.util.RandomString;

/**
 * This class, along with {@link MatsExampleJettyServer}, offers a set of tools for easy setup of a Mats3 Endpoint or
 * client, with an optional HTTP server (Jetty). The aim is to minimize infrastructure and boilerplate code, allowing
 * you to focus on exploring Mats3's features without worrying about the setup process.
 *
 * @author Endre Stølsvik 2023-03-21 22:52 - http://stolsvik.com/, endre@stolsvik.com
 */
public class MatsExampleKit {
    public static ConnectionFactory createActiveMqConnectionFactory() {
        // :: Make ActiveMq JMS ConnectionFactory, towards localhost (which is default, using failover protocol)
        ActiveMQConnectionFactory jmsConnectionFactory = new ActiveMQConnectionFactory();

        // :: We won't be needing Topic Advisories (we don't use temp queues/topics), so don't subscribe to them.
        jmsConnectionFactory.setWatchTopicAdvisories(false);

        // :: RedeliveryPolicy
        RedeliveryPolicy redeliveryPolicy = jmsConnectionFactory.getRedeliveryPolicy();
        redeliveryPolicy.setInitialRedeliveryDelay(500);
        redeliveryPolicy.setRedeliveryDelay(2000); // This is not in use when using exp. backoff and initial != 0
        redeliveryPolicy.setUseExponentialBackOff(true);
        redeliveryPolicy.setBackOffMultiplier(2);
        redeliveryPolicy.setUseCollisionAvoidance(true);
        redeliveryPolicy.setCollisionAvoidancePercent((short) 15);

        // NOTE! Only need 1 redelivery for testing, totally ignoring the above. Use 6-10 for production.
        redeliveryPolicy.setMaximumRedeliveries(1);

        // :: We don't need in-order, so just deliver other messages while waiting for redelivery.
        // NOTE: This was buggy until 5.17.3: https://issues.apache.org/jira/browse/AMQ-8617
        jmsConnectionFactory.setNonBlockingRedelivery(true);

        return jmsConnectionFactory;
    }

    public static JmsMatsFactory<String> createMatsFactory() {
        MatsExampleKit.configureLogbackToConsole_Info();
        return createMatsFactory(getCallingClassSimpleName());
    }

    public static JmsMatsFactory<String> createMatsFactory(String appName) {
        MatsExampleKit.configureLogbackToConsole_Info();
        return createMatsFactory(createActiveMqConnectionFactory(), appName);
    }

    public static JmsMatsFactory<String> createMatsFactory(ConnectionFactory jmsConnectionFactory) {
        MatsExampleKit.configureLogbackToConsole_Info();
        return createMatsFactory(jmsConnectionFactory, getCallingClassSimpleName());
    }

    public static JmsMatsFactory<String> createMatsFactory(ConnectionFactory jmsConnectionFactory, String appName) {
        MatsExampleKit.configureLogbackToConsole_Info();

        // :: Make the JMS-based MatsFactory, providing the JMS ConnectionFactory
        JmsMatsFactory<String> matsFactory = JmsMatsFactory.createMatsFactory_JmsOnlyTransactions(appName, "#examples#",
                JmsMatsJmsSessionHandler_Pooling.create(jmsConnectionFactory),
                MatsSerializerJson.create());

        // .. turn down the concurrency from default cpus * 2, as that is pretty heavy on an e.g. 8-core dev machine.
        matsFactory.getFactoryConfig().setConcurrency(2);

        // .. set a unique nodename emulating multiple hosts on a single machine, so if we use futurizers, they won't
        // step on each other's toes
        String origNodename = matsFactory.getFactoryConfig().getNodename();
        matsFactory.getFactoryConfig().setNodename(origNodename + "_" + RandomString.randomString(6));

        // :: Add a shutdownhook to take it down in case of e.g. Ctrl-C - if it has not been done by the code.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // ?: Still running?
            if (matsFactory.getFactoryConfig().isRunning()) {
                // -> Yes, it has running components, so shut it down.
                try {
                    matsFactory.stop(10_000);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }));
        return matsFactory;
    }

    public static void configureLogbackToConsole_Debug() {
        configureLogbackToConsole(Level.DEBUG);
        configureLogRemovePeriodicDebug();
    }

    public static void configureLogbackToConsole_Info() {
        configureLogbackToConsole(Level.INFO);
    }

    public static void configureLogbackToConsole_Warn() {
        configureLogbackToConsole(Level.WARN);
    }

    private static volatile boolean __logbackAlreadyConfigured;

    public static void configureLogbackToConsole(Level rootLevel) {
        if (__logbackAlreadyConfigured) {
            return;
        }
        __logbackAlreadyConfigured = true;

        // :: Support override with -D system property, e.g. '-Dwarn'.

        // REMEMBER that you then must invoke the jbang files as such: 'jbang -Dwarn <file>'
        // (you cannot provide a system parameter after the invoked file!)

        if (System.getProperty("trace") != null) {
            rootLevel = Level.TRACE;
        }
        if (System.getProperty("debug") != null) {
            rootLevel = Level.DEBUG;
        }
        if (System.getProperty("info") != null) {
            rootLevel = Level.INFO;
        }
        if (System.getProperty("warn") != null) {
            rootLevel = Level.WARN;
        }
        if (System.getProperty("error") != null) {
            rootLevel = Level.ERROR;
        }
        if (System.getProperty("no_logging") != null) {
            rootLevel = Level.OFF;
        }

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
        logEncoder.setContext(context);

        if (System.getProperty("mdc") != null) {
            logEncoder.setPattern("%d{HH:mm:ss.SSS} %-5level [%thread] {%logger{36}} - %msg"
                    + "%n             MDC   {%mdc}%n%n");
        }
        else {
            logEncoder.setPattern("%d{HH:mm:ss.SSS} %-5level [%thread] {%logger{36}} - %msg%n%n");
        }
        logEncoder.start();

        ConsoleAppender<ILoggingEvent> logConsoleAppender = new ConsoleAppender<>();
        logConsoleAppender.setContext(context);
        logConsoleAppender.setName("console");
        logConsoleAppender.setEncoder(logEncoder);
        logConsoleAppender.start();

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.detachAndStopAllAppenders();
        root.addAppender(logConsoleAppender);
        root.setLevel(rootLevel);

        StatusManager statusManager = context.getStatusManager();
        statusManager.add(new InfoStatus("Programmatically removed existing root appenders,"
                + " adding formatted ConsoleAppender, root Logger threshold [" + rootLevel + "].",
                MatsExampleKit.class));

        StatusPrinter.print(context);
    }

    public static void configureLogLevel(String loggerName, Level level) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);
        logger.setLevel(level);
    }

    public static void configureLogLevel(Class<?> loggerClass, Level level) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerClass);
        logger.setLevel(level);
    }

    public static void configureLogRemovePeriodicDebug() {
        // Both of the following are annoying on the broker-side, periodically quite often outputting loglines
        configureLogLevel(Queue.class, Level.INFO);
        // .. and this is also annoying on the client-side
        configureLogLevel(AbstractInactivityMonitor.class, Level.INFO);
        // This is periodic, quite often, on the broker if persistence is enabled
        configureLogLevel(MessageDatabase.class, Level.INFO);
    }

    public static Logger getClassLogger() {
        return LoggerFactory.getLogger(getCallingClassNameAndMethod()[0]);
    }

    /**
     * Tries to find an available port at, or at maxAttempts ports above, the desired port.
     *
     * @param desiredPort
     *         which port really wanted.
     * @param maxAttempts
     *         how many ports to check. If <code>(9010, 10)</code>, it will check 9010-9019.
     * @return the available port, either the desired, or one available at maxAttempts above.
     * @return the avaiable port. Throws {@link IllegalArgumentException} if not possible within maxAttempts.
     */
    static int findAvailablePortAtOrUpwardsOf(int desiredPort, int maxAttempts) {
        int attempts = 0;
        int currentPort = desiredPort;
        while (true) {
            boolean available = checkIfPortIsAvailable(currentPort);
            if (available) {
                return currentPort;
            }
            currentPort++;
            attempts++;
            if (attempts == maxAttempts) {
                throw new IllegalArgumentException(
                        "Couldn't find an available port within [" + maxAttempts
                                + "] ports above [" + desiredPort + "]");
            }
        }
    }

    /**
     * Checks whether a port is available. Note that there is a race condition, whereby the port might be taken right
     * afterwards.
     *
     * @param port
     *         the port to check.
     * @return <code>true</code> if the port was available at the time being checked.
     */
    static boolean checkIfPortIsAvailable(int port) {
        ServerSocket serverSocket = null;
        try {
            // Check if we can make a ServerSocket for this port.
            // (There is a race condition here, someone else might grab the port before the server)
            serverSocket = new ServerSocket(port);
            // Ensure that the socket is available right after close.
            serverSocket.setReuseAddress(true);
        }
        catch (IOException e) {
            return false;
        }
        finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                }
                catch (IOException e) {
                    return false;
                }
            }
        }
        return true;
    }

    // Loaned from MatsTestHelp
    static String getCallingClassSimpleName() {
        String[] classnameAndMethod = getCallingClassNameAndMethod();
        int lastDot = classnameAndMethod[0].lastIndexOf('.');
        return lastDot > 0
                ? classnameAndMethod[0].substring(lastDot + 1)
                : classnameAndMethod[0];
    }

    /**
     * from <a href="https://stackoverflow.com/a/11306854">Stackoverflow - Denys Séguret</a>.
     */
    static String[] getCallingClassNameAndMethod() {
        StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stElements.length; i++) {
            StackTraceElement ste = stElements[i];
            if (!ste.getClassName().startsWith(MatsExampleKit.class.getPackageName())) {
                return new String[] { ste.getClassName(), ste.getMethodName() };
            }
        }
        throw new AssertionError("Could not determine calling class.");
    }
}
