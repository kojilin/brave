package brave.mysql;

import brave.Span;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Properties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingStatementInterceptorTest {
  @Mock Connection connection;
  @Mock DatabaseMetaData metaData;

  @Mock Span span;
  String url = "jdbc:mysql://myhost:5555/mydatabase";

  @Test public void parseServerIpPort_ipFromHost_portFromUrl() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4");

    TracingStatementInterceptor.parseServerIpPort(connection, span);

    verify(span).remoteServiceName("mysql");
    verify(span).parseRemoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpPort_serviceNameFromDatabaseName() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4");
    when(connection.getCatalog()).thenReturn("mydatabase");

    TracingStatementInterceptor.parseServerIpPort(connection, span);

    verify(span).remoteServiceName("mysql-mydatabase");
    verify(span).parseRemoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpPort_propertiesOverrideServiceName() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4").setProperty("zipkinServiceName", "foo");

    TracingStatementInterceptor.parseServerIpPort(connection, span);

    verify(span).remoteServiceName("foo");
    verify(span).parseRemoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpPort_emptyZipkinServiceNameIgnored() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4").setProperty("zipkinServiceName", "");

    TracingStatementInterceptor.parseServerIpPort(connection, span);

    verify(span).remoteServiceName("mysql");
    verify(span).parseRemoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpPort_doesntCrash() throws SQLException {
    when(connection.getMetaData()).thenThrow(new SQLException());

    verifyNoMoreInteractions(span);
  }

  Properties setupAndReturnPropertiesForHost(String host) throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);
    Properties properties = new Properties();
    when(connection.getProperties()).thenReturn(properties);
    when(connection.getHost()).thenReturn(host);
    return properties;
  }
}
