package io.mats3.examples;

import java.net.URL;

import org.apache.activemq.ActiveMQConnectionFactory;
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
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;
import io.mats3.MatsFactory;
import io.mats3.impl.jms.JmsMatsFactory;
import io.mats3.impl.jms.JmsMatsJmsSessionHandler_Pooling;
import io.mats3.serial.json.MatsSerializerJson;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.RandomString;

/**
 * @author Endre Stølsvik 2023-03-21 22:52 - http://stolsvik.com/, endre@stolsvik.com
 */
public class MatsExampleKit {

    private static final Logger log = MatsTestHelp.getClassLogger();

    public static MatsFactory createMatsFactory(String appname) {
        MatsExampleKit.configureLogbackToConsole_Info();

        // :: Make ActiveMq JMS ConnectionFactory, towards localhost (which is default, using failover protocol)
        ActiveMQConnectionFactory jmsConnectionFactory = new ActiveMQConnectionFactory();
        // We won't be needing Topic Advisories (we don't use temp queues/topics), so don't subscribe to them.
        jmsConnectionFactory.setWatchTopicAdvisories(false);

        // :: Make the JMS-based MatsFactory, providing the JMS ConnectionFactory
        MatsFactory matsFactory = JmsMatsFactory.createMatsFactory_JmsOnlyTransactions(appname, "#examples#",
                JmsMatsJmsSessionHandler_Pooling.create(jmsConnectionFactory),
                MatsSerializerJson.create());
        // .. turn down the concurrency from default cpus * 2, as that is pretty heavy on an e.g. 8-core machine.
        matsFactory.getFactoryConfig().setConcurrency(2);
        // .. set a unique nodename emulating "hosts", so if we use futurizers, they won't step on each other's toes
        String origNodename = matsFactory.getFactoryConfig().getNodename();
        matsFactory.getFactoryConfig().setNodename(origNodename + RandomString.randomString(6));

        // :: Add a shutdownhook to take it down in case of e.g. Ctrl-C - if it has not been done by the code.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (matsFactory.getFactoryConfig().isRunning()) {
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

        String prop = System.getProperty("trace");
        if (prop != null) {
            rootLevel = Level.TRACE;
        }
        prop = System.getProperty("debug");
        if (prop != null) {
            rootLevel = Level.DEBUG;
        }
        prop = System.getProperty("info");
        if (prop != null) {
            rootLevel = Level.INFO;
        }
        prop = System.getProperty("warn");
        if (prop != null) {
            rootLevel = Level.WARN;
        }
        prop = System.getProperty("error");
        if (prop != null) {
            rootLevel = Level.ERROR;
        }
        prop = System.getProperty("off");
        if (prop != null) {
            rootLevel = Level.OFF;
        }

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
        logEncoder.setContext(context);

        prop = System.getProperty("mdc");
        if (prop != null) {
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
                + " adding ConsoleAppender with root threshold [" + rootLevel + "].", MatsExampleKit.class));

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

    public static URL getActiveMqConsoleWarFile() {
        long nanosAtStart_findingUrl = System.nanoTime();
        try (ScanResult result = new ClassGraph()
                .acceptJars("activemq-web-console*.war")
                .disableNestedJarScanning()
                // .verbose() // Log to stderr
                .enableAllInfo() // Scan classes, methods, fields, annotations
                .scan()) { // Start the scan
            ResourceList resourceList = result.getResourcesWithLeafName("index.jsp");
            log.info("Size: " + resourceList.getURLs().size());
            if (resourceList.getURLs().isEmpty()) {
                throw new IllegalStateException("dammit");
            }
            URL url = resourceList.get(1).getClasspathElementURL();

            double msTaken = (System.nanoTime() - nanosAtStart_findingUrl) / 1_000_000d;

            log.info("url: " + url + ", ms taken: " + msTaken);

            return url;
        }
    }
}
