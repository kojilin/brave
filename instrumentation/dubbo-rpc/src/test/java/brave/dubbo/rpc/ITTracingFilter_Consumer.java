/*
 * Copyright 2013-2023 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.dubbo.rpc;

import brave.Clock;
import brave.Tag;
import brave.handler.MutableSpan;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.rpc.RpcResponseParser;
import brave.rpc.RpcRuleSampler;
import brave.rpc.RpcTracing;
import com.alibaba.dubbo.common.beanutil.JavaBeanDescriptor;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static brave.Span.Kind.CLIENT;
import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static brave.sampler.Sampler.ALWAYS_SAMPLE;
import static brave.sampler.Sampler.NEVER_SAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ITTracingFilter_Consumer extends ITTracingFilter {
  ReferenceConfig<GraterService> wrongClient;

  @BeforeEach void setup() {
    init();
    server.start();

    String url = "dubbo://" + server.ip() + ":" + server.port() + "?scope=remote&generic=bean";
    client = new ReferenceConfig<>();
    client.setApplication(new ApplicationConfig("bean-consumer"));
    client.setFilter("tracing");
    client.setInterface(GreeterService.class);
    client.setUrl(url);

    wrongClient = new ReferenceConfig<>();
    wrongClient.setApplication(new ApplicationConfig("bad-consumer"));
    wrongClient.setFilter("tracing");
    wrongClient.setInterface(GraterService.class);
    wrongClient.setUrl(url);
  }

  @Test void propagatesNewTrace() {
    client.get().sayHello("jorge");

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isTrue();
    assertThat(extracted.parentIdString()).isNull();
    assertSameIds(testSpanHandler.takeRemoteSpan(CLIENT), extracted);
  }

  @Test void propagatesChildOfCurrentSpan() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isTrue();
    assertChildOf(extracted, parent);
    assertSameIds(testSpanHandler.takeRemoteSpan(CLIENT), extracted);
  }

  /** Unlike Brave 3, Brave 4 propagates trace ids even when unsampled */
  @Test void propagatesUnsampledContext() {
    TraceContext parent = newTraceContext(SamplingFlags.NOT_SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isFalse();
    assertChildOf(extracted, parent);
  }

  @Test void propagatesBaggage() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      BAGGAGE_FIELD.updateValue(parent, "joey");
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(BAGGAGE_FIELD.getValue(extracted)).isEqualTo("joey");

    testSpanHandler.takeRemoteSpan(CLIENT);
  }

  @Test void propagatesBaggage_unsampled() {
    TraceContext parent = newTraceContext(SamplingFlags.NOT_SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      BAGGAGE_FIELD.updateValue(parent, "joey");
      client.get().sayHello("jorge");
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(BAGGAGE_FIELD.getValue(extracted)).isEqualTo("joey");
  }

  /** This prevents confusion as a blocking client should end before, the start of the next span. */
  @Test void clientTimestampAndDurationEnclosedByParent() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    Clock clock = tracing.clock(parent);

    long start = clock.currentTimeMicroseconds();
    try (Scope scope = currentTraceContext.newScope(parent)) {
      client.get().sayHello("jorge");
    }
    long finish = clock.currentTimeMicroseconds();

    MutableSpan clientSpan = testSpanHandler.takeRemoteSpan(CLIENT);
    assertChildOf(clientSpan, parent);
    assertSpanInInterval(clientSpan, start, finish);
  }

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test void usesParentFromInvocationTime() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      RpcContext.getContext().asyncCall(() -> client.get().sayHello("jorge"));
      RpcContext.getContext().asyncCall(() -> client.get().sayHello("romeo"));
    }

    try (Scope scope = currentTraceContext.newScope(null)) {
      // complete within a different scope
      for (int i = 0; i < 2; i++) {
        TraceContext extracted = server.takeRequest().context();
        assertChildOf(extracted, parent);
      }
    }

    // The spans may report in a different order than the requests
    for (int i = 0; i < 2; i++) {
      assertChildOf(testSpanHandler.takeRemoteSpan(CLIENT), parent);
    }
  }

  @Test void reportsClientKindToZipkin() {
    client.get().sayHello("jorge");

    testSpanHandler.takeRemoteSpan(CLIENT);
  }

  @Test void defaultSpanNameIsMethodName() {
    client.get().sayHello("jorge");

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).name())
        .isEqualTo("brave.dubbo.rpc.GreeterService/sayHello");
  }

  @Test void onTransportException_setsError() {
    server.stop();

    assertThatThrownBy(() -> client.get().sayHello("jorge"))
        .isInstanceOf(RpcException.class);

    testSpanHandler.takeRemoteSpanWithErrorMessage(CLIENT, ".*RemotingException.*");
  }

  @Test void onTransportException_setsError_async() {
    server.stop();

    RpcContext.getContext().asyncCall(() -> client.get().sayHello("romeo"));

    testSpanHandler.takeRemoteSpanWithErrorMessage(CLIENT, ".*RemotingException.*");
  }

  @Test void finishesOneWay() {
    RpcContext.getContext().asyncCall(() -> {
      client.get().sayHello("romeo");
    });

    testSpanHandler.takeRemoteSpan(CLIENT);
  }

  @Test void setsError_onUnimplemented() {
    assertThatThrownBy(() -> wrongClient.get().sayHello("jorge"))
        .isInstanceOf(RpcException.class);

    MutableSpan span =
        testSpanHandler.takeRemoteSpanWithErrorMessage(CLIENT, ".*Not found exported service.*");
    assertThat(span.tags())
        .containsEntry("dubbo.error_code", "1");
  }

  /** Shows if you aren't using RpcTracing, the old "dubbo.error_code" works */
  @Test void setsError_onUnimplemented_legacy() {
    ((TracingFilter) ExtensionLoader.getExtensionLoader(Filter.class)
        .getExtension("tracing")).isInit = false;

    ((TracingFilter) ExtensionLoader.getExtensionLoader(Filter.class)
        .getExtension("tracing"))
        .setTracing(tracing);

    assertThatThrownBy(() -> wrongClient.get().sayHello("jorge"))
        .isInstanceOf(RpcException.class);

    MutableSpan span =
        testSpanHandler.takeRemoteSpanWithErrorMessage(CLIENT, ".*Not found exported service.*");
    assertThat(span.tags())
        .containsEntry("dubbo.error_code", "1");
  }

  /** Ensures the span completes on asynchronous invocation. */
  @Test void test_async_invoke() throws Exception {
    client.setAsync(true);
    String jorge = client.get().sayHello("jorge");
    assertThat(jorge).isNull();
    Object o = RpcContext.getContext().getFuture().get();
    assertThat(o).isNotNull();

    testSpanHandler.takeRemoteSpan(CLIENT);
  }

  /* RpcTracing-specific feature tests */

  @Test void customSampler() {
    RpcTracing rpcTracing = RpcTracing.newBuilder(tracing).clientSampler(RpcRuleSampler.newBuilder()
        .putRule(methodEquals("sayGoodbye"), NEVER_SAMPLE)
        .putRule(serviceEquals("brave.dubbo"), ALWAYS_SAMPLE)
        .build()).build();
    init().setRpcTracing(rpcTracing);

    // unsampled
    client.get().sayGoodbye("jorge");

    // sampled
    client.get().sayHello("jorge");

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).name()).endsWith("sayHello");
    // @After will also check that sayGoodbye was not sampled
  }

  @Test void customParser() {
    Tag<DubboResponse> javaValue = new Tag<DubboResponse>("dubbo.result_value") {
      @Override protected String parseValue(DubboResponse input, TraceContext context) {
        Result result = input.result();
        if (result == null) return null;
        Object value = result.getValue();
        if (value instanceof JavaBeanDescriptor) {
          return String.valueOf(((JavaBeanDescriptor) value).getProperty("value"));
        }
        return null;
      }
    };

    RpcTracing rpcTracing = RpcTracing.newBuilder(tracing)
        .clientResponseParser((res, context, span) -> {
          RpcResponseParser.DEFAULT.parse(res, context, span);
          if (res instanceof DubboResponse) {
            javaValue.tag((DubboResponse) res, span);
          }
        }).build();
    init().setRpcTracing(rpcTracing);

    String javaResult = client.get().sayHello("jorge");

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).tags())
        .containsEntry("dubbo.result_value", javaResult);
  }
}
