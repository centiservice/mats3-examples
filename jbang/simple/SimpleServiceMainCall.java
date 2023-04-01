//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//REPOS mavencentral,LocalMaven
//DEPS io.mats3.examples:mats-examples:0.0.1

package simple;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import io.mats3.MatsFactory;
import io.mats3.examples.MatsExampleKit;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import io.mats3.util.MatsFuturizer.Reply;

/**
 * A main-class which fires up a MatsFactory, a MatsFuturizer, and then calls the "SimpleService.simple"
 * Endpoint.
 */
public class SimpleServiceMainCall {

    private static final Logger log = MatsTestHelp.getClassLogger();

    public static void main(String... args) throws Exception {
        // Create a MatsFactory and MatsFuturizer
        MatsFactory matsFactory = MatsExampleKit.createMatsFactory("TestCall");
        MatsFuturizer matsFuturizer = MatsFuturizer.createMatsFuturizer(matsFactory);

        // ----- A single call
        CompletableFuture<Reply<SimpleServiceReplyDto>> future1 = matsFuturizer.futurizeNonessential(
                MatsTestHelp.traceId(), "TestCall.main.1", "SimpleService.simple",
                SimpleServiceReplyDto.class,
                new SimpleServiceRequestDto(1, "TestOne"));
        log.info("######## Got reply #1! " + future1.get().getReply());

        // ---- Two parallel calls.
        CompletableFuture<Reply<SimpleServiceReplyDto>> future2A = matsFuturizer.futurizeNonessential(
                MatsTestHelp.traceId(), "TestCall.main.2A", "SimpleService.simple",
                SimpleServiceReplyDto.class,
                new SimpleServiceRequestDto(20, "TestTwoA"));
        CompletableFuture<Reply<SimpleServiceReplyDto>> future2B = matsFuturizer.futurizeNonessential(
                MatsTestHelp.traceId(), "TestCall.main.2B", "SimpleService.simple",
                SimpleServiceReplyDto.class,
                new SimpleServiceRequestDto(21, "TestTwoB"));
        log.info("######## Got reply #2A! " + future2A.get().getReply());
        log.info("######## Got reply #2B! " + future2B.get().getReply());

        // :: Close out
        matsFuturizer.close();
        matsFactory.stop(30_000);
    }

    // ----- Contract copied from SimpleService

    record SimpleServiceRequestDto(int number, String string) {
    }

    record SimpleServiceReplyDto(String result, int numChars) {
    }

}
