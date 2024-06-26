/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.Clock;
import brave.Tracing;
import brave.TracingCustomizer;
import brave.handler.SpanHandler;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.sampler.Sampler;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.AbstractFactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class TracingFactoryBean extends AbstractFactoryBean {
  String localServiceName;
  List<SpanHandler> spanHandlers = new ArrayList<SpanHandler>();
  Clock clock;
  Sampler sampler;
  CurrentTraceContext currentTraceContext;
  Propagation.Factory propagationFactory;
  Boolean traceId128Bit;
  Boolean supportsJoin;
  List<TracingCustomizer> customizers;

  @Override protected Tracing createInstance() {
    Tracing.Builder builder = Tracing.newBuilder();
    if (localServiceName != null) builder.localServiceName(localServiceName);
    for (SpanHandler spanHandler : spanHandlers) {
      builder.addSpanHandler(spanHandler);
    }
    if (clock != null) builder.clock(clock);
    if (sampler != null) builder.sampler(sampler);
    if (currentTraceContext != null) builder.currentTraceContext(currentTraceContext);
    if (propagationFactory != null) builder.propagationFactory(propagationFactory);
    if (traceId128Bit != null) builder.traceId128Bit(traceId128Bit);
    if (supportsJoin != null) builder.supportsJoin(supportsJoin);
    if (customizers != null) {
      for (TracingCustomizer customizer : customizers) customizer.customize(builder);
    }
    return builder.build();
  }

  @Override protected void destroyInstance(Object instance) {
    ((Tracing) instance).close();
  }

  @Override public Class<? extends Tracing> getObjectType() {
    return Tracing.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setLocalServiceName(String localServiceName) {
    this.localServiceName = localServiceName;
  }

  public void setSpanHandlers(List<SpanHandler> spanHandlers) {
    this.spanHandlers.addAll(spanHandlers);
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  public void setSampler(Sampler sampler) {
    this.sampler = sampler;
  }

  public void setCurrentTraceContext(CurrentTraceContext currentTraceContext) {
    this.currentTraceContext = currentTraceContext;
  }

  public void setPropagationFactory(Propagation.Factory propagationFactory) {
    this.propagationFactory = propagationFactory;
  }

  public void setTraceId128Bit(boolean traceId128Bit) {
    this.traceId128Bit = traceId128Bit;
  }

  public void setSupportsJoin(Boolean supportsJoin) {
    this.supportsJoin = supportsJoin;
  }

  public void setCustomizers(List<TracingCustomizer> customizers) {
    this.customizers = customizers;
  }
}
