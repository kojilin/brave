/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.context.jfr;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class JfrScopeDecoratorTest {
  @TempDir
  public File folder;

  ExecutorService wrappedExecutor = Executors.newSingleThreadExecutor();
  ScopeDecorator decorator = JfrScopeDecorator.get();
  CurrentTraceContext currentTraceContext = StrictCurrentTraceContext.newBuilder()
    .addScopeDecorator(JfrScopeDecorator.get())
    .build();

  Executor executor = currentTraceContext.executor(wrappedExecutor);
  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(1).build();
  TraceContext context2 = TraceContext.newBuilder().traceId(1).parentId(1).spanId(2).build();
  TraceContext context3 = TraceContext.newBuilder().traceId(2).spanId(3).build();

  @AfterEach void shutdownExecutor() throws InterruptedException {
    wrappedExecutor.shutdown();
    wrappedExecutor.awaitTermination(1, TimeUnit.SECONDS);
  }

  @Test void endToEndTest() throws Exception {
    Path destination = File.createTempFile("execute.jfr", null, folder).toPath();

    try (Recording recording = new Recording()) {
      recording.start();

      makeFiveScopes();

      recording.dump(destination);
    }

    List<RecordedEvent> events = RecordingFile.readAllEvents(destination);
    assertThat(events).extracting(e ->
      tuple(e.getString("traceId"), e.getString("parentId"), e.getString("spanId")))
      .containsExactlyInAnyOrder(
        tuple("0000000000000001", null, "0000000000000001"),
        tuple("0000000000000001", null, "0000000000000001"),
        tuple(null, null, null),
        tuple("0000000000000001", "0000000000000001", "0000000000000002"),
        tuple("0000000000000002", null, "0000000000000003")
      );
  }

  @Test void doesntDecorateNoop() {
    assertThat(decorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(decorator.decorateScope(null, Scope.NOOP)).isSameAs(Scope.NOOP);
  }

  /**
   * This makes five scopes:
   *
   * <pre><ol>
   *   <li>Explicit scope 1</li>
   *   <li>Implicit scope 1 with a scoping executor</li>
   *   <li>Explicit scope 2 inside an executor thread</li>
   *   <li>Explicit clearing scope inside an executor thread</li>
   *   <li>Explicit scope 3 outside the executor thread</li>
   * </ol></pre>
   */
  void makeFiveScopes() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);

    try (Scope scope = currentTraceContext.newScope(context)) {
      executor.execute(() -> {
        try (Scope clear = currentTraceContext.newScope(null)) {
        }
        try (Scope child = currentTraceContext.newScope(context2)) {
          latch.countDown();
        }
      });
    }

    try (Scope scope = currentTraceContext.newScope(context3)) {
      latch.countDown();
      shutdownExecutor();
    }
  }
}
