/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.vertx.web;

import brave.Span;
import brave.Tracing;
import brave.http.HttpRequestParser;
import brave.http.HttpTags;
import brave.test.http.ITHttpServer;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

@Disabled("Run manually until openzipkin/brave#1270")
class ITVertxWebTracing extends ITHttpServer {
  Vertx vertx;
  HttpServer server;
  volatile int port;

  @Override protected void init() {
    stop();
    vertx = Vertx.vertx(new VertxOptions());

    Router router = Router.router(vertx);
    router.route(HttpMethod.OPTIONS, "/").handler(ctx -> {
      ctx.response().end("bar");
    });
    router.route("/foo").handler(ctx -> {
      ctx.response().end("bar");
    });
    router.route("/async").handler(ctx -> {
      if (Tracing.currentTracer().currentSpan() == null) {
        throw new IllegalStateException("couldn't read current span!");
      }
      ctx.request().endHandler(v -> ctx.response().end("bar"));
    });
    router.route("/reroute").handler(ctx -> {
      ctx.reroute("/foo");
    });
    router.route("/rerouteAsync").handler(ctx -> {
      if (Tracing.currentTracer().currentSpan() == null) {
        throw new IllegalStateException("couldn't read current span!");
      }
      ctx.reroute("/async");
    });
    router.route("/baggage").handler(ctx -> {
      ctx.response().end(BAGGAGE_FIELD.getValue());
    });
    router.route("/badrequest").handler(ctx -> {
      ctx.response().setStatusCode(400).end();
    });
    router.route("/child").handler(ctx -> {
      httpTracing.tracing().tracer().nextSpan().name("child").start().finish();
      ctx.response().end("happy");
    });
    router.route("/exception").handler(ctx -> {
      ctx.fail(503, NOT_READY_ISE);
    });
    router.route("/items/:itemId").handler(ctx -> {
      ctx.response().end(ctx.request().getParam("itemId"));
    });
    router.route("/async_items/:itemId").handler(ctx -> {
      if (Tracing.currentTracer().currentSpan() == null) {
        throw new IllegalStateException("couldn't read current span!");
      }
      ctx.request().endHandler(v -> ctx.response().end(ctx.request().getParam("itemId")));
    });
    Router subrouter = Router.router(vertx);
    subrouter.route("/items/:itemId").handler(ctx -> {
      ctx.response().end(ctx.request().getParam("itemId"));
    });
    router.mountSubRouter("/nested", subrouter);
    router.route("/exceptionAsync").handler(ctx -> {
      ctx.request().endHandler(v -> ctx.fail(503, NOT_READY_ISE));
    });

    Handler<RoutingContext> routingContextHandler =
      VertxWebTracing.create(httpTracing).routingContextHandler();
    router.route()
      .order(-1).handler(routingContextHandler)
      .failureHandler(routingContextHandler);

    server = vertx.createHttpServer(new HttpServerOptions().setPort(0).setHost("localhost"));

    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(router::handle).listen(async -> {
      port = async.result().actualPort();
      latch.countDown();
    });

    awaitFor10Seconds(latch, "server didn't start");
  }

  // makes sure we don't accidentally rewrite the incoming http path
  @Test void handlesReroute() throws IOException {
    handlesReroute("/reroute");
  }

  @Test void handlesRerouteAsync() throws IOException {
    handlesReroute("/rerouteAsync");
  }

  @Override @Test public void httpRoute_nested() {
    // Can't currently fully resolve the route template of a sub-router
    // We get "/nested" not "/nested/items/:itemId
    // https://groups.google.com/forum/?fromgroups#!topic/vertx/FtF2yVr5ZF8
    try {
      super.httpRoute_nested();
      failBecauseExceptionWasNotThrown(AssertionError.class);
    } catch (AssertionError | IOException e) {
      assertThat(e.getMessage().contains("nested"));
    }
  }

  void handlesReroute(String path) throws IOException {
    httpTracing = httpTracing.toBuilder().serverRequestParser((request, context, span) -> {
      HttpRequestParser.DEFAULT.parse(request, context, span);
      HttpTags.URL.tag(request, span); // just the path is logged by default
    }).build();
    init();

    Response response = get(path);
    assertThat(response.isSuccessful()).withFailMessage("not successful: " + response).isTrue();

    assertThat(testSpanHandler.takeRemoteSpan(Span.Kind.SERVER).tags())
      .containsEntry("http.path", path)
      .containsEntry("http.url", url(path));
  }

  @Override
  protected String url(String path) {
    return "http://127.0.0.1:" + port + path;
  }

  @AfterEach void stop() {
    if (vertx == null) return;

    CountDownLatch latch = new CountDownLatch(2);
    server.close(ar -> {
      latch.countDown();
    });
    vertx.close(ar -> {
      latch.countDown();
    });
    awaitFor10Seconds(latch, "server didn't close");
  }

  void awaitFor10Seconds(CountDownLatch latch, String message) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS))
        .withFailMessage(message)
        .isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
