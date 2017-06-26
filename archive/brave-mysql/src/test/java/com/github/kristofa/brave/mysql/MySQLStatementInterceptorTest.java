package com.github.kristofa.brave.mysql;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.github.kristofa.brave.ClientTracer;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;
import com.mysql.jdbc.ResultSetInternalMethods;
import com.mysql.jdbc.Statement;

import com.twitter.zipkin.gen.Endpoint;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import zipkin.Constants;
import zipkin.TraceKeys;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Properties;

public class MySQLStatementInterceptorTest {

    private final ClientTracer clientTracer = mock(ClientTracer.class);
    private MySQLStatementInterceptor subject;

    @Before
    public void setUp() throws Exception {
        subject = new MySQLStatementInterceptor();
        MySQLStatementInterceptor.setClientTracer(clientTracer);
    }

    @Test
    public void preProcessShouldNotFailIfNoClientTracer() throws Exception {
        MySQLStatementInterceptor.setClientTracer(null);

        assertNull(subject.preProcess("sql", mock(Statement.class), mock(Connection.class)));

        verifyZeroInteractions(clientTracer);
    }

    @Test
    public void preProcessShouldBeginTracingSQLCall() throws Exception {
        final String sql = randomAlphanumeric(20);

        final Connection connection = mock(Connection.class);

        assertNull(subject.preProcess(sql, mock(Statement.class), connection));

        final InOrder order = inOrder(clientTracer);

        order.verify(clientTracer).startNewSpan("query");
        order.verify(clientTracer).submitBinaryAnnotation(eq(TraceKeys.SQL_QUERY), eq(sql));
        order.verify(clientTracer).setClientSent();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void preProcessShouldLogServerAddress() throws Exception {
        final String sql = randomAlphanumeric(20);
        final String schema = randomAlphanumeric(20);

        final Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(schema);
        when(connection.getHost()).thenReturn("1.2.3.4");
        final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:mysql://foo:9999/test");
        
        Properties props = new Properties();
        when(connection.getProperties()).thenReturn(props);
        when(connection.getCatalog()).thenReturn("test");

        subject.preProcess(sql, mock(Statement.class), connection);

        verify(clientTracer).setClientSent(Endpoint.builder()
            .ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4).port(9999).serviceName("mysql-test").build());
    }

    @Test
    public void preProcessShouldLogServerAddress_defaultsPortTo3306() throws Exception {
        final String sql = randomAlphanumeric(20);
        final String schema = randomAlphanumeric(20);

        final Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(schema);
        when(connection.getHost()).thenReturn("1.2.3.4");
        final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:mysql://foo/test");
        
        Properties props = new Properties();
        when(connection.getProperties()).thenReturn(props);
        when(connection.getCatalog()).thenReturn("test");

        subject.preProcess(sql, mock(Statement.class), connection);

        verify(clientTracer).setClientSent(Endpoint.builder()
            .ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4).port(3306).serviceName("mysql-test").build());
    }
    
    @Test
    public void preProcessShouldLogProvidedServiceName() throws Exception {
    	final String sql = randomAlphanumeric(20);
        final String schema = randomAlphanumeric(20);

        final Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(schema);
        when(connection.getHost()).thenReturn("1.2.3.4");
        final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:mysql://foo/test");
        
        Properties props = new Properties();
        props.setProperty("zipkinServiceName", "hello-brave");
        when(connection.getProperties()).thenReturn(props);
        when(connection.getCatalog()).thenReturn("test");

        subject.preProcess(sql, mock(Statement.class), connection);

        verify(clientTracer).setClientSent(Endpoint.builder()
            .ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4).port(3306).serviceName("hello-brave").build());
    }
    
    @Test
    public void preProcessShouldIgnoreExceptionsLoggingServerAddress() throws Exception {
        final String sql = randomAlphanumeric(20);
        final String schema = randomAlphanumeric(20);

        final Connection connection = mock(Connection.class);
        when(connection.getSchema()).thenReturn(schema);
        when(connection.getHost()).thenReturn("1.2.3.4");
        when(connection.getMetaData()).thenThrow(new SQLException());

        subject.preProcess(sql, mock(Statement.class), connection);

        verify(clientTracer).setClientSent();
    }

    @Test
    public void preProcessShouldBeginTracingPreparedStatementCall() throws Exception {
        final String sql = randomAlphanumeric(20);

        final PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.getPreparedSql()).thenReturn(sql);
        final Connection connection = mock(Connection.class);

        assertNull(subject.preProcess(null, statement, connection));

        final InOrder order = inOrder(clientTracer);

        order.verify(clientTracer).startNewSpan("query");
        order.verify(clientTracer).submitBinaryAnnotation(eq(TraceKeys.SQL_QUERY), eq(sql));
        order.verify(clientTracer).setClientSent();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void postProcessShouldNotFailIfNoClientTracer() throws Exception {
        MySQLStatementInterceptor.setClientTracer(null);

        assertNull(subject.postProcess("sql", mock(Statement.class), mock(ResultSetInternalMethods.class), mock(Connection.class), 1, true, true, null));

        verifyZeroInteractions(clientTracer);
    }

    @Test
    public void postProcessShouldFinishTracingFailedSQLCall() throws Exception {

        final int warningCount = 1;
        final int errorCode = 2;

        assertNull(subject.postProcess("sql", mock(Statement.class), mock(ResultSetInternalMethods.class), mock(Connection.class), warningCount, true, true,
                new SQLException("", "", errorCode)));

        final InOrder order = inOrder(clientTracer);

        order.verify(clientTracer).submitBinaryAnnotation(eq("warning.count"), eq(warningCount + ""));
        order.verify(clientTracer).submitBinaryAnnotation(eq(Constants.ERROR), eq(errorCode + ""));
        order.verify(clientTracer).setClientReceived();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void postProcessShouldFinishTracingSuccessfulSQLCall() throws Exception {

        assertNull(subject.postProcess("sql", mock(Statement.class), mock(ResultSetInternalMethods.class), mock(Connection.class), 0, true, true, null));

        final InOrder order = inOrder(clientTracer);

        order.verify(clientTracer).setClientReceived();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void executeTopLevelOnlyShouldOnlyExecuteTopLevelQueries() throws Exception {
        assertTrue(subject.executeTopLevelOnly());
        verifyZeroInteractions(clientTracer);
    }

}
