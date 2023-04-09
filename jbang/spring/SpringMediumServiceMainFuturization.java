//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//REPOS mavencentral,LocalMaven
//DEPS io.mats3.examples:mats-examples:RC0-1.0.0

package spring;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import io.mats3.examples.MatsExampleKit;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import io.mats3.util.MatsFuturizer.Reply;

/**
 * Exercises the {@link spring.SpringMediumService}. Note: This is most definitely not how to use a MatsFuturizer in
 * production! This is only to demonstrate a single call from a main-class.
 */
public class SpringMediumServiceMainFuturization {
    public static void main(String... args) throws Exception {
        MatsExampleKit.configureLogbackToConsole_Warn();

        // NOTE: NEVER do this in production! MatsFuturizer is a singleton, long-lived service!
        try (MatsFuturizer matsFuturizer = MatsExampleKit.createMatsFuturizer()) {
            // ----- A single call
            double random = ThreadLocalRandom.current().nextDouble(-10, 10);
            CompletableFuture<Reply<SpringMediumServiceReplyDto>> future = matsFuturizer.futurizeNonessential(
                    MatsTestHelp.traceId(), "SpringMediumServiceMainFuturization",
                    "SpringMediumService.matsClassMapping", SpringMediumServiceReplyDto.class,
                    new SpringMediumServiceRequestDto(Math.PI, Math.E, random));

            // :: Receive, verify and print.
            SpringMediumServiceReplyDto reply = future.get().getReply();
            boolean correct = Math.pow(Math.PI * Math.E, random) == reply.result;
            System.out.println("######## Got reply! " + reply + " - " + (correct ? "Correct!" : "Wrong!"));
        }
    }

    // ----- Contract copied from SimpleService

    record SpringMediumServiceRequestDto(double multiplicand, double multiplier, double exponent) {
    }

    record SpringMediumServiceReplyDto(double result) {
    }
}