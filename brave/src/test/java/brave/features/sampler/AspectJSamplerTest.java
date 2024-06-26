/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.features.sampler;

import brave.ScopedSpan;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.DeclarativeSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.test.TestSpanHandler;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.atomic.AtomicReference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

class AspectJSamplerTest {

  // Don't use static configuration in real life. This is only to satisfy the unit test runner
  static StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();
  static TestSpanHandler spans = new TestSpanHandler();
  static AtomicReference<Tracing> tracing = new AtomicReference<>();

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @BeforeEach void clear() {
    tracing.set(Tracing.newBuilder()
      .currentTraceContext(currentTraceContext)
      .addSpanHandler(spans)
      .sampler(new Sampler() {
        @Override public boolean isSampled(long traceId) {
          throw new AssertionError(); // in this case, we aren't expecting a fallback
        }
      }).build());
    spans.clear();

    context.register(Config.class);
    context.refresh();
  }

  @AfterEach void close() {
    context.close();
    Tracing currentTracing = tracing.get();
    if (currentTracing != null) currentTracing.close();
    currentTraceContext.close();
  }

  @Test void traced() {
    Service service = context.getBean(Service.class);

    service.traced();

    assertThat(spans).isNotEmpty();
  }

  @Test void notTraced() {
    Service service = context.getBean(Service.class);

    service.notTraced();

    assertThat(spans).isEmpty();
  }

  @Configuration
  @EnableAspectJAutoProxy
  @Import({Service.class, TracingAspect.class})
  static class Config {
  }

  @Component @Aspect static class TracingAspect {
    SamplerFunction<Traced> samplerFunction = DeclarativeSampler.createWithRate(Traced::sampleRate);

    @Around("@annotation(traced)")
    public Object traceThing(ProceedingJoinPoint pjp, Traced traced) throws Throwable {
      Tracer tracer = tracing.get().tracer();

      // When there is no trace in progress, this overrides the decision based on the annotation
      ScopedSpan span = tracer.startScopedSpan(spanName(pjp), samplerFunction, traced);
      try {
        return pjp.proceed();
      } catch (RuntimeException | Error e) {
        span.error(e);
        throw e;
      } finally {
        span.finish();
      }
    }
  }

  @Component // aop only works for public methods.. the type can be package private though
  static class Service {
    // these two methods set different rates. This shows that instances are independent
    @Traced public void traced() {
    }

    @Traced(sampleRate = 0) public void notTraced() {
    }
  }

  @Retention(RetentionPolicy.RUNTIME) public @interface Traced {
    int sampleRate() default 10;
  }

  static String spanName(ProceedingJoinPoint pjp) {
    return pjp.getSignature().getName();
  }
}
