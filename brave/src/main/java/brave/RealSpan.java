package brave;

import brave.internal.recorder.Recorder;
import brave.propagation.TraceContext;
import com.google.auto.value.AutoValue;
import zipkin.Endpoint;

/** This wraps the public api and guards access to a mutable span. */
@AutoValue
abstract class RealSpan extends Span {

  abstract Clock clock();

  abstract Recorder recorder();

  static RealSpan create(TraceContext context, Clock clock, Recorder recorder) {
    return new AutoValue_RealSpan(context, clock, recorder);
  }

  @Override public boolean isNoop() {
    return false;
  }

  @Override public Span start() {
    return start(clock().currentTimeMicroseconds());
  }

  @Override public Span start(long timestamp) {
    recorder().start(context(), timestamp);
    return this;
  }

  @Override public Span name(String name) {
    recorder().name(context(), name);
    return this;
  }

  @Override public Span kind(Kind kind) {
    recorder().kind(context(), kind);
    return this;
  }

  @Override public Span annotate(String value) {
    return annotate(clock().currentTimeMicroseconds(), value);
  }

  @Override public Span annotate(long timestamp, String value) {
    recorder().annotate(context(), timestamp, value);
    return this;
  }

  @Override public Span tag(String key, String value) {
    recorder().tag(context(), key, value);
    return this;
  }

  @Override public Span remoteEndpoint(Endpoint remoteEndpoint) {
    recorder().remoteEndpoint(context(), remoteEndpoint);
    return this;
  }

  @Override public void finish() {
    finish(clock().currentTimeMicroseconds());
  }

  @Override public void finish(long timestamp) {
    recorder().finish(context(), timestamp);
  }

  @Override public void flush() {
    recorder().flush(context());
  }

  @Override
  public String toString() {
    return "RealSpan(" + context() + ")";
  }
}
