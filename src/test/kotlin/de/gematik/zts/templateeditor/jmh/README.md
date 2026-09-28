# JMH

Benchmarking backend service for the Template Editor.

## Setup

To set up the environment for running JMH benchmarks, ensure you have the following prerequisites:

- Java Development Kit (JDK) 25 or higher
- Gradle

In addition, for the file upload benchmark please download the necessary [Loinc.zip](https://loinc.org/downloads?license_sync=1) file and place it in the `src/test/resources` directory. You need to register in order to download the Loinc.zip file.

## Benchmarks

You can run the JMH benchmarks from the command line with Gradle:

```bash
gradle jmh
```

This task runs the Java Microbenchmark Harness (JMH) benchmarks from `src/test/kotlin/de/gematik/zts/templateeditor/jmh`. 
It starts the application in a test profile, executes the benchmark scenarios against the running service, and measures their average execution time in milliseconds. 
In addition to execution time, the benchmark setup also tracks `gc` and `stack` profiler data and records a `JFR` file.

The benchmark results are written to `build/reports/jmh/results.json`.
The flight recording is saved to `build/reports/jmh/results.jfr`.