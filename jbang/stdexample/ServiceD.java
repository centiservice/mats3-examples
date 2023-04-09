//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//REPOS mavencentral,LocalMaven
//DEPS io.mats3.examples:mats-examples:RC0-1.0.0

package stdexample;

import io.mats3.MatsFactory;
import io.mats3.examples.MatsExampleJettyServer;

/** Mats single-stage Endpoint which calculates <code>a/b</code>. */
public class ServiceD {
    public static void main(String... args) {
        MatsExampleJettyServer.create(9040)
                .addMatsFactory()
                .setupUsingMatsFactory(ServiceD::setupEndpoint)
                .addMatsLocalInspect_WithRootHtml()
                .start();
    }

    static void setupEndpoint(MatsFactory matsFactory) {
        matsFactory.single("ServiceD.endpointD",
                EndpointDReplyDTO.class, EndpointDRequestDTO.class,
                (ctx, msg) -> {
                    double result = msg.a / msg.b;
                    return new EndpointDReplyDTO(result);
                });
    }

    // ====== ServiceD Request and Reply DTOs

    record EndpointDRequestDTO(double a, double b) {}

    record EndpointDReplyDTO(double result) {}

}
