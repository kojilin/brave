/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.web;

import brave.Span;
import brave.test.http.ITHttpAsyncClient;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiConsumer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsAsyncClientHttpRequestFactory;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import static org.assertj.core.api.Assertions.assertThat;

class ITTracingAsyncClientHttpRequestInterceptor
  extends ITHttpAsyncClient<AsyncClientHttpRequestFactory> {
  AsyncClientHttpRequestInterceptor interceptor;
  CloseableHttpAsyncClient asyncClient = HttpAsyncClients.createSystem();

  @AfterEach @Override public void close() throws Exception {
    asyncClient.close();
    super.close();
  }

  AsyncClientHttpRequestFactory configureClient(AsyncClientHttpRequestInterceptor interceptor) {
    HttpComponentsAsyncClientHttpRequestFactory factory =
      new HttpComponentsAsyncClientHttpRequestFactory(asyncClient);
    factory.setReadTimeout(1000);
    factory.setConnectTimeout(1000);
    this.interceptor = interceptor;
    return factory;
  }

  @Override protected AsyncClientHttpRequestFactory newClient(int port) {
    return configureClient(TracingAsyncClientHttpRequestInterceptor.create(httpTracing));
  }

  @Override protected void closeClient(AsyncClientHttpRequestFactory client) {
    // done in close()
  }

  @Override protected void get(AsyncClientHttpRequestFactory client, String pathIncludingQuery) {
    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.getForEntity(url(pathIncludingQuery), String.class).completable().join();
  }

  @Override protected void options(AsyncClientHttpRequestFactory client, String path) {
    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.optionsForAllow(url(path)).completable().join();
  }

  @Override protected void post(AsyncClientHttpRequestFactory client, String uri, String content) {
    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.postForEntity(url(uri), RequestEntity.post(URI.create(url(uri))).body(content),
      String.class).completable().join();
  }

  @Override protected void get(AsyncClientHttpRequestFactory client, String path,
    BiConsumer<Integer, Throwable> callback) {
    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.getForEntity(url(path), String.class)
      .addCallback(new ListenableFutureCallback<ResponseEntity<String>>() {
        @Override public void onFailure(Throwable throwable) {
          if (throwable instanceof HttpStatusCodeException) {
            callback.accept(((HttpStatusCodeException) throwable).getRawStatusCode(), null);
          } else {
            callback.accept(null, throwable);
          }
        }

        @Override public void onSuccess(ResponseEntity<String> entity) {
          callback.accept(entity.getStatusCodeValue(), null);
        }
      });
  }

  @Test void currentSpanVisibleToUserInterceptors() throws Exception {
    server.enqueue(new MockResponse());

    AsyncRestTemplate restTemplate = new AsyncRestTemplate(client);
    restTemplate.setInterceptors(Arrays.asList(interceptor, (request, body, execution) -> {
      request.getHeaders()
        .add("my-id", currentTraceContext.get().traceIdString());
      return execution.executeAsync(request, body);
    }));
    restTemplate.getForEntity(server.url("/foo").toString(), String.class).get();

    RecordedRequest request = takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
      .isEqualTo(request.getHeader("my-id"));

    testSpanHandler.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Override @Disabled("blind to the implementation of redirects")
  public void redirect() {
  }

  @Override @Disabled("doesn't know the remote address")
  public void reportsServerAddress() {
  }

  @Override @Disabled("sometimes the client span last longer than the future")
  // ignoring flakes as AsyncRestTemplate is deprecated anyway and only impact is inaccurate timing
  public void clientTimestampAndDurationEnclosedByParent() {
  }
}
