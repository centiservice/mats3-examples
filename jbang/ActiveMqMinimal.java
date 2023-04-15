//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:1.0.0

import io.mats3.examples.jbang.MatsJbangKit;
import io.mats3.test.broker.MatsTestBroker;
import io.mats3.test.broker.MatsTestBroker.ActiveMq;

public class ActiveMqMinimal {
    public static void main(String[] args) {
        MatsJbangKit.configureLogbackToConsole_Info();
        MatsTestBroker.newActiveMqBroker(ActiveMq.LOCALHOST, ActiveMq.SHUTDOWNHOOK)
                .waitUntilStopped();
    }
}