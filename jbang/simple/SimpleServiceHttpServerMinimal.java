//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:RC1-1.0.0

package simple;

import io.mats3.examples.jbang.MatsJbangJettyServer;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import io.mats3.util.MatsFuturizer.Reply;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SimpleServiceHttpServerMinimal {
    public static void main(String... args) {
        MatsJbangJettyServer.create(8080)
                .addMatsFactory() // Creates a MatsFactory, using appName = calling class.
                .addMatsFuturizer() // Creates a MatsFuturizer, using the ServletContext MatsFactory
                .addMatsLocalInspect() // Includes 'localinspect' local MatsFactory Monitor
                .setRootHtlm("""
                        <html><body>
                        <h1>Basic Servlet MatsFuturizer Example, sync and async, sequentially issued</h1>
                        <h3>LocalHtmlInspectForMatsFactory</h3>
                        <a href="localinspect">Monitoring/introspection GUI for the MatsFactory.</a><p>
                        <h3>Single, simple futurization:</h3>
                        <a href="initiate_simple">Simple sync Servlet handling, single call.</a><p>
                        </body></html>
                        """)
                .start();
    }

    // ----- Simple Servlet doing a single Mats futurization, no timings.

    @WebServlet("/initiate_simple")
    public static class InitiateServlet_Simple extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            var matsFuturizer = (MatsFuturizer) req.getServletContext().getAttribute(MatsFuturizer.class.getName());

            // The Futurization
            var replyFuture = matsFuturizer.futurizeNonessential(
                    MatsTestHelp.traceId(), "TestJettyServer", "SimpleService.simple", SimpleServiceReplyDto.class,
                    new SimpleServiceRequestDto(42, "teststring"));

            // Outputting the result
            try {
                Reply<SimpleServiceReplyDto> futureReply = replyFuture.get(30, TimeUnit.SECONDS);
                resp.getWriter().println("Got reply: " + futureReply.getReply());
            }
            catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new IOException("Couldn't get reply.", e);
            }
        }
    }

    // ----- Contract copied from SimpleService

    record SimpleServiceRequestDto(int number, String string) {
    }

    record SimpleServiceReplyDto(String result, int numChars) {
    }
}
