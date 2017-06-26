package com.github.kristofa.brave;


/**
 * Empty implementation ignoring all events.
 * @deprecated Replaced by {@code zipkin.reporter.ReporterMetrics#NOOP_METRICS}
 */
@Deprecated
public class EmptySpanCollectorMetricsHandler implements SpanCollectorMetricsHandler {

    @Override
    public void incrementAcceptedSpans(int quantity) {

    }

    @Override
    public void incrementDroppedSpans(int quantity) {

    }
}
