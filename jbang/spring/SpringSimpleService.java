//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:RC0-1.0.0

package spring;

import org.springframework.context.annotation.Configuration;

import io.mats3.examples.MatsJbangKit;
import io.mats3.spring.EnableMats;
import io.mats3.spring.MatsMapping;

/**
 * A simple, Spring-based, main-class style, single-stage Endpoint, replying with a modified version of the request.
 * This is directly a Spring-variant of {@link simple.SimpleService} - the endpoint is identical otherwise. You can
 * fire up one of each if you want.
 */
@EnableMats // Enables Mats3 SpringConfig
@Configuration // Ensures that Spring processes inner classes, components, beans and configs
public class SpringSimpleService {
    public static void main(String... args) {
        MatsJbangKit.startSpring();
    }

    // A single-stage Endpoint defined using @MatsMapping
    @MatsMapping("SimpleService.simple")
    SimpleServiceReplyDto endpoint(SimpleServiceRequestDto msg) {
        String result = msg.string + ':' + msg.number + ":FromSimple";
        return new SimpleServiceReplyDto(result, result.length());
    }

    // ----- Contract DTOs:

    record SimpleServiceRequestDto(int number, String string) {
    }

    record SimpleServiceReplyDto(String result, int numChars) {
    }
}
