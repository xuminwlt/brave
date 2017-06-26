package brave.httpasyncclient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.assertj.core.api.AbstractObjectAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingHttpAsyncClientBuilder_HttpAdapterTest {
  TracingHttpAsyncClientBuilder.HttpAdapter adapter =
      new TracingHttpAsyncClientBuilder.HttpAdapter();
  @Mock HttpRequestWrapper request;

  @Test public void parseServerAddress_skipsOnNoop() {
    assertThat(adapter.parseServerAddress(request, Endpoint.builder()))
        .isFalse();
  }

  @Test public void parseServerAddress_prefersAddress() throws UnknownHostException {
    when(request.getTarget()).thenReturn(new HttpHost(InetAddress.getByName("127.0.0.1")));

    assertParsedEndpoint()
        .isEqualTo(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseServerAddress_acceptsHostname() {
    when(request.getTarget()).thenReturn(new HttpHost("127.0.0.1"));

    assertParsedEndpoint()
        .isEqualTo(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseServerAddress_ipAndPortFromHost() {
    when(request.getTarget()).thenReturn(new HttpHost("127.0.0.1", 9999));

    assertParsedEndpoint()
        .isEqualTo(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).port(9999).build());
  }

  @Test public void parseServerAddress_doesntNsLookup() {
    when(request.getTarget()).thenReturn(new HttpHost("localhost"));

    assertThat(adapter.parseServerAddress(request, Endpoint.builder()))
        .isFalse();
  }

  AbstractObjectAssert<?, Endpoint> assertParsedEndpoint() {
    Endpoint.Builder remoteAddress = Endpoint.builder();
    assertThat(adapter.parseServerAddress(request, remoteAddress))
        .isTrue();

    return assertThat(remoteAddress.serviceName("").build());
  }
}
