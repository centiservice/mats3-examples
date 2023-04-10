//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:RC0-1.0.0

package stdexample;

import io.mats3.MatsFactory;
import io.mats3.examples.MatsJbangJettyServer;

/**
 * Mats single-stage Endpoint which calculates <code>a*b</code>. A single Endpoint is a convenience method of creating a
 * Mats Endpoint with only a single stage. Since it only has a single stage, it does not need a state object (its state
 * is specified as void.class).
 */
public class ServiceB {
    public static void main(String... args) {
        MatsJbangJettyServer.create(9020)
                .addMatsFactory()
                .setupUsingMatsFactory(ServiceB::setupEndpoint)
                .addMatsLocalInspect_WithRootHtml()
                .start();
    }

    static void setupEndpoint(MatsFactory matsFactory) {
        matsFactory.single("ServiceB.endpointB",
                EndpointBReplyDTO.class, EndpointBRequestDTO.class,
                (ctx, msg) -> {
                    double result = msg.a * msg.b;
                    return new EndpointBReplyDTO(result);
                });
    }

    // ====== ServiceB Request and Reply DTOs

    record EndpointBRequestDTO(double a, double b) {}

    record EndpointBReplyDTO(double result) {}
}
