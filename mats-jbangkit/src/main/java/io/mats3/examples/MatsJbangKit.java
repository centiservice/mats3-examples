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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.util.StatusPrinter;
import io.mats3.MatsFactory;
import io.mats3.impl.jms.JmsMatsFactory;
import io.mats3.impl.jms.JmsMatsJmsSessionHandler_Pooling;
import io.mats3.serial.json.MatsSerializerJson;
import io.mats3.util.MatsFuturizer;
import io.mats3.util.RandomString;

/**
 * This class, along with {@link MatsJbangJettyServer}, offers a set of tools for easy setup of a Mats3 Endpoint or
 * client, with an optional HTTP server (Jetty). The aim is to minimize infrastructure and boilerplate code, allowing
 * you to focus on exploring Mats3's features without worrying about the setup process.
 *
 * @author Endre Stølsvik 2023-03-21 22:52 - http://stolsvik.com/, endre@stolsvik.com
 */
public class MatsJbangKit {
    /**
     * Creates an ActiveMQ {@link ConnectionFactory} towards localhost, and configures it with a few (optional) relevant
     * features: Drop subscription to topic advisories, defines an exponential redelivery policy but then specifies only
     * 1 redelivery attempt (since this is for testing, not production), and specifies nonblocking redeliveries, since
     * Mats3 Endpoints can never rely on in-order delivery.
     *
     * @return the JMS {@link ConnectionFactory} to localhost ActiveMQ.
     */
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

    /**
     * Convenience for {@link #createMatsFactory(ConnectionFactory, String)} where the JMS {@link ConnectionFactory} is
     * created using {@link #createActiveMqConnectionFactory()}, and the appname is deduced from the calling class (as
     * gotten from current thread's stacktrace).
     *
     * @return the created MatsFactory.
     */
    public static JmsMatsFactory<String> createMatsFactory() {
        MatsJbangKit.configureLogbackToConsole_Info();
        return createMatsFactory(getCallingClassSimpleName());
    }

    /**
     * Convenience for {@link #createMatsFactory(ConnectionFactory, String)} where the JMS {@link ConnectionFactory} is
     * created using {@link #createActiveMqConnectionFactory()}.
     *
     * @param appName
     *         what appName to use for the MatsFactory.
     * @return the created MatsFactory.
     */
    public static JmsMatsFactory<String> createMatsFactory(String appName) {
        MatsJbangKit.configureLogbackToConsole_Info();
        return createMatsFactory(createActiveMqConnectionFactory(), appName);
    }

    /**
     * Convenience for {@link #createMatsFactory(ConnectionFactory, String)} where the appname is deduced from the
     * calling class (as gotten from current thread's stacktrace).
     *
     * @param jmsConnectionFactory
     *         the {@link ConnectionFactory} to use for the {@link JmsMatsFactory}.
     * @return the created MatsFactory.
     */
    public static JmsMatsFactory<String> createMatsFactory(ConnectionFactory jmsConnectionFactory) {
        MatsJbangKit.configureLogbackToConsole_Info();
        return createMatsFactory(jmsConnectionFactory, getCallingClassSimpleName());
    }

    /**
     * Creates a JMS-transaction-only MatsFactory using the supplied JMS {@link ConnectionFactory} and app name - also
     * adds some randomness to the node name (in addition to default hostname), so that each JVM "emulates" a different
     * node.
     *
     * @param jmsConnectionFactory
     *         the {@link ConnectionFactory} to use for the {@link JmsMatsFactory}.
     * @param appName
     *         what appName to use for the MatsFactory.
     * @return the created MatsFactory.
     */
    public static JmsMatsFactory<String> createMatsFactory(ConnectionFactory jmsConnectionFactory, String appName) {
        MatsJbangKit.configureLogbackToConsole_Info();

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

    /**
     * If you just need a {@link MatsFuturizer} to talk to the Mats fabric on ActiveMQ on localhost, and do not need the
     * actual {@link MatsFactory MatsFactory} nor the JMS {@link ConnectionFactory}, you can use this method to get one
     * right away. It will employ the {@link #createMatsFactory()} to make the <code>MatsFactory</code>, which again
     * uses {@link #createActiveMqConnectionFactory()} to get the JMS <code>ConnectionFactory</code> to localhost
     * ActiveMQ.
     * <p>
     * <b>You should never create a MatsFuturizer for a single call in production!</b>
     * This method is only usable for demonstrating interaction with the Mats fabric from a main-class. A MatsFuturizer
     * is a singleton, long-lived service - you only need a single instance for a long-lived JVM.
     *
     * @return a newly created {@link MatsFuturizer}, which employs a newly created {@link MatsFactory}, which employs a
     * newly created JMS {@link ConnectionFactory}.
     */
    public static MatsFuturizer createMatsFuturizer() {
        // :: Hacking together a solution to also close MatsFactory when MatsFuturizer is closed.
        JmsMatsFactory<String> matsFactory = createMatsFactory();
        String endpointIdPrefix = matsFactory.getFactoryConfig().getAppName();
        int corePoolSize = Math.max(5, matsFactory.getFactoryConfig().getConcurrency() * 4);
        int maximumPoolSize = Math.max(100, matsFactory.getFactoryConfig().getConcurrency() * 20);

        return new MatsFuturizer(matsFactory, endpointIdPrefix, corePoolSize, maximumPoolSize, 50_000) {
            @Override
            public void close() {
                super.close();
                _matsFactory.close();
            }
        };
    }

    /**
     * Creates a Spring {@link AnnotationConfigApplicationContext}, populating it with a {@link MatsFactory} so that the
     * annotation {@link io.mats3.spring.EnableMats @EnableMats} works, and a {@link MatsFuturizer} for simple injection
     * when needed - the latter is lazy inited. It registers the supplied component classes, and then refreshes the
     * context (i.e. starts it), which will "boot" any {@link io.mats3.spring.MatsMapping @MatsMapping}s and
     * {@link io.mats3.spring.MatsClassMapping @MatsClassMapping}s.
     *
     * @param componentClasses
     *         which classes should be registered as Spring component classes, both {@literal @Configuration} classes,
     *         and {@literal @Components} and its derivatives.
     * @return the created {@link AnnotationConfigApplicationContext}.
     */
    public static AnnotationConfigApplicationContext startSpring(Class<?>... componentClasses) {
        // Create the MatsFactory (implicitly gets the JMS ConnectionFactory)
        JmsMatsFactory<String> matsFactory = MatsJbangKit.createMatsFactory();

        // Fire up Spring
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        // Register Futurizer, lazy init bean.
        ctx.registerBean(MatsFuturizer.class, () -> MatsFuturizer.createMatsFuturizer(matsFactory),
                bd -> bd.setLazyInit(true));
        // Register MatsFactory
        // Snag: Evidently, when using registerBean, Spring's automatic 'destroy method inference' seems to only
        // work for classes implementing Closeable or AutoCloseable. JmsMatsFactory does not, yet. Thus, specify.
        ctx.registerBean(JmsMatsFactory.class, () -> matsFactory, bd -> bd.setDestroyMethodName("close"));

        ctx.register(componentClasses);
        ctx.refresh();
        return ctx;
    }

    /**
     * Convenience variant of {@link #startSpring(Class[])} which deduces a component class, typically a
     * {@link org.springframework.context.annotation.Configuration @Configuration} class, from the calling class as
     * deduced by the thread stack trace.
     *
     * @return the created {@link AnnotationConfigApplicationContext}.
     */
    public static AnnotationConfigApplicationContext startSpring() {
        // Find caller class
        String callerclassname = MatsJbangKit.getCallingClassNameAndMethod()[0];
        Class<?> callingClass;
        try {
            callingClass = Class.forName(callerclassname);
        }
        catch (ClassNotFoundException e) {
            throw new AssertionError("Didn't find caller class [" + callerclassname + "].", e);
        }
        return startSpring(callingClass);
    }

    /**
     * Configures Logback using {@link #configureLogbackToConsole(Level)}, setting root log level to DEBUG.
     */
    public static void configureLogbackToConsole_Debug() {
        configureLogbackToConsole(Level.DEBUG);
        configureLogRemovePeriodicDebug();
    }

    /**
     * Configures Logback using {@link #configureLogbackToConsole(Level)}, setting root log level to INFO.
     */
    public static void configureLogbackToConsole_Info() {
        configureLogbackToConsole(Level.INFO);
    }

    /**
     * Configures Logback using {@link #configureLogbackToConsole(Level)}, setting root log level to WARN.
     */
    public static void configureLogbackToConsole_Warn() {
        configureLogbackToConsole(Level.WARN);
    }

    private static volatile boolean __logbackAlreadyConfigured;

    /**
     * Configures Logback, setting up a Console appender with a readable format for console (not printing the MDC unless
     * JVM is started with '<code>-Dmdc</code>'), and sets the root logging threshold to the specified log level (but
     * can be overridden with '<code>-D{level}</code>', where {level} can be trace, debug, info, warn, error, and
     * no_logging).
     * <p>
     * Note that it will only be configured once even in face of multiple invocations. Most methods in the Example Kit
     * configures logging with INFO, but if you want anything else, just configure it earlier - or use the '-D{level}'
     * functionality.
     *
     * @param rootLevel
     *         the root logging threshold.
     */
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
                MatsJbangKit.class));

        StatusPrinter.print(context);
    }

    /**
     * Configures the log level for the specified logger name.
     *
     * @param loggerName
     *         the logger for which to set the log level.
     * @param level
     *         what log level (threshold) the logger should have.
     */
    public static void configureLogLevel(String loggerName, Level level) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);
        logger.setLevel(level);
    }

    /**
     * Configures the log level for the specified logger name, as gotten from the specified class.
     *
     * @param loggerClass
     *         the class for which to set the log level.
     * @param level
     *         what log level (threshold) the logger should have.
     */
    public static void configureLogLevel(Class<?> loggerClass, Level level) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerClass);
        logger.setLevel(level);
    }

    /**
     * Some parts of ActiveMQ are annoying in that they produce periodic log lines on DEBUG which add little relevant
     * information for this exploration. Automatically invoked by {@link #configureLogbackToConsole_Debug()}.
     */
    public static void configureLogRemovePeriodicDebug() {
        // Both of the following are annoying on the broker-side, periodically quite often outputting loglines
        configureLogLevel(Queue.class, Level.INFO);
        // .. and this is also annoying on the client-side
        configureLogLevel(AbstractInactivityMonitor.class, Level.INFO);
        // This is periodic, quite often, on the broker if persistence is enabled
        configureLogLevel(MessageDatabase.class, Level.INFO);
    }

    /**
     * Small helper to get a {@link Logger} for the invoking class (as gotten from a stacktrace) - is helpful for
     * copy-pasting code!
     *
     * @return a Logger for the invoking class.
     */
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
            if (!ste.getClassName().startsWith(MatsJbangKit.class.getPackageName())) {
                return new String[] { ste.getClassName(), ste.getMethodName() };
            }
        }
        throw new AssertionError("Could not determine calling class.");
    }
}
