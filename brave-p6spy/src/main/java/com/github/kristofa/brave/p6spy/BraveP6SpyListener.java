package com.github.kristofa.brave.p6spy;

import com.github.kristofa.brave.ClientTracer;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import zipkin.Constants;
import zipkin.TraceKeys;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.SQLException;

public final class BraveP6SpyListener extends SimpleJdbcEventListener {
    // TODO: Figure out a better approach
    static volatile ClientTracer clientTracer;

    private int ipv4;
    private int port;
    private final String serviceName;

    public BraveP6SpyListener(P6BraveOptions options) {
        try {
            InetAddress address = Inet4Address.getByName(options.getHost());
            ipv4 = ByteBuffer.wrap(address.getAddress()).getInt();
        } catch (UnknownHostException ignored) {
        }
        try {
            port = Integer.parseInt(options.getPort());
        } catch (Exception ignored) {
        }
        serviceName = options.getServiceName();
    }

    public static void setClientTracer(ClientTracer tracer) {
        clientTracer = tracer;
    }

    @Override
    public void onBeforeAnyExecute(StatementInformation statementInformation) {
        startTrace(statementInformation.getSql());
    }

    @Override
    public void onAfterAnyExecute(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
        endTrace(e);
    }

    @Override
    public void onBeforeAnyAddBatch(StatementInformation statementInformation) {
        startTrace(statementInformation.getSql());
    }

    @Override
    public void onAfterAnyAddBatch(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
        endTrace(e);
    }

    private void startTrace(String sql) {
        ClientTracer clientTracer = BraveP6SpyListener.clientTracer;
        if (clientTracer != null) {
            beginTrace(clientTracer, sql);
        }
    }

    private void endTrace(SQLException e) {
        ClientTracer clientTracer = BraveP6SpyListener.clientTracer;
        if (clientTracer != null) {
            endTrace(clientTracer, e);
        }
    }

    private void beginTrace(final ClientTracer tracer, final String sql) {
        tracer.startNewSpan("query");
        tracer.submitBinaryAnnotation(TraceKeys.SQL_QUERY, sql);

        if (ipv4 != 0 && port > 0) {
            tracer.setClientSent(ipv4, port, serviceName);
        } else { // logging the server address is optional
            tracer.setClientSent();
        }
    }

    private void endTrace(final ClientTracer tracer, final SQLException statementException) {
        try {
            if (statementException != null) {
                tracer.submitBinaryAnnotation(Constants.ERROR, statementException.getErrorCode());
            }
        } finally {
            tracer.setClientReceived();
        }
    }
}
