//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//REPOS mavencentral,LocalMaven=file:///home/endre/localmaven
//DEPS io.mats3.examples:mats-examples:0.0.1
// DEPS org.apache.activemq:activemq-broker:5.16.6
// DEPS org.apache.activemq:activemq-kahadb-store:5.16.6
// DEPS ch.qos.logback:logback-classic:1.4.6

import io.mats3.examples.MatsExampleKit;
import io.mats3.test.broker.MatsTestBroker;
import io.mats3.test.broker.MatsTestBroker.ActiveMq;

/**
 * Starts an ActiveMQ instance on standard port 61616. "Mats3 optimized", but you can also use clean distro. Configured
 * features, ordered with most important first: Include Stats plugin, Individual DLQs, Prioritized messages, GC inactive
 * destinations, lesser prefetch, split memory, DLQ expired msgs.
 */
public class ActiveMqRun {
    public static void main(String[] args) {
        MatsExampleKit.configureLogbackToConsole_Info();
        MatsTestBroker.newActiveMqBroker(ActiveMq.LOCALHOST, ActiveMq.SHUTDOWNHOOK)
                // Just wait until Ctrl-C or equivalent.
                .waitUntilStopped();
    }
}