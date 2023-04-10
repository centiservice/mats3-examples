//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:RC1-1.0.0

package spring;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.mats3.examples.jbang.MatsJbangKit;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import io.mats3.util.MatsFuturizer.Reply;
import simple.SimpleServiceMainFuturization;

/**
 * This is a rather meaningless Spring variant of {@link SimpleServiceMainFuturization}, in that it does not really use
 * the Spring Context for anything other than getting the {@link MatsFuturizer} from - no bean processing or Mats3
 * SpringConfig is performed here.
 * <p>
 * <b>Again, as pointed out in these other main-class, single futurized calls, you should never use a MatsFuturizer in
 * this single-use way!</b> - the futurizer is meant to be a singleton, long-lived object in a long-lived service.
 */
public class SpringSimpleServiceMainFuturization {
    public static void main(String... args) throws ExecutionException, InterruptedException {
        AnnotationConfigApplicationContext springContext = MatsJbangKit.startSpring();
        MatsFuturizer matsFuturizer = springContext.getBean(MatsFuturizer.class);

        // ----- A single call
        CompletableFuture<Reply<SimpleServiceReplyDto>> future = matsFuturizer.futurizeNonessential(
                MatsTestHelp.traceId(), "SpringSimpleServiceMainFuturization", "SpringMediumService.matsClassMapping",
                SimpleServiceReplyDto.class, new SimpleServiceRequestDto(1, "TestOne"));

        // Output result
        System.out.println("######## Got reply! " + future.get().getReply());

        // Close down and exit.
        springContext.close();
    }

    // ----- Contract copied from SpringSimpleService

    record SimpleServiceRequestDto(int number, String string) {
    }

    record SimpleServiceReplyDto(String result, int numChars) {
    }
}
