package brave.http;

import brave.SpanCustomizer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.TraceKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpParserTest {
  @Mock HttpClientAdapter<Object, Object> adapter;
  @Mock SpanCustomizer customizer;
  Object request = new Object();
  Object response = new Object();
  HttpParser parser = new HttpParser();

  @Test public void spanName_isMethod() {
    when(adapter.method(request)).thenReturn("GET");

    assertThat(parser.spanName(adapter, request))
        .isEqualTo("GET");
  }

  @Test public void request_addsPath() {
    when(adapter.path(request)).thenReturn("/foo");

    parser.request(adapter, request, customizer);

    verify(customizer).tag(TraceKeys.HTTP_PATH, "/foo");
  }

  @Test public void request_doesntCrashOnNullPath() {
    parser.request(adapter, request, customizer);

    verify(customizer, never()).tag(TraceKeys.HTTP_PATH, null);
  }

  @Test public void response_tagsStatusAndErrorOnResponseCode() {
    when(adapter.statusCode(response)).thenReturn(400);

    parser.response(adapter, response, null, customizer);

    verify(customizer).tag("http.status_code", "400");
    verify(customizer).tag("error", "400");
  }

  @Test public void response_tagsErrorFromException() {
    parser.response(adapter, response, new RuntimeException("drat"), customizer);

    verify(customizer).tag("error", "drat");
  }

  @Test public void response_tagsErrorPrefersExceptionVsResponseCode() {
    when(adapter.statusCode(response)).thenReturn(400);

    parser.response(adapter, response, new RuntimeException("drat"), customizer);

    verify(customizer).tag("error", "drat");
  }

  @Test public void response_tagsErrorOnExceptionEvenIfStatusOk() {
    when(adapter.statusCode(response)).thenReturn(200);

    parser.response(adapter, response, new RuntimeException("drat"), customizer);

    verify(customizer).tag("error", "drat");
  }
}
