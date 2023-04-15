//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:1.0.0

package simple;

import io.mats3.examples.jbang.MatsJbangKit;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import io.mats3.util.MatsFuturizer.Reply;

import java.util.concurrent.CompletableFuture;

public class SimpleServiceMainFuturizationMinimal {

    public static void main(String... args) throws Exception {
        MatsFuturizer matsFuturizer = MatsJbangKit.createMatsFuturizer();

        // A "futurization" to the 'SimpleService.simple' MatsEndpoint
        CompletableFuture<Reply<SimpleServiceReplyDto>> future1 = matsFuturizer.futurizeNonessential(
                MatsTestHelp.traceId(), "SimpleServiceMainFuturization.main.1", "SimpleService.simple",
                SimpleServiceReplyDto.class, new SimpleServiceRequestDto(1, "TestOne"));
        // Sync wait for the reply
        System.out.println("######## Got reply #1! " + future1.get().getReply());

        // Clean up
        matsFuturizer.close();
    }

    // ----- Contract copied from SimpleService

    record SimpleServiceRequestDto(int number, String string) {
    }

    record SimpleServiceReplyDto(String result, int numChars) {
    }
}
