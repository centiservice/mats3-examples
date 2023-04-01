# Mats<sup>3</sup>-examples using JBang!

<a href="https://jbang.dev/">JBang</a> is a powerful "Java single file direct run" solution, which compared to Java's
own solution, also handles dependencies and Java versions.

This project aims to make it dead simple to play around and experiment with the
<a href="https://mats3.io/">Mats<sup>3</sup></a> library.

This project has two pieces:

1. A Maven-published project which includes "MatsExampleKit" and "MatsExampleJettyServer", to simply, with very few
   lines, get hold of the boilerplate components you need to experiment.
    * This project also depends on all the dependencies you need, so that the JBang file only requires a single
      dependency line
2. A few sets of JBang files that demonstrates some basic aspects of Mats<sup>3</sup>.
    * Also included is a single-line way to get a running localhost ActiveMQ instance.