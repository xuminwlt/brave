package brave.httpasyncclient;

import brave.Span;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import java.io.IOException;
import java.util.concurrent.Future;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import zipkin.Endpoint;

/**
 * Note: The current span is only visible to interceptors {@link #addInterceptorLast(HttpRequestInterceptor)
 * added last}.
 */
public final class TracingHttpAsyncClientBuilder extends HttpAsyncClientBuilder {

  public static HttpAsyncClientBuilder create(Tracing tracing) {
    return new TracingHttpAsyncClientBuilder(HttpTracing.create(tracing));
  }

  public static HttpAsyncClientBuilder create(HttpTracing httpTracing) {
    return new TracingHttpAsyncClientBuilder(httpTracing);
  }

  final CurrentTraceContext currentTraceContext;
  final TraceContext.Injector<HttpMessage> injector;
  final HttpClientHandler<HttpRequest, HttpResponse> handler;

  TracingHttpAsyncClientBuilder(HttpTracing httpTracing) { // intentionally hidden
    if (httpTracing == null) throw new NullPointerException("httpTracing == null");
    this.currentTraceContext = httpTracing.tracing().currentTraceContext();
    this.handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
    this.injector = httpTracing.tracing().propagation().injector(HttpMessage::setHeader);
  }

  @Override public CloseableHttpAsyncClient build() {
    super.addInterceptorFirst((HttpRequestInterceptor) (request, context) -> {
      HttpHost host = HttpClientContext.adapt(context).getTargetHost();

      TraceContext parent = (TraceContext) context.getAttribute(TraceContext.class.getName());
      Span span;
      try (Scope scope = currentTraceContext.newScope(parent)) {
        span = handler.handleSend(injector, request, HttpRequestWrapper.wrap(request, host));
      }

      context.setAttribute(Span.class.getName(), span);
      context.setAttribute(Scope.class.getName(), currentTraceContext.newScope(span.context()));
    });
    super.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
      Scope scope = (Scope) context.getAttribute(Scope.class.getName());
      if (scope != null) {
        context.removeAttribute(Scope.class.getName());
        scope.close();
      }
    });
    super.addInterceptorLast((HttpResponseInterceptor) (response, context) -> {
      Span span = (Span) context.getAttribute(Span.class.getName());
      handler.handleReceive(response, null, span);
    });
    return new TracingHttpAsyncClient(super.build());
  }

  static final class HttpAdapter extends brave.http.HttpClientAdapter<HttpRequest, HttpResponse> {

    @Override public boolean parseServerAddress(HttpRequest httpRequest, Endpoint.Builder builder) {
      if (!(httpRequest instanceof HttpRequestWrapper)) return false;
      HttpHost target = ((HttpRequestWrapper) httpRequest).getTarget();
      if (target == null) return false;
      if (builder.parseIp(target.getAddress()) || builder.parseIp(target.getHostName())) {
        builder.port(target.getPort());
        return true;
      }
      return false;
    }

    @Override public String method(HttpRequest request) {
      return request.getRequestLine().getMethod();
    }

    @Override public String path(HttpRequest request) {
      String result = request.getRequestLine().getUri();
      int queryIndex = result.indexOf('?');
      return queryIndex == -1 ? result : result.substring(0, queryIndex);
    }

    @Override public String url(HttpRequest request) {
      if (request instanceof HttpRequestWrapper) {
        HttpRequestWrapper wrapper = (HttpRequestWrapper) request;
        HttpHost target = wrapper.getTarget();
        if (target != null) return target.toURI() + wrapper.getURI();
      }
      return request.getRequestLine().getUri();
    }

    @Override public String requestHeader(HttpRequest request, String name) {
      Header result = request.getFirstHeader(name);
      return result != null ? result.getValue() : null;
    }

    @Override public Integer statusCode(HttpResponse response) {
      return response.getStatusLine().getStatusCode();
    }
  }

  final class TracingHttpAsyncClient extends CloseableHttpAsyncClient {
    private final CloseableHttpAsyncClient delegate;

    TracingHttpAsyncClient(CloseableHttpAsyncClient delegate) {
      this.delegate = delegate;
    }

    @Override public <T> Future<T> execute(HttpAsyncRequestProducer requestProducer,
        HttpAsyncResponseConsumer<T> responseConsumer, HttpContext context,
        FutureCallback<T> callback) {
      context.setAttribute(TraceContext.class.getName(), currentTraceContext.get());
      return delegate.execute(
          new TracingAsyncRequestProducer(requestProducer, context),
          new TracingAsyncResponseConsumer<>(responseConsumer, context),
          context,
          callback
      );
    }

    @Override public void close() throws IOException {
      delegate.close();
    }

    @Override public boolean isRunning() {
      return delegate.isRunning();
    }

    @Override public void start() {
      delegate.start();
    }
  }

  final class TracingAsyncRequestProducer implements HttpAsyncRequestProducer {
    final HttpAsyncRequestProducer requestProducer;
    final HttpContext context;

    TracingAsyncRequestProducer(HttpAsyncRequestProducer requestProducer, HttpContext context) {
      this.requestProducer = requestProducer;
      this.context = context;
    }

    @Override public void close() throws IOException {
      requestProducer.close();
    }

    @Override public HttpHost getTarget() {
      return requestProducer.getTarget();
    }

    @Override public HttpRequest generateRequest() throws IOException, HttpException {
      return requestProducer.generateRequest();
    }

    @Override public void produceContent(ContentEncoder encoder, IOControl io) throws IOException {
      requestProducer.produceContent(encoder, io);
    }

    @Override public void requestCompleted(HttpContext context) {
      requestProducer.requestCompleted(context);
    }

    @Override public void failed(Exception ex) {
      Span currentSpan = (Span) context.getAttribute(Span.class.getName());
      if (currentSpan != null) {
        context.removeAttribute(Span.class.getName());
        handler.handleReceive(null, ex, currentSpan);
      }
      requestProducer.failed(ex);
    }

    @Override public boolean isRepeatable() {
      return requestProducer.isRepeatable();
    }

    @Override public void resetRequest() throws IOException {
      requestProducer.resetRequest();
    }
  }

  final class TracingAsyncResponseConsumer<T> implements HttpAsyncResponseConsumer<T> {
    final HttpAsyncResponseConsumer<T> responseConsumer;
    final HttpContext context;

    TracingAsyncResponseConsumer(HttpAsyncResponseConsumer<T> responseConsumer,
        HttpContext context) {
      this.responseConsumer = responseConsumer;
      this.context = context;
    }

    @Override public void responseReceived(HttpResponse response)
        throws IOException, HttpException {
      responseConsumer.responseReceived(response);
    }

    @Override public void consumeContent(ContentDecoder decoder, IOControl ioctrl)
        throws IOException {
      responseConsumer.consumeContent(decoder, ioctrl);
    }

    @Override public void responseCompleted(HttpContext context) {
      responseConsumer.responseCompleted(context);
    }

    @Override public void failed(Exception ex) {
      Span currentSpan = (Span) context.getAttribute(Span.class.getName());
      if (currentSpan != null) {
        context.removeAttribute(Span.class.getName());
        handler.handleReceive(null, ex, currentSpan);
      }
      responseConsumer.failed(ex);
    }

    @Override public Exception getException() {
      return responseConsumer.getException();
    }

    @Override public T getResult() {
      return responseConsumer.getResult();
    }

    @Override public boolean isDone() {
      return responseConsumer.isDone();
    }

    @Override public void close() throws IOException {
      responseConsumer.close();
    }

    @Override public boolean cancel() {
      return responseConsumer.cancel();
    }
  }
}
