/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PropagationFactoryTest {
  Propagation.Factory factory = new Propagation.Factory() {
    @Override public Propagation<String> get() {
      return null;
    }
  };

  /** 64 bit trace IDs are not consistently mandatory across propagation, yet. */
  @Test void requires128BitTraceId_defaultsToFalse() {
    assertThat(factory.requires128BitTraceId())
      .isFalse();
  }

  /** join (reusing span ID on client and server side) is rarely supported outside B3. */
  @Test void supportsJoin_defaultsToFalse() {
    assertThat(B3Propagation.FACTORY.supportsJoin())
      .isTrue();
    assertThat(factory.supportsJoin())
      .isFalse();
  }

  @Test void decorate_defaultsToReturnSameInstance() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(1).build();
    assertThat(factory.decorate(context))
      .isSameAs(context);
  }
}
