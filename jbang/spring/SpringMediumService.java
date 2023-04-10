//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:RC1-1.0.0

package spring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import io.mats3.MatsEndpoint.ProcessContext;
import io.mats3.MatsFactory;
import io.mats3.examples.jbang.MatsJbangKit;
import io.mats3.spring.EnableMats;
import io.mats3.spring.MatsClassMapping;
import io.mats3.spring.MatsClassMapping.Stage;
import io.mats3.spring.MatsMapping;

/** Demonstrates Mats3' SpringConfig with @MatsMapping and @MatsClassMapping. */
@EnableMats // Enables Mats3 SpringConfig
@Configuration // Ensures that Spring processes inner classes, components, beans and configs
public class SpringMediumService {
    public static void main(String... args) {
        // One way to do it: Manually create MatsFactory in main, then use this for Spring
        // Could also have made it using a @Bean.
        MatsFactory matsFactory = MatsJbangKit.createMatsFactory();
        // Fire up Spring, manually
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(MatsFactory.class, () -> matsFactory);
        ctx.register(SpringMediumService.class);
        ctx.refresh();
        // Note: SpringSimpleService instead uses a single-line MatsExampleKit to start Spring.
    }

    @Service
    static class MultiplierService {
        double multiply(double multiplicand, double multiplier) {
            return multiplicand * multiplier;
        }
    }

    @Autowired
    private MultiplierService _multiplierService;

    private static final String PRIVATE_ENDPOINT = "SpringMediumService.private.matsMapping";

    // A single-stage Endpoint defined using @MatsMapping
    @MatsMapping(PRIVATE_ENDPOINT)
    PrivateReplyDto endpoint(PrivateRequestDto msg) {
        return new PrivateReplyDto(_multiplierService.multiply(msg.multiplicand, msg.multiplier));
    }


    /**
     * An interface for a "Service", which is required by the @MatsClassMapping later, and provided by a
     * {@code @Bean} in the {@code @Configuration} class below, {@link TestConfiguration}.
     */
    interface ExponentiationService {
        double exponentiate(double base, double exponent);
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        public ExponentiationService exponentiationService() {
            return new ExponentiationService() {
                @Override
                public double exponentiate(double base, double exponent) {
                    return Math.pow(base, exponent);
                }
            };
        }
    }

    // A multi-stage Endpoint defined using @MatsClassMapping
    @MatsClassMapping("SpringMediumService.matsClassMapping")
    static class SpringService_MatsClassMapping_Leaf {

        // Autowired/Injected Spring Bean
        @Autowired
        private transient ExponentiationService _exponentiationService;

        // ProcessContext is injected, but can also be provided as argument, simplifying testing.
        private ProcessContext<SpringMediumServiceReplyDto> _context;

        // This is a state field, since it is not the other two types, and not static.
        private double _exponent;

        @Stage(Stage.INITIAL)
        void initial(SpringMediumServiceRequestDto msg) {
            _exponent = msg.exponent;
            _context.request(PRIVATE_ENDPOINT, new PrivateRequestDto(msg.multiplicand, msg.multiplier));
        }

        @Stage(10)
        SpringMediumServiceReplyDto second(PrivateReplyDto msg) {
            double result = _exponentiationService.exponentiate(msg.result, _exponent);
            return new SpringMediumServiceReplyDto(result);
        }
    }

    // ----- Private Endpoint DTOs

    record PrivateRequestDto(double multiplicand, double multiplier) {
    }

    record PrivateReplyDto(double result) {
    }


    // ----- Contract DTOs:

    record SpringMediumServiceRequestDto(double multiplicand, double multiplier, double exponent) {
    }

    record SpringMediumServiceReplyDto(double result) {
    }
}
