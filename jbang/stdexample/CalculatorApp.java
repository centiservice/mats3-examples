//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//REPOS mavencentral,LocalMaven
//DEPS io.mats3.examples:mats-examples:1.0.0

package stdexample;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import io.mats3.examples.MatsExampleJettyServer;
import io.mats3.examples.MatsExampleJettyServer.FunctionalAsyncListener;
import io.mats3.examples.MatsExampleKit;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * An "application" which employs the Mats3 Endpoint 'ServiceA.endpointA' to calculate <code>a*b - (c/d + e)</code> in
 * an obscenely complicated way! You must also have all of ServiceA to ServiceD running, along with an ActiveMQ
 * instance, to be able to get results. The application is available at HTTP port 9000, or a higher port if you start
 * multiple instances of it.
 */
public class CalculatorApp {
    public static void main(String... args) {
        MatsExampleJettyServer.create(9000)
                .addMatsFactory()
                .addMatsFuturizer()
                .addMatsLocalInspect()
                .setRootHtlm("""
                        <html><body>
                        <h1>CalculatorApp: Calculates <code>a*b - (c/d + e)</code> in an obscenely
                        complicated way!</h1>
                        <h3>LocalHtmlInspectForMatsFactory</h3>
                        <a href="localinspect">Monitoring/introspection GUI for the MatsFactory.</a><p>
                        <h3>Single, simple futurization for 'Math.PI * 4d - (5d / 6d + 7d)':</h3>
                        <a href="simple_futurization">Simple sync Servlet handling, single call.</a><p>
                        <h3>Multiple futurizations:</h3>
                        You should run this a few times to warm the Mats fabric JVMs.<p>
                        <a href="initiate_multi?count=1000">Async Servlet handling, 1000 calls.</a><br/>
                        </body></html>
                        """)
                .start();
    }

    @WebServlet("/simple_futurization")
    public static class InitiateServlet_Simple extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            var matsFuturizer = (MatsFuturizer) req.getServletContext().getAttribute(MatsFuturizer.class.getName());

            resp.getWriter().println("Sending request via MatsFuturizer.");
            resp.flushBuffer();

            // The Futurization, invoking 'ServiceA.endpointA' to do the calculation.
            var replyFuture = matsFuturizer.futurizeNonessential(
                    MatsTestHelp.traceId(), "CalculatorApp.single", "ServiceA.endpointA", EndpointAReplyDTO.class,
                    new EndpointARequestDTO(Math.PI, 4, 5, 6, 7));

            // Outputting the result
            try {
                EndpointAReplyDTO reply = replyFuture.get(30, TimeUnit.SECONDS).getReply();
                resp.getWriter().println("Got reply for 'Math.PI * 4d - (5d / 6d + 7d)': " + reply);
                boolean correct = Math.PI * 4d - (5d / 6d + 7d) == reply.result;
                resp.getWriter().println("Result is " + (correct ? "correct!" : "wrong!"));

            }
            catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new IOException("Couldn't get reply.", e);
            }
        }
    }

    @WebServlet(urlPatterns = "/initiate_multi", asyncSupported = true)
    public static class InitiateServlet_Async_Multi extends HttpServlet {
        private static final Logger log = MatsExampleKit.getClassLogger();

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            var matsFuturizer = (MatsFuturizer) req.getServletContext().getAttribute(MatsFuturizer.class.getName());

            String countP = req.getParameter("count");
            int count = countP != null ? Integer.parseInt(countP) : 10;

            PrintWriter out = resp.getWriter();
            out.println("Initiating async " + count + " concurrent futurizations. Timing at bottom.");
            out.flush();

            // :: Starting Servlet AsyncContext, which means that we can write to the output stream after the
            // Servlet thread has exited, and then 'complete' the AsyncContext when we're finished.
            AsyncContext asyncContext = req.startAsync();
            asyncContext.setTimeout(30_000);
            // .. add a AsyncListener to handle timeout.
            asyncContext.addListener(FunctionalAsyncListener.onTimeout(event -> {
                String msg = "AsyncContext timed out while waiting for [" + count + "] futures to complete.";
                out.println(msg);
                log.error(msg);
                asyncContext.complete();
            }));

            long nanosStart_Start = System.nanoTime();
            // :: Fire off all the futures
            AtomicInteger countdown = new AtomicInteger(count);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < count; i++) {
                double a = random.nextDouble(-100d, 100d);
                double b = random.nextDouble(-100d, 100d);
                double c = random.nextDouble(-100d, 100d);
                double d = random.nextDouble(-100d, 100d);
                double e = random.nextDouble(-100d, 100d);
                long nanosStart_futurization = System.nanoTime();
                var replyFuture = matsFuturizer.futurizeNonessential(MatsTestHelp.traceId(),
                        "CalculatorApp.multi", "ServiceA.endpointA", EndpointAReplyDTO.class,
                        new EndpointARequestDTO(a, b, c, d, e));

                // Handle the reply via thenAccept, which will run on the futurizer completer thread pool.
                replyFuture.thenAccept(reply -> {
                    long nanosAt_Reply = System.nanoTime();
                    EndpointAReplyDTO replyDto = reply.getReply();
                    // Write to the Servlet output stream (which is synchronized)
                    boolean correct = a * b - (c / d + e) == replyDto.result;
                    out.println("Result: " + replyDto + (correct ? " Correct!" : " Wrong!")
                            + ", millis taken: " + ((nanosAt_Reply - nanosStart_futurization) / 1_000_000d)
                            + ", millis since start: " + ((nanosAt_Reply - nanosStart_Start) / 1_000_000d));
                    // Count down, and if we hit 0, then complete Servlet AsyncContext.
                    int current = countdown.decrementAndGet();
                    if (current == 0) {
                        outputStats(out, count, nanosStart_Start);
                        asyncContext.complete();
                    }
                });
            }

            // We're done, Servlet thread exits, the result will be provided async by futurizer completer thread.
            log.info("!!! Exiting Servlet, time taken: " + (System.nanoTime() - nanosStart_Start) / 1_000_000d);
        }
    }

    private static void outputStats(PrintWriter out, int count, long nanosStart_Start) {
        double msTaken_SinceStart = (System.nanoTime() - nanosStart_Start) / 1_000_000d;
        out.println("## Total millis taken: " + msTaken_SinceStart
                + ", req/sec: " + (count / (msTaken_SinceStart / 1000d)));
    }

    // ====== Imported DTOs for ServiceA

    record EndpointARequestDTO(double a, double b, double c, double d, double e) {}

    record EndpointAReplyDTO(double result) {}
}
