package brave.httpclient;

import brave.http.ITHttpClient;
import java.io.IOException;
import java.net.URI;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingHttpClientBuilder extends ITHttpClient<CloseableHttpClient> {

  @Override protected CloseableHttpClient newClient(int port) {
    return TracingHttpClientBuilder.create(httpTracing).disableAutomaticRetries().build();
  }

  @Override protected void closeClient(CloseableHttpClient client) throws IOException {
    client.close();
  }

  @Override protected void get(CloseableHttpClient client, String pathIncludingQuery)
      throws IOException {
    client.execute(new HttpGet(URI.create(url(pathIncludingQuery)))).close();
  }

  @Override protected void post(CloseableHttpClient client, String pathIncludingQuery, String body)
      throws Exception {
    HttpPost post = new HttpPost(URI.create(url(pathIncludingQuery)));
    post.setEntity(new StringEntity(body));
    client.execute(post).close();
  }

  @Override protected void getAsync(CloseableHttpClient client, String pathIncludingQuery) {
    throw new AssumptionViolatedException("This is not an async library");
  }

  @Test public void currentSpanVisibleToUserFilters() throws Exception {
    server.enqueue(new MockResponse());
    closeClient(client);

    client = TracingHttpClientBuilder.create(httpTracing).disableAutomaticRetries()
        .addInterceptorFirst((HttpRequestInterceptor) (request, context) ->
            request.setHeader("my-id", currentTraceContext.get().traceIdString())
        ).build();

    get(client, "/foo");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
        .isEqualTo(request.getHeader("my-id"));
  }
}
