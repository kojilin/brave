package brave.httpclient5;

import brave.test.http.ITHttpAsyncClient;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import okhttp3.Protocol;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.junit.Before;

public class ITTracingH2AsyncClientBuilder extends ITHttpAsyncClient<CloseableHttpAsyncClient> {

  static void invoke(CloseableHttpAsyncClient client, SimpleHttpRequest req) throws IOException {
    Future<SimpleHttpResponse> future = client.execute(req, null);
    blockOnFuture(future);
  }

  static <V> V blockOnFuture(Future<V> future) throws IOException {
    try {
      return future.get(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      Throwable er = e.getCause();
      if (er instanceof RuntimeException) {
        throw (RuntimeException) er;
      }
      if (er instanceof IOException) {
        throw (IOException) er;
      }
      throw new AssertionError(e);
    } catch (TimeoutException e) {
      throw new AssertionError(e);
    }
  }

  @Before
  public void setup() throws IOException {
    List<Protocol> protocols = new ArrayList<>();
    protocols.add(Protocol.H2_PRIOR_KNOWLEDGE);
    server.setProtocols(protocols);
    super.setup();
  }

  @Override
  protected CloseableHttpAsyncClient newClient(int port) {
    CloseableHttpAsyncClient result =
      TracingHttpClients.create(httpTracing,
        H2AsyncClientBuilder.create().disableAutomaticRetries());
    result.start();
    return result;
  }

  @Override
  protected void closeClient(CloseableHttpAsyncClient client) throws IOException {
    client.close();
  }

  @Override
  protected void get(CloseableHttpAsyncClient client, String pathIncludingQuery)
    throws IOException {
    invoke(client, SimpleHttpRequests.get(URI.create(url(pathIncludingQuery))));
  }

  @Override
  protected void options(CloseableHttpAsyncClient client, String path) throws IOException {
    invoke(client, SimpleHttpRequests.options(URI.create(url(path))));
  }

  @Override
  protected void post(CloseableHttpAsyncClient client, String pathIncludingQuery, String body)
    throws IOException {
    SimpleHttpRequest post = SimpleHttpRequests.post(URI.create(url(pathIncludingQuery)));
    post.setBody(body, ContentType.TEXT_PLAIN);
    invoke(client, post);
  }

  @Override
  protected void get(CloseableHttpAsyncClient client, String path,
    BiConsumer<Integer, Throwable> callback) {
    SimpleHttpRequest get = SimpleHttpRequests.get(URI.create(url(path)));
    client.execute(get, new FutureCallback<SimpleHttpResponse>() {
      @Override
      public void completed(SimpleHttpResponse res) {
        callback.accept(res.getCode(), null);
      }

      @Override
      public void failed(Exception ex) {
        callback.accept(null, ex);
      }

      @Override
      public void cancelled() {
        callback.accept(null, new CancellationException());
      }
    });
  }
}
