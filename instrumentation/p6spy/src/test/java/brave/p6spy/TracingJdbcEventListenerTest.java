package brave.p6spy;

import brave.ScopedSpan;
import brave.Span;
import brave.Tracing;
import brave.sampler.Sampler;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static brave.p6spy.ITTracingP6Factory.tracingBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingJdbcEventListenerTest {
  @Mock Connection connection;
  @Mock DatabaseMetaData metaData;
  @Mock StatementInformation statementInformation;
  @Mock ConnectionInformation ci;

  @Mock Span span;
  String url = "jdbc:mysql://1.2.3.4:5555/mydatabase";
  String urlWithServiceName = url + "?zipkinServiceName=mysql_service&foo=bar";
  String urlWithEmptyServiceName = url + "?zipkinServiceName=&foo=bar";
  String urlWithWhiteSpace =
      "jdbc:sqlserver://1.2.3.4;databaseName=mydatabase;applicationName=Microsoft JDBC Driver for SQL Server";

  @Test public void parseServerIpPort_ipAndPortFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("", false).parseServerIpPort(connection, span);

    verify(span).parseRemoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpPort_serviceNameFromDatabaseName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false).parseServerIpPort(connection, span);

    verify(span).remoteServiceName("mydatabase");
    verify(span).parseRemoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpPort_serviceNameFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithServiceName);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false).parseServerIpPort(connection, span);

    verify(span).remoteServiceName("mysql_service");
    verify(span).parseRemoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpPort_emptyServiceNameFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithEmptyServiceName);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false).parseServerIpPort(connection, span);

    verify(span).remoteServiceName("mydatabase");
    verify(span).parseRemoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpPort_overrideServiceName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("foo", false).parseServerIpPort(connection, span);

    verify(span).remoteServiceName("foo");
    verify(span).parseRemoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpPort_doesntCrash() throws SQLException {
    when(connection.getMetaData()).thenThrow(new SQLException());

    verifyNoMoreInteractions(span);
  }

  @Test public void parseServerIpPort_withWhiteSpace() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithWhiteSpace);

    new TracingJdbcEventListener("foo", false).parseServerIpPort(connection, span);

    verify(span).remoteServiceName("foo");
  }

  @Test public void nullSqlWontNPE() throws SQLException {
    ArrayList<zipkin2.Span> spans = new ArrayList<>();
    try (Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE, spans).build()) {

      when(statementInformation.getSql()).thenReturn(null);
      when(statementInformation.getConnectionInformation()).thenReturn(ci);
      when(ci.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getURL()).thenReturn(url);

      TracingJdbcEventListener listener = new TracingJdbcEventListener("", false);
      listener.onBeforeAnyExecute(statementInformation);
      listener.onAfterAnyExecute(statementInformation, 1, null);

      assertThat(spans).isEmpty();
    }
  }

  @Test public void handleAfterExecute_without_beforeExecute_getting_called() {
    Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE, new ArrayList<>()).build();
    ScopedSpan parent = tracing.tracer().startScopedSpan("test");
    try {
      TracingJdbcEventListener listener = new TracingJdbcEventListener("", false);
      listener.onAfterAnyExecute(statementInformation, 1, null);
      listener.onAfterAnyExecute(statementInformation, 1, null);
    } finally {
      parent.finish();
    }
  }
}
