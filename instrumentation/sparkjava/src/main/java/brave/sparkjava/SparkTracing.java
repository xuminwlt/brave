package brave.sparkjava;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import brave.servlet.HttpServletAdapter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import spark.ExceptionHandler;
import spark.Filter;
import spark.Request;

public final class SparkTracing {
  public static SparkTracing create(Tracing tracing) {
    return new SparkTracing(HttpTracing.create(tracing));
  }

  public static SparkTracing create(HttpTracing httpTracing) {
    return new SparkTracing(httpTracing);
  }

  final Tracer tracer;
  final HttpServerHandler<HttpServletRequest, HttpServletResponse> handler;
  final TraceContext.Extractor<Request> extractor;

  SparkTracing(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, new HttpServletAdapter());
    extractor = httpTracing.tracing().propagation().extractor(Request::headers);
  }

  public Filter before() {
    return (request, response) -> {
      Span span = handler.handleReceive(extractor, request, request.raw());
      request.attribute(Tracer.SpanInScope.class.getName(), tracer.withSpanInScope(span));
    };
  }

  public Filter afterAfter() {
    return (request, response) -> {
      Span span = tracer.currentSpan();
      if (span == null) return;
      ((Tracer.SpanInScope) request.attribute(Tracer.SpanInScope.class.getName())).close();
      handler.handleSend(response.raw(), null, span);
    };
  }

  public ExceptionHandler exception(ExceptionHandler delegate) {
    return (exception, request, response) -> {
      Span span = tracer.currentSpan();
      if (span != null) {
        ((Tracer.SpanInScope) request.attribute(Tracer.SpanInScope.class.getName())).close();
        handler.handleSend(null, exception, span);
      }
      delegate.handle(exception, request, response);
    };
  }
}
