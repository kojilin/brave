/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.baggage;

import brave.propagation.Propagation;

/**
 * This allows configuration plugins to collaborate on building an instance of {@link
 * CorrelationScopeDecorator}.
 *
 * <p>For example, a customizer can {@linkplain CorrelationScopeDecorator.Builder#add(CorrelationScopeConfig)
 * configure a baggage field} without instantiating the {@link CorrelationScopeDecorator.Builder).
 *
 * <p>This also allows one object to customize both {@linkplain BaggagePropagation baggage}
 * and {@link CorrelationScopeDecorator correlation integration}, by implementing both customizer
 * interfaces.
 *
 * <h3>Integration examples</h3>
 *
 * <p>In practice, a dependency injection tool applies a collection of these instances prior to
 * {@link CorrelationScopeDecorator.Builder#build() building the scope instance}. For example, an
 * injected {@code List<CorrelationCustomizer>} parameter to a provider of {@link
 * Propagation.Factory }.
 *
 * <p>Here are some examples, in alphabetical order:
 * <pre><ul>
 *   <li><a href="https://dagger.dev/multibindings.html">Dagger Set Multibindings</a></li>
 *   <li><a href="http://google.github.io/guice/api-docs/latest/javadoc/com/google/inject/multibindings/Multibinder.html">Guice Set Multibinder</a></li>
 *   <li><a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/core.html#beans-autowired-annotation">Spring Autowired Collections</a></li>
 * </ul></pre>
 *
 * <p><em>Note</em>: This type is safe to implement as a lambda, or use as a method reference as it
 * is effectively a {@code FunctionalInterface}. It isn't annotated as such because the project has
 * a minimum Java language level 6.
 *
 * @see CorrelationScopeConfig
 * @see BaggageCustomizer
 * @since 5.11
 */
// @FunctionalInterface, except Java language level 6. Do not add methods as it will break API!
public interface CorrelationScopeCustomizer {
  /** Use to avoid comparing against null references */
  CorrelationScopeCustomizer NOOP = new CorrelationScopeCustomizer() {
    @Override public void customize(CorrelationScopeDecorator.Builder builder) {
    }

    @Override public String toString() {
      return "NoopCorrelationScopeCustomizer{}";
    }
  };

  void customize(CorrelationScopeDecorator.Builder builder);
}
