package spring;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import io.mats3.MatsFactory;
import io.mats3.examples.MatsExampleKit;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import io.mats3.util.MatsFuturizer.Reply;

public class SpringSimpleServiceMainCall {

    private static final Logger log = MatsExampleKit.getClassLogger();

    public static void main(String... args) throws Exception {
        // Create a MatsFactory and MatsFuturizer
        MatsFactory matsFactory = MatsExampleKit.createMatsFactory();
        MatsFuturizer matsFuturizer = MatsFuturizer.createMatsFuturizer(matsFactory);

        // ----- A single call
        CompletableFuture<Reply<SpringSimpleServiceReplyDto>> future = matsFuturizer.futurizeNonessential(
                MatsTestHelp.traceId(), "SpringSimpleServiceMainCall", "SpringSimpleService.matsClassMapping",
                SpringSimpleServiceReplyDto.class,
                new SpringSimpleServiceRequestDto(Math.PI, Math.E, 1.234));

        // :: Receive, verify and print.
        SpringSimpleServiceReplyDto reply = future.get().getReply();
        boolean correct = Math.pow(Math.PI * Math.E, 1.234) == reply.result;
        log.info("######## Got reply! " + reply + " - " + (correct ? "Correct!" : "Wrong!"));

        // :: Clean up
        matsFuturizer.close();
        matsFactory.stop(30_000);
    }

    // ----- Contract copied from SimpleService

    record SpringSimpleServiceRequestDto(double multiplicand, double multiplier, double exponent) {
    }

    record SpringSimpleServiceReplyDto(double result) {
    }
}