/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.codec;

import brave.Tags;
import brave.handler.MutableSpan;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import static brave.handler.MutableSpanBenchmarks.newBigClientMutableSpan;
import static brave.handler.MutableSpanBenchmarks.newServerMutableSpan;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class ZipkinV2JsonWriterBenchmarks {

  static final ZipkinV2JsonWriter writer = new ZipkinV2JsonWriter(Tags.ERROR);
  static final MutableSpan serverSpan = newServerMutableSpan();
  static final MutableSpan bigClientSpan = newBigClientMutableSpan();
  static final byte[] buffer = new byte[1024];

  @Benchmark public int sizeInBytes_serverSpan() {
    return writer.sizeInBytes(serverSpan);
  }

  @Benchmark public void write_serverSpan() {
    writer.write(serverSpan, new WriteBuffer(buffer, 0));
  }

  @Benchmark public int sizeInBytes_bigClientSpan() {
    return writer.sizeInBytes(bigClientSpan);
  }

  @Benchmark public void write_bigClientSpan() {
    writer.write(bigClientSpan, new WriteBuffer(buffer, 0));
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + ZipkinV2JsonWriter.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
