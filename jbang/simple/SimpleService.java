//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//REPOS mavencentral,LocalMaven
//DEPS io.mats3.examples:mats-examples:1.0.0

package simple;

import javax.jms.ConnectionFactory;

import io.mats3.MatsFactory;
import io.mats3.examples.MatsExampleKit;

/**
 * A simple, Java only, main-class style, single-stage Endpoint, replying with a modified version of the request. While
 * this demonstrates how Mats3 works wrt. which components are involved for making the simplest Mats3 Endpoint, a
 * proper, long-running service should have more infrastructure wrt. monitoring and introspection, typically running
 * within a Servlet container or similar.
 */
public class SimpleService {
    public static void main(String... args) {
        ConnectionFactory jmsConnectionFactory = MatsExampleKit.createActiveMqConnectionFactory();

        MatsFactory matsFactory = MatsExampleKit.createMatsFactory(jmsConnectionFactory);

        // ----- The Single-Stage Endpoint
        matsFactory.single("SimpleService.simple",
                SimpleServiceReplyDto.class, SimpleServiceRequestDto.class,
                (processContext, msg) -> {
                    String result = msg.string + ':' + msg.number + ":FromSimple";
                    return new SimpleServiceReplyDto(result, result.length());
                });
    }

    // ----- Contract DTOs:

    record SimpleServiceRequestDto(int number, String string) {
    }

    record SimpleServiceReplyDto(String result, int numChars) {
    }
}
