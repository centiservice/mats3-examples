//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:1.0.0

package spring;

import io.mats3.examples.jbang.MatsJbangKit;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import io.mats3.util.MatsFuturizer.Reply;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exercises the 'SpringMediumService.matsClassMapping'. Note: This is most definitely not how to use a MatsFuturizer in
 * production! This is only to demonstrate a single call from a main-class.
 */
public class SpringMediumServiceMainFuturization {
    public static void main(String... args) throws Exception {
        MatsFuturizer matsFuturizer = MatsJbangKit.createMatsFuturizer();

        double random = ThreadLocalRandom.current().nextDouble(-10, 10);

        // ----- A single call
        CompletableFuture<Reply<SpringMediumServiceReplyDto>> future = matsFuturizer.futurizeNonessential(
                MatsTestHelp.traceId(), "SpringMediumServiceMainFuturization.main",
                "SpringMediumService.matsClassMapping", SpringMediumServiceReplyDto.class,
                new SpringMediumServiceRequestDto(Math.PI, Math.E, random));

        // :: Receive, verify and print.
        SpringMediumServiceReplyDto reply = future.get().getReply();
        boolean correct = Math.pow(Math.PI * Math.E, random) == reply.result;
        System.out.println("######## Got reply! " + reply + " - " + (correct ? "Correct!" : "Wrong!"));

        // Clean up
        matsFuturizer.close();
    }

    // ----- Contract copied from SpringMediumService

    record SpringMediumServiceRequestDto(double multiplicand, double multiplier, double exponent) {
    }

    record SpringMediumServiceReplyDto(double result) {
    }
}