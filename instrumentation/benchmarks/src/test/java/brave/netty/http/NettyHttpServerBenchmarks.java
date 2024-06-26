/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.netty.http;

import brave.Tracing;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.http.HttpServerBenchmarks;
import brave.propagation.B3Propagation;
import brave.sampler.Sampler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.AttributeKey;
import io.undertow.servlet.api.DeploymentInfo;
import java.net.InetSocketAddress;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import static brave.EndToEndBenchmarks.COUNTRY_CODE;
import static brave.EndToEndBenchmarks.REQUEST_ID;
import static brave.EndToEndBenchmarks.USER_ID;

public class NettyHttpServerBenchmarks extends HttpServerBenchmarks {

  EventLoopGroup bossGroup;
  EventLoopGroup workerGroup;

  @Override protected void init(DeploymentInfo servletBuilder) {
  }

  // NOTE: if the tracing server handler starts to override more methods, this needs to be updated
  static class TracingDispatchHandler extends ChannelDuplexHandler {
    static final AttributeKey<String> URI_ATTRIBUTE = AttributeKey.valueOf("uri");

    final ChannelDuplexHandler unsampled = NettyHttpTracing.create(
      Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE).build()
    ).serverHandler();
    final ChannelDuplexHandler traced = NettyHttpTracing.create(
      Tracing.newBuilder()
        .propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
          .add(SingleBaggageField.remote(REQUEST_ID))
          .add(
            SingleBaggageField.newBuilder(COUNTRY_CODE).addKeyName("baggage-country-code").build())
          .add(SingleBaggageField.newBuilder(USER_ID).addKeyName("baggage-user-id").build())
          .build())
        .build()
    ).serverHandler();
    final ChannelDuplexHandler tracedBaggage = NettyHttpTracing.create(
      Tracing.newBuilder().build()
    ).serverHandler();
    final ChannelDuplexHandler traced128 = NettyHttpTracing.create(
      Tracing.newBuilder().traceId128Bit(true).build()
    ).serverHandler();

    @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (!(msg instanceof HttpRequest)) {
        ctx.fireChannelRead(msg);
        return;
      }
      String uri = ((HttpRequest) msg).uri();
      if ("/unsampled".equals(uri)) {
        ctx.channel().attr(URI_ATTRIBUTE).set(uri);
        unsampled.channelRead(ctx, msg);
      } else if ("/traced".equals(uri)) {
        ctx.channel().attr(URI_ATTRIBUTE).set(uri);
        traced.channelRead(ctx, msg);
      } else if ("/tracedBaggage".equals(uri)) {
        ctx.channel().attr(URI_ATTRIBUTE).set(uri);
        COUNTRY_CODE.updateValue("FO");
        tracedBaggage.channelRead(ctx, msg);
      } else if ("/traced128".equals(uri)) {
        ctx.channel().attr(URI_ATTRIBUTE).set(uri);
        traced128.channelRead(ctx, msg);
      } else {
        ctx.fireChannelRead(msg);
      }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) throws Exception {
      String uri = ctx.channel().attr(URI_ATTRIBUTE).get();
      if (uri == null) {
        ctx.write(msg, prm);
        return;
      }
      if ("/unsampled".equals(uri)) {
        unsampled.write(ctx, msg, prm);
      } else if ("/traced".equals(uri)) {
        traced.write(ctx, msg, prm);
      } else if ("/traced128".equals(uri)) {
        traced128.write(ctx, msg, prm);
      } else {
        ctx.write(msg, prm);
      }
    }
  }

  @Override protected int initServer() throws InterruptedException {
    bossGroup = new NioEventLoopGroup(1);
    workerGroup = new NioEventLoopGroup();

    ServerBootstrap b = new ServerBootstrap();
    b.option(ChannelOption.SO_BACKLOG, 1024);
    b.group(bossGroup, workerGroup)
      .channel(NioServerSocketChannel.class)
      .childHandler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(final Channel ch) throws Exception {
          ChannelPipeline p = ch.pipeline();
          p.addLast(new HttpServerCodec());
          p.addLast(new TracingDispatchHandler());
          p.addLast(new HelloWorldHandler());
        }
      });

    Channel ch = b.bind(0).sync().channel();
    return ((InetSocketAddress) ch.localAddress()).getPort();
  }

  @TearDown(Level.Trial) public void closeNetty() {
    if (bossGroup != null) bossGroup.shutdownGracefully();
    if (workerGroup != null) workerGroup.shutdownGracefully();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + NettyHttpServerBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
