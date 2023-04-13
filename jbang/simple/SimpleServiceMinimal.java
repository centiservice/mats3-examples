//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:RC1-1.0.0

package simple;

import io.mats3.examples.jbang.MatsJbangKit;

public class SimpleServiceMinimal {
    public static void main(String... args) {
        var matsFactory = MatsJbangKit.createMatsFactory();
        matsFactory.single("SimpleService.simple",
                SimpleServiceReplyDto.class, SimpleServiceRequestDto.class,
                (processContext, msg) -> {
                    String result = msg.string + ':' + msg.number + ":FromSimple";
                    return new SimpleServiceReplyDto(result, result.length());
                });
    }

    // ----- Contract Request and Reply DTOs

    record SimpleServiceRequestDto(int number, String string) {
    }

    record SimpleServiceReplyDto(String result, int numChars) {
    }
}