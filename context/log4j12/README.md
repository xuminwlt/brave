# brave-context-log4j12
This adds trace and span IDs to the Log4J v1.2 Mapped Diagnostic Context so that you
can search or aggregate logs accordingly.

To enable this, configure `brave.Tracing` with `MDCCurrentTraceContext`
like so:

```java
tracing = Tracing.newBuilder()
    .currentTraceContext(MDCCurrentTraceContext.create())
    ...
    .build();
```

Then, in your log configuration, you can use `traceId` and or `spanId`.

Here's an example log4j.properties pattern:

```xml
appender.console.layout.ConversionPattern = %d{ABSOLUTE} [%X{traceId}/%X{spanId}] %-5p [%t] %C{2} (%F:%L) - %m%n
```

When a trace is in progress, it would log statements like this:
```
11:01:29,799 [e2ffceb485bdfb1d/e2ffceb485bdfb1d] INFO  [main] c.a.FooController (FooController.java:30) - I got here!
```

Users could then copy/paste the trace ID into the zipkin UI, or use log
correlation to further debug a problem.
