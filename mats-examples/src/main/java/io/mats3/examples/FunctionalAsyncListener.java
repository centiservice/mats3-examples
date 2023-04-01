package io.mats3.examples;

import java.io.IOException;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;

/**
 * Adapt the Servlet {@link AsyncListener} to a bit more modern-looking {@link FunctionalInterface}.
 *
 * @author Endre Stølsvik 2023-03-30 22:48 - http://stolsvik.com/, endre@stolsvik.com
 */
@FunctionalInterface
public interface FunctionalAsyncListener {

    void event(AsyncEvent asyncEvent) throws IOException;

    static AsyncListener onStartAsync(FunctionalAsyncListener startAsyncListener) {
        return new AsyncListener() {
            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {
                startAsyncListener.event(event);
            }

            @Override
            public void onComplete(AsyncEvent event) {
            }

            @Override
            public void onTimeout(AsyncEvent event) {
            }

            @Override
            public void onError(AsyncEvent event) {
            }
        };
    }

    static AsyncListener onComplete(FunctionalAsyncListener timeoutListener) {
        return new AsyncListener() {
            @Override
            public void onStartAsync(AsyncEvent event) {
            }

            @Override
            public void onComplete(AsyncEvent event) throws IOException {
                timeoutListener.event(event);
            }

            @Override
            public void onTimeout(AsyncEvent event) {
            }

            @Override
            public void onError(AsyncEvent event) {
            }
        };
    }

    static AsyncListener onTimeout(FunctionalAsyncListener timeoutListener) {
        return new AsyncListener() {
            @Override
            public void onStartAsync(AsyncEvent event) {
            }

            @Override
            public void onComplete(AsyncEvent event) {
            }

            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                timeoutListener.event(event);
            }

            @Override
            public void onError(AsyncEvent event) {
            }
        };
    }

    static AsyncListener onError(FunctionalAsyncListener timeoutListener) {
        return new AsyncListener() {
            @Override
            public void onStartAsync(AsyncEvent event) {
            }

            @Override
            public void onComplete(AsyncEvent event) {
            }

            @Override
            public void onTimeout(AsyncEvent event) {
            }

            @Override
            public void onError(AsyncEvent event) throws IOException {
                timeoutListener.event(event);
            }
        };
    }
}
