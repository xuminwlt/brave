package brave.http;

import brave.Tracing;
import brave.internal.StrictCurrentTraceContext;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import java.util.concurrent.ConcurrentLinkedDeque;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import zipkin.Span;
import zipkin.internal.Util;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttp {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Rule public MockWebServer server = new MockWebServer();

  protected ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();

  protected CurrentTraceContext currentTraceContext = new StrictCurrentTraceContext();
  protected HttpTracing httpTracing;

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
        .reporter(s -> {
          // make sure the context was cleared prior to finish.. no leaks!
          TraceContext current = httpTracing.tracing().currentTraceContext().get();
          if (current != null) {
            assertThat(current.spanId())
                .isNotEqualTo(s.id);
          }
          spans.add(s);
        })
        .currentTraceContext(currentTraceContext)
        .sampler(sampler);
  }

  void assertReportedTagsInclude(String key, String... values) {
    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(key))
        .extracting(b -> new String(b.value, Util.UTF_8))
        .containsExactly(values);
  }
}
