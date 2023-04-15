//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:RC1-1.0.0

package spring;

import io.mats3.examples.jbang.MatsJbangKit;
import io.mats3.spring.EnableMats;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

/**
 * Exercises the 'SpringMediumService.matsClassMapping' using a Spring service bean. Note: This is most definitely not
 * how to use a MatsFuturizer in production! This is only to demonstrate a single call from a main-class.
 */
@EnableMats // Enables Mats3 SpringConfig
@Configuration // Ensures that Spring processes inner classes, components, beans and configs
public class SpringMediumServiceSpringFuturization {

    public static void main(String... args) {
        var springContext = MatsJbangKit.startSpring();
        // Fetch the Spring service bean from the Spring Context
        var client = springContext.getBean(SpringMediumServiceClient.class);
        // Invoke it. (Note: This should never be done inside a Mats Stage!)
        double result = client.multiplyAndExponentiate(5, 6, Math.PI);
        // Dump the Spring Context, taking down everything.
        springContext.close();

        // Verify and output
        boolean correct = Math.pow(5 * 6, Math.PI) == result;
        System.out.println("######## Got reply! " + result + " - " + (correct ? "Correct!" : "Wrong!"));
    }

    @Service
    static class SpringMediumServiceClient {
        @Autowired
        private MatsFuturizer _matsFuturizer;

        double multiplyAndExponentiate(double multiplicand, double multiplier, double exponent) {
            var future = _matsFuturizer.futurizeNonessential(MatsTestHelp.traceId(),
                    "SpringMediumServiceClient.client", "SpringMediumService.matsClassMapping",
                    SpringMediumServiceReplyDto.class,
                    new SpringMediumServiceRequestDto(multiplicand, multiplier, exponent));
            try {
                return future.get().getReply().result();
            }
            catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Couldn't get result from Service.", e);
            }
        }
    }

    // ----- Contract copied from SpringMediumService

    record SpringMediumServiceRequestDto(double multiplicand, double multiplier, double exponent) {
    }

    record SpringMediumServiceReplyDto(double result) {
    }
}
