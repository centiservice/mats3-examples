package spring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import io.mats3.MatsEndpoint.ProcessContext;
import io.mats3.MatsFactory;
import io.mats3.examples.MatsExampleKit;
import io.mats3.spring.EnableMats;
import io.mats3.spring.MatsClassMapping;
import io.mats3.spring.MatsClassMapping.Stage;
import io.mats3.spring.MatsMapping;

@EnableMats
@Configuration // Ensures that inner classes are also processed
public class SpringSimpleService {
    public static void main(String... args) {
        MatsFactory matsFactory = MatsExampleKit.createMatsFactory();

        // Fire up Spring
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(MatsFactory.class, () -> matsFactory);
        ctx.register(SpringSimpleService.class);
        ctx.refresh();
    }

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

    @Service
    static class MultiplierService {
        double multiply(double multiplicand, double multiplier) {
            return multiplicand * multiplier;
        }
    }

    @Autowired
    private MultiplierService _multiplierService;

    private static final String PRIVATE_ENDPOINT = "SpringSimpleService.private.matsMapping";

    @MatsMapping(PRIVATE_ENDPOINT)
    PrivateReplyDto endpoint(PrivateRequestDto msg) {
        return new PrivateReplyDto(_multiplierService.multiply(msg.multiplicand, msg.multiplier));
    }

    @MatsClassMapping("SpringSimpleService.matsClassMapping")
    static class SpringService_MatsClassMapping_Leaf {
        // Autowired/Injected "service"
        @Autowired
        private transient ExponentiationService _exponentiationService;

        // ProcessContext is injected, but can also be provided as argument, simplifying testing.
        private ProcessContext<SpringSimpleServiceReplyDto> _context;

        // This is a state field, since it is not the other two types.
        private double _exponent;

        @Stage(Stage.INITIAL)
        void initial(SpringSimpleServiceRequestDto msg) {
            _exponent = msg.exponent;
            _context.request(PRIVATE_ENDPOINT, new PrivateRequestDto(msg.multiplicand, msg.multiplier));
        }

        @Stage(10)
        SpringSimpleServiceReplyDto second(PrivateReplyDto msg) {
            double result = _exponentiationService.exponentiate(msg.result, _exponent);
            return new SpringSimpleServiceReplyDto(result);
        }
    }

    // ----- Private Endpoint DTOs

    record PrivateRequestDto(double multiplicand, double multiplier) {
    }

    record PrivateReplyDto(double result) {
    }


    // ----- Contract DTOs:

    record SpringSimpleServiceRequestDto(double multiplicand, double multiplier, double exponent) {
    }

    record SpringSimpleServiceReplyDto(double result) {
    }

}
