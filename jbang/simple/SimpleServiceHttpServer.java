//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS io.mats3.examples:mats-jbangkit:1.0.0

package simple;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import io.mats3.examples.jbang.MatsJbangJettyServer;
import io.mats3.examples.jbang.MatsJbangJettyServer.FunctionalAsyncListener;
import io.mats3.examples.jbang.MatsJbangKit;
import io.mats3.test.MatsTestHelp;
import io.mats3.util.MatsFuturizer;
import io.mats3.util.MatsFuturizer.Reply;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fires up a Jetty instance, and creates a few Servlets: Two using ordinary sync Servlet handling, the last async - all
 * uses the {@link MatsFuturizer} to fire off messages, getting an async Future in return. Depends on localhost ActiveMQ
 * and the {@link SimpleService} running.
 */
public class SimpleServiceHttpServer {
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
                        <h3>Multiple futurizations:</h3>
                        You should run these a few times to warm the Mats fabric JVMs.<p>
                        <a href="initiate_sync_multi?count=1000">Sync Servlet handling, 1000 calls.</a><br/>
                        <a href="initiate_async_multi?count=1000">Async Servlet handling, 1000 calls.</a><br/>
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

    // ----- More complex Servlets, firing of multiple futures, sync and async, with timing and timeout handling.

    @WebServlet("/initiate_sync_multi")
    public static class InitiateServlet_Sync_Multi extends HttpServlet {
        private static final Logger log = MatsJbangKit.getClassLogger();

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            var matsFuturizer = (MatsFuturizer) req.getServletContext().getAttribute(MatsFuturizer.class.getName());

            String countP = req.getParameter("count");
            int count = countP != null ? Integer.parseInt(countP) : 10;

            PrintWriter out = resp.getWriter();
            out.println("Initiating sync " + count + " concurrent futurizations. Timing at bottom.");
            out.flush();

            CountDownLatch countDownLatch = new CountDownLatch(count);

            long nanosStart_Start = System.nanoTime();
            // :: Fire off all the futures
            for (int i = 0; i < count; i++) {
                long nanosStart_futurization = System.nanoTime();
                var replyFuture = matsFuturizer.futurizeNonessential(MatsTestHelp.traceId(),
                        "TestJettyServer", "SimpleService.simple", SimpleServiceReplyDto.class,
                        new SimpleServiceRequestDto(i, "#of " + count + "#"));

                // Handle the reply via thenAccept, which will run on the futurizer completer thread pool.
                // Could also have used future.get() to do this fully synchronous.
                replyFuture.thenAccept(reply -> {
                    long nanosAt_Reply = System.nanoTime();
                    SimpleServiceReplyDto replyDto = reply.getReply();
                    out.println("Result: " + replyDto + ", millis taken: "
                            + ((nanosAt_Reply - nanosStart_futurization) / 1_000_000d)
                            + ", millis since start: " + ((nanosAt_Reply - nanosStart_Start) / 1_000_000d));
                    // :: Count down, so that Servlet thread can continue and exit.
                    countDownLatch.countDown();
                });
            }

            // :: Waiting to all futurizations have gone through.
            try {
                boolean countedDown = countDownLatch.await(30, TimeUnit.SECONDS);
                if (!countedDown) {
                    String msg = "Timed out synchronously waiting for [" + count + "] futures.";
                    out.println(msg);
                    log.error(msg);
                    return;
                }
            }
            catch (InterruptedException e) {
                throw new IOException("Interrupted.");
            }

            outputStats(out, count, nanosStart_Start);

            // We're done, exit servlet - all results gotten and written sync, the servlet thread is now released.
            log.info("!!! Exiting Servlet, time taken: " + ((System.nanoTime() - nanosStart_Start) / 1_000_000d));
        }
    }

    @WebServlet(urlPatterns = "/initiate_async_multi", asyncSupported = true)
    public static class InitiateServlet_Async_Multi extends HttpServlet {
        private static final Logger log = MatsJbangKit.getClassLogger();

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
            for (int i = 0; i < count; i++) {
                long nanosStart_futurization = System.nanoTime();
                var replyFuture = matsFuturizer.futurizeNonessential(MatsTestHelp.traceId(),
                        "TestJettyServer", "SimpleService.simple", SimpleServiceReplyDto.class,
                        new SimpleServiceRequestDto(i, "#of " + count + "#"));

                // Handle the reply via thenAccept, which will run on the futurizer completer thread pool.
                replyFuture.thenAccept(reply -> {
                    long nanosAt_Reply = System.nanoTime();
                    SimpleServiceReplyDto replyDto = reply.getReply();
                    // Write to the Servlet output stream (which is synchronized)
                    out.println("Result: " + replyDto + ", millis taken: "
                            + ((nanosAt_Reply - nanosStart_futurization) / 1_000_000d)
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

    // ----- Contract copied from SimpleService

    record SimpleServiceRequestDto(int number, String string) {
    }

    record SimpleServiceReplyDto(String result, int numChars) {
    }
}
