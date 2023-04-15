//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:1.0.0

package stdexample;

import io.mats3.MatsEndpoint;
import io.mats3.MatsFactory;
import io.mats3.examples.jbang.MatsJbangJettyServer;

/**
 * Vulgarly complex Mats Endpoint to calculate <code>a*b - (c/d + e)</code>, consisting of three stages. Utilizes
 * EndpointB which can calculate <code>a*b</code>, and EndpointC which can calculate <code>a/b + c</code>.
 */
public class ServiceA {
    public static void main(String... args) {
        MatsJbangJettyServer.create(9010)
                .addMatsFactory()
                .setupUsingMatsFactory(ServiceA::setupEndpoint)
                .addMatsLocalInspect_WithRootHtml()
                .start();
    }

    private static class EndpointAState {
        double result_a_multiply_b;
        double c, d, e;
    }

    // Vulgarly complex Mats Endpoint to calculate 'a*b - (c/d + e)'.
    static void setupEndpoint(MatsFactory matsFactory) {
        MatsEndpoint<EndpointAReplyDTO, EndpointAState> ep = matsFactory
                .staged("ServiceA.endpointA", EndpointAReplyDTO.class, EndpointAState.class);

        ep.stage(EndpointARequestDTO.class, (ctx, state, msg) -> {
            // State: Keep c, d, e for next calculation
            state.c = msg.c;
            state.d = msg.d;
            state.e = msg.e;
            // Perform request to EndpointB to calculate 'a*b'
            ctx.request("ServiceB.endpointB", new EndpointBRequestDTO(msg.a, msg.b));
        });
        ep.stage(EndpointBReplyDTO.class, (ctx, state, msg) -> {
            // State: Keep the result from 'a*b' calculation
            state.result_a_multiply_b = msg.result;
            // Perform request to Endpoint C to calculate 'c/d + e'
            ctx.request("ServiceC.endpointC", new EndpointCRequestDTO(state.c, state.d, state.e));
        });
        ep.lastStage(EndpointCReplyDTO.class, (ctx, state, msg) -> {
            // We now have the two sub calculations to perform final subtraction
            double result = state.result_a_multiply_b - msg.result;
            // Reply with our result
            return new EndpointAReplyDTO(result);
        });
    }

    // ----- Imported DTOs for ServiceB

    record EndpointBRequestDTO(double a, double b) {}

    record EndpointBReplyDTO(double result) {}

    // ----- Imported DTOs for ServiceC

    record EndpointCRequestDTO(double a, double b, double c) {}

    record EndpointCReplyDTO(double result) {}

    // ====== ServiceA Request and Reply DTOs

    record EndpointARequestDTO(double a, double b, double c, double d, double e) {}

    record EndpointAReplyDTO(double result) {}
}
