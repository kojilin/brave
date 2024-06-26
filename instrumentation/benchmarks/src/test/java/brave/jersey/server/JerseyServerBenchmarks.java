/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jersey.server;

import brave.Tracing;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.http.HttpServerBenchmarks;
import brave.http.HttpTracing;
import brave.propagation.B3Propagation;
import brave.sampler.Sampler;
import io.undertow.servlet.api.DeploymentInfo;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import org.glassfish.jersey.servlet.ServletContainer;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import static brave.baggage.BaggagePropagationBenchmarks.BAGGAGE_FIELD;
import static io.undertow.servlet.Servlets.servlet;
import static java.util.Arrays.asList;

public class JerseyServerBenchmarks extends HttpServerBenchmarks {
  @Path("")
  public static class Resource {
    @GET @Produces("text/plain; charset=UTF-8") public String get() {
      // noop if not configured
      BAGGAGE_FIELD.updateValue("FO");
      return "hello world";
    }
  }

  @ApplicationPath("/nottraced")
  public static class App extends Application {
    @Override public Set<Object> getSingletons() {
      return Collections.singleton(new Resource());
    }
  }

  @ApplicationPath("/unsampled")
  public static class Unsampled extends Application {
    @Override public Set<Object> getSingletons() {
      return new LinkedHashSet<>(asList(new Resource(), TracingApplicationEventListener.create(
        HttpTracing.create(Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE).build())
      )));
    }
  }

  @ApplicationPath("/traced")
  public static class TracedApp extends Application {
    @Override public Set<Object> getSingletons() {
      return new LinkedHashSet<>(asList(new Resource(), TracingApplicationEventListener.create(
        HttpTracing.create(Tracing.newBuilder().build())
      )));
    }
  }

  @ApplicationPath("/tracedBaggage")
  public static class TracedBaggageApp extends Application {
    @Override public Set<Object> getSingletons() {
      return new LinkedHashSet<>(asList(new Resource(), TracingApplicationEventListener.create(
        HttpTracing.create(Tracing.newBuilder()
          .propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
            .add(SingleBaggageField.remote(BAGGAGE_FIELD)).build())
          .build())
      )));
    }
  }

  @ApplicationPath("/traced128")
  public static class Traced128App extends Application {
    @Override public Set<Object> getSingletons() {
      return new LinkedHashSet<>(asList(new Resource(), TracingApplicationEventListener.create(
        HttpTracing.create(Tracing.newBuilder()
          .traceId128Bit(true)
          .build())
      )));
    }
  }

  @Override protected void init(DeploymentInfo servletBuilder) {
    servletBuilder.addServlets(
      servlet("Unsampled", ServletContainer.class)
        .setLoadOnStartup(1)
        .addInitParam("javax.ws.rs.Application", Unsampled.class.getName())
        .addMapping("/unsampled"),
      servlet("Traced", ServletContainer.class)
        .setLoadOnStartup(1)
        .addInitParam("javax.ws.rs.Application", TracedApp.class.getName())
        .addMapping("/traced"),
      servlet("TracedBaggage", ServletContainer.class)
        .setLoadOnStartup(1)
        .addInitParam("javax.ws.rs.Application", TracedBaggageApp.class.getName())
        .addMapping("/tracedBaggage"),
      servlet("Traced128", ServletContainer.class)
        .setLoadOnStartup(1)
        .addInitParam("javax.ws.rs.Application", Traced128App.class.getName())
        .addMapping("/traced128"),
      servlet("App", ServletContainer.class)
        .setLoadOnStartup(1)
        .addInitParam("javax.ws.rs.Application", App.class.getName())
        .addMapping("/*")
    );
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + JerseyServerBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
