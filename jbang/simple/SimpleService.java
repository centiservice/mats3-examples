//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//REPOS mavencentral,LocalMaven
//DEPS io.mats3.examples:mats-examples:0.0.1

package simple;

import io.mats3.MatsFactory;
import io.mats3.examples.MatsExampleKit;

/** A simple, single-stage Endpoint, returning a modified version of the input. */
public class SimpleService {
    public static void main(String... args) {
        MatsFactory matsFactory = MatsExampleKit.createMatsFactory("SimpleService");
        matsFactory.getFactoryConfig().setConcurrency(8);

        // ----- The Single-Stage Endpoint
        matsFactory.single("SimpleService.simple",
                SimpleServiceReplyDto.class, SimpleServiceRequestDto.class,
                (processContext, msg) -> {
                    String result = msg.string + ':' + msg.number + ":FromSingle";
                    return new SimpleServiceReplyDto(result, result.length());
                });
    }

    // ----- Contract DTOs:

    record SimpleServiceRequestDto(int number, String string) {
    }

    record SimpleServiceReplyDto(String result, int numChars) {
    }
}
