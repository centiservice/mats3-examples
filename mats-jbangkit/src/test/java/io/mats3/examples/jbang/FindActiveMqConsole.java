package io.mats3.examples.jbang;

import java.net.URL;

import org.slf4j.Logger;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;
import io.mats3.test.MatsTestHelp;

/**
 * Dummy file just to keep the code for finding the Console WAR file. This works, but I didn't find a way to install it
 * into the Jetty server as a WAR that worked. It was the JSP code in the Console that wouldn't work.
 */
public class FindActiveMqConsole {
    private static final Logger log = MatsTestHelp.getClassLogger();

    public static URL getActiveMqConsoleWarFile() {
        long nanosAtStart_findingUrl = System.nanoTime();
        try (ScanResult result = new ClassGraph()
                .acceptJars("activemq-web-console*.war")
                .disableNestedJarScanning()
                // .verbose() // Log to stderr
                .enableAllInfo() // Scan classes, methods, fields, annotations
                .scan()) { // Start the scan
            ResourceList resourceList = result.getResourcesWithLeafName("index.jsp");
            log.info("Size: " + resourceList.getURLs().size());
            if (resourceList.getURLs().isEmpty()) {
                throw new IllegalStateException("dammit");
            }
            URL url = resourceList.get(1).getClasspathElementURL();

            double msTaken = (System.nanoTime() - nanosAtStart_findingUrl) / 1_000_000d;

            log.info("url: " + url + ", ms taken: " + msTaken);

            return url;
        }
    }
}
