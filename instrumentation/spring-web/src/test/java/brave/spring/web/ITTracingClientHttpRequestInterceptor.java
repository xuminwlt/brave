package brave.spring.web;

import brave.http.ITHttpClient;
import java.util.Arrays;
import java.util.Collections;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingClientHttpRequestInterceptor extends ITHttpClient<ClientHttpRequestFactory> {

  ClientHttpRequestInterceptor interceptor;

  ClientHttpRequestFactory configureClient(ClientHttpRequestInterceptor interceptor) {
    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
    factory.setReadTimeout(1000);
    factory.setConnectTimeout(1000);
    this.interceptor = interceptor;
    return factory;
  }

  @Override protected ClientHttpRequestFactory newClient(int port) {
    return configureClient(TracingClientHttpRequestInterceptor.create(httpTracing));
  }

  @Override protected void closeClient(ClientHttpRequestFactory client) {
    ((HttpComponentsClientHttpRequestFactory) client).destroy();
  }

  @Override protected void get(ClientHttpRequestFactory client, String pathIncludingQuery)
      throws Exception {
    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.getForObject(url(pathIncludingQuery), String.class);
  }

  @Override protected void post(ClientHttpRequestFactory client, String uri, String content) {
    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.postForObject(url(uri), content, String.class);
  }

  @Override protected void getAsync(ClientHttpRequestFactory client, String uri) {
    throw new AssumptionViolatedException("TODO: async rest template has its own interceptor");
  }

  @Test public void currentSpanVisibleToUserInterceptors() throws Exception {
    server.enqueue(new MockResponse());

    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Arrays.asList(interceptor, (request, body, execution) -> {
      request.getHeaders()
          .add("my-id", currentTraceContext.get().traceIdString());
      return execution.execute(request, body);
    }));
    restTemplate.getForObject(server.url("/foo").toString(), String.class);

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
        .isEqualTo(request.getHeader("my-id"));
  }

  @Override @Test(expected = AssertionError.class)
  public void redirect() throws Exception { // blind to the implementation of redirects
    super.redirect();
  }

  @Override @Test(expected = AssertionError.class)
  public void reportsServerAddress() throws Exception { // doesn't know the remote address
    super.reportsServerAddress();
  }
}
