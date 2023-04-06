//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//REPOS mavencentral,LocalMaven
//DEPS io.mats3.examples:mats-examples:1.0.0

package stdexample;

import io.mats3.MatsEndpoint;
import io.mats3.MatsFactory;
import io.mats3.examples.MatsExampleJettyServer;

/**
 * Mats two-stage Endpoint which calculates <code>a/b + c</code>. Utilizes EndpointD which can calculate
 * <code>a/b</code>.
 */
public class ServiceC {
    public static void main(String... args) {
        MatsExampleJettyServer.create(9030)
                .addMatsFactory()
                .setupUsingMatsFactory(ServiceC::setupEndpoint)
                .addMatsLocalInspect_WithRootHtml()
                .start();
    }

    private static class EndpointCState {
        double c;
    }

    static void setupEndpoint(MatsFactory matsFactory) {
        MatsEndpoint<EndpointCReplyDTO, EndpointCState> ep = matsFactory
                .staged("ServiceC.endpointC", EndpointCReplyDTO.class, EndpointCState.class);
        ep.stage(EndpointCRequestDTO.class, (ctx, state, msg) -> {
            // State: Keep c for next calculation
            state.c = msg.c;
            // Perform request to EndpointD to calculate 'a/b'
            ctx.request("ServiceD.endpointD", new EndpointDRequestDTO(msg.a, msg.b));
        });
        ep.lastStage(EndpointDReplyDTO.class, (ctx, state, msg) -> {
            double result = msg.result + state.c;
            return new EndpointCReplyDTO(result);
        });
    }

    // ----- Imported DTOs for ServiceD

    record EndpointDRequestDTO(double a, double b) {}

    record EndpointDReplyDTO(double result) {}

    // ====== ServiceC Request and Reply DTOs

    record EndpointCRequestDTO(double a, double b, double c) {}

    record EndpointCReplyDTO(double result) {}
}
