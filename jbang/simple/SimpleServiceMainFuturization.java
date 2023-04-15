//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:1.0.0

package simple;

import io.mats3.MatsFactory;
import io.mats3.examples.jbang.MatsJbangKit;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import io.mats3.util.MatsFuturizer.Reply;

import javax.jms.ConnectionFactory;
import java.util.concurrent.CompletableFuture;

/**
 * A main-class which fires up a JMS ConnectionFactory, a MatsFactory, a MatsFuturizer, and then calls the
 * "SimpleService.simple" Endpoint, which must be running along with a localhost ActiveMQ (or at least be started soon
 * thereafter). Note that this is just an example to demonstrate how Mats3 works wrt. what components are involved - you
 * would not typically use this type of one-off single-invocation JVM logic. Mats3 is meant to be used as an
 * Intra-Service Communication system for multiple long-running services.
 */
public class SimpleServiceMainFuturization {

    public static void main(String... args) throws Exception {
        // :: Create a JMS ConnectionFactory, MatsFactory and MatsFuturizer to demonstrate the setup.
        ConnectionFactory jmsConnectionFactory = MatsJbangKit.createActiveMqConnectionFactory();
        MatsFactory matsFactory = MatsJbangKit.createMatsFactory(jmsConnectionFactory);
        MatsFuturizer matsFuturizer = MatsFuturizer.createMatsFuturizer(matsFactory);

        // ----- A single call
        CompletableFuture<Reply<SimpleServiceReplyDto>> future1 = matsFuturizer.futurizeNonessential(
                MatsTestHelp.traceId(), "SimpleServiceMainFuturization.main.1", "SimpleService.simple",
                SimpleServiceReplyDto.class, new SimpleServiceRequestDto(1, "TestOne"));
        // Sync wait for the reply
        System.out.println("######## Got reply #1! " + future1.get().getReply());

        // ---- Two parallel calls, async wait for reply
        var wait2A = matsFuturizer.futurizeNonessential(
                        MatsTestHelp.traceId(), "SimpleServiceMainFuturization.main.2A", "SimpleService.simple",
                        SimpleServiceReplyDto.class, new SimpleServiceRequestDto(20, "TestTwoA"))
                .thenAccept(reply -> System.out.println("######## Got reply #2A! " + reply.getReply()));

        var wait2B = matsFuturizer.futurizeNonessential(
                        MatsTestHelp.traceId(), "SimpleServiceMainFuturization.main.2B", "SimpleService.simple",
                        SimpleServiceReplyDto.class, new SimpleServiceRequestDto(21, "TestTwoB"))
                .thenAccept(reply -> System.out.println("######## Got reply #2B! " + reply.getReply()));

        wait2A.join();
        wait2B.join();

        // :: Clean up to exit.
        matsFuturizer.close();
        matsFactory.close();
    }

    // ----- Contract copied from SimpleService

    record SimpleServiceRequestDto(int number, String string) {
    }

    record SimpleServiceReplyDto(String result, int numChars) {
    }
}
