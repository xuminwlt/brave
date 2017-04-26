# Deprecation Notice
Brave was redesigned starting with version 4. Please see the
[README](../brave/README.md) for details on how to use Brave's Tracer.

# Brave Api (version 3)
brave-core contains the core brave implementations used to set up and keep track of
tracing state. 

Since brave 3.0.0 it is recommended to use:
 
   * At client side:
      * `com.github.kristofa.brave.ClientRequestInterceptor` : Starts a new span for a new outgoing request. Submits cs annotation.
      * `com.github.kristofa.brave.ClientResponseInterceptor` : Deals with response from request. Submits cr annotation.
   * At server side:
      * `com.github.kristofa.brave.ServerRequestInterceptor` : Intercepts incoming requests. Submits sr annotation.
      * `com.github.kristofa.brave.ServerResponseInterceptor` : Intercepts response for request. Submits ss annotation.

For each of these classes you will have to implement and adapter that will deal with platform specifics.

These interceptors use the `ClientTracer` and `ServerTracer` which was the way to integrate with brave 2.x
The interceptors are higher level and easier to use. In fact the common logic which was with brave 2.x implemented
over and over with each integration is now implemented once in the interceptors.

Because http integration is very common there is a separate module: `brave-http` which builds upon `brave-core`.
You should check this out of you want to do http integrations.

## instantiating brave api

```java
final Brave.Builder builder = new Brave.Builder();
final Brave brave = builder
  .spanCollector(aSpanCollector)
  .sampler(Sampler.create(0.1f)) // retain 10% of traces
  .build();
  
// Creates different interceptors  
brave.clientRequestInterceptor();
brave.clientResponseInterceptor();
brave.serverRequestInterceptor();
brave.serverResponseInterceptor(); 
```

As you can see in above example, Since brave 3.0 the way to instantiate the api has changed. 
We use a Builder now that lets you configure custom:

   * SpanCollector. Default value = `LoggingSpanCollector`
   * Sampler. Default value is to send every trace.
   * ServerAndClientSpanState. Default value is `ThreadLocalServerAndClientSpanState`.

Once the `Brave` object is created you can get the different interceptors. 

### SpanCollector ###

The `SpanCollector` will receive all spans and can do with them whatever needed. You can create your own
implementations but have some implementations that you can use:

   * `LoggingSpanCollector` : Part of brave-core. This implementation will simply log the spans using 'java.util.Logger' (INFO log level).
   * `EmptySpanCollector` : Part of brave-core. Does nothing.
   * `ZipkinSpanCollector` : Part of `brave-zipkin-spancollector` module. Span collector that supports sending spans directly to `zipkin-collector` service or Scribe.

### Sampler ###

You might not want to trace all requests that are being submitted:

   * to avoid performance overhea
   * to avoid running out of storage

and you don't need to trace all requests to come to usable data.

A Sampler's purpose (`com.github.kristofa.brave.Sampler`) is to decide if a given
request should get traced or not.

The decision, should trace (true) or not (false) is taken by the first request (client side or server side) and should
be passed through to all subsequent requests. This has as a consequence that we either
trace a full request tree or none of the requests at all which is good. We don't want incomplete traces.

Sampler has a factory method that accepts a a fixed sample rate as a percentage. The
sample rate can't be adapted at run time.  Behaviour:

*   sample rate 0.0f : Non of the requests will be traced. Means tracing is disabled
*   sample rate 1.0f : All requests will be traced.
*   sample rate (0.0, 1.0) : For example 0.3f, 30% of requests will be traced.

If you want to use a Sampler implementation which allows adapting sample rate at run
time see `brave-sampler-zookeeper` project which contains a Sampler with ZooKeeper support.



## brave and multi threading ##

In its default configuration Brave uses ThreadLocal variables to keep track of trace/span state 
(`ThreadLocalServerAndClientSpanState`). By doing this it is
able to support tracking state for multiple requests in the model where 1 request = 1 thread.

However when you start a new Thread yourself from a request Thread brave will lose its trace/span state.
To bind the same state to your new Thread you can use the `ServerSpanThreadBinder` accessible through `Brave`or 
you can use `com.github.kristofa.brave.BraveExecutorService`.

BraveExecutorService implements the `java.util.concurrent.ExecutorService` interface and acts as a decorator for
an existing `ExecutorService`.  It also uses the `ServerSpanThreadBinder` which it takes in its constructor but
once set up if is transparent for your code and will make sure any thread you start through the ExecutorService
will get proper trace/span state.

Instead of using `BraveExecutorService` or the `ServerSpanThreadBinder` directly you can also
use the `BraveCallable` and `BraveRunnable`. These are used internally by the BraveExecutorService.

## 128-bit trace IDs

Traditionally, Zipkin trace IDs were 64-bit. Starting with Zipkin 1.14,
128-bit trace identifiers are supported. This can be useful in sites that
have very large traffic volume, persist traces forever, or are re-using
externally generated 128-bit IDs.

If you want Brave to generate 128-bit trace identifiers when starting new
root spans, set `Brave.Builder.traceId128Bit(true)`

When 128-bit trace ids are propagated, they will be twice as long as
before. For example, the `X-B3-TraceId` header will hold a 32-character
value like `163ac35c9f6413ad48485a3953bb6124`.

Before doing this, ensure your Zipkin setup is up-to-date, and downstream
instrumented services can read the longer 128-bit trace IDs.

Note: this only affects the trace ID, not span IDs. For example, span ids
within a trace are always 64-bit. 
