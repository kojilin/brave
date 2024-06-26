/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

import java.io.IOException;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DubboParserTest {
  @Mock Invocation invocation;
  @Mock Invoker invoker;
  @Mock URL url;

  @Test void method() {
    when(invocation.getMethodName()).thenReturn("sayHello");

    assertThat(DubboParser.method(invocation))
        .isEqualTo("sayHello");
  }

  @Test void method_malformed() {
    when(invocation.getMethodName()).thenReturn("");

    assertThat(DubboParser.method(invocation)).isNull();
  }

  @Test void method_invoke() {
    when(invocation.getMethodName()).thenReturn("$invoke");
    when(invocation.getArguments()).thenReturn(new Object[] {"sayHello"});

    assertThat(DubboParser.method(invocation))
        .isEqualTo("sayHello");
  }

  @Test void method_invoke_nullArgs() {
    when(invocation.getMethodName()).thenReturn("$invoke");

    assertThat(DubboParser.method(invocation)).isNull();
  }

  @Test void method_invoke_emptyArgs() {
    when(invocation.getMethodName()).thenReturn("$invoke");
    when(invocation.getArguments()).thenReturn(new Object[0]);

    assertThat(DubboParser.method(invocation)).isNull();
  }

  @Test void method_invoke_nonStringArg() {
    when(invocation.getMethodName()).thenReturn("$invoke");
    when(invocation.getArguments()).thenReturn(new Object[] {new Object()});

    assertThat(DubboParser.method(invocation)).isNull();
  }

  @Test void service() {
    when(invoker.getUrl()).thenReturn(url);
    when(url.getServiceInterface()).thenReturn("brave.dubbo.GreeterService");

    assertThat(DubboParser.service(invoker))
        .isEqualTo("brave.dubbo.GreeterService");
  }

  @Test void service_nullUrl() {
    assertThat(DubboParser.service(invoker)).isNull();
  }

  @Test void service_nullServiceInterface() {
    when(invoker.getUrl()).thenReturn(url);

    assertThat(DubboParser.service(invoker)).isNull();
  }

  @Test void service_malformed() {
    when(invoker.getUrl()).thenReturn(url);
    when(url.getServiceInterface()).thenReturn("");

    assertThat(DubboParser.service(invoker)).isNull();
  }

  @Test void errorCodes() {
    assertThat(DubboParser.errorCode(null))
        .isEqualTo(DubboParser.errorCode(new IOException("timeout")))
        .isNull();

    assertThat(DubboParser.errorCode(new RpcException(0)))
        .isEqualTo("UNKNOWN_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(1)))
        .isEqualTo("NETWORK_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(2)))
        .isEqualTo("TIMEOUT_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(3)))
        .isEqualTo("BIZ_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(4)))
        .isEqualTo("FORBIDDEN_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(5)))
        .isEqualTo("SERIALIZATION_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(6)))
        .isEqualTo("NO_INVOKER_AVAILABLE_AFTER_FILTER");
    assertThat(DubboParser.errorCode(new RpcException(7)))
        .isEqualTo("LIMIT_EXCEEDED_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(8)))
        .isEqualTo("TIMEOUT_TERMINATE");
    assertThat(DubboParser.errorCode(new RpcException(9)))
      .isEqualTo("REGISTRY_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(10)))
      .isEqualTo("ROUTER_CACHE_NOT_BUILD");
    assertThat(DubboParser.errorCode(new RpcException(11)))
      .isEqualTo("METHOD_NOT_FOUND");
    assertThat(DubboParser.errorCode(new RpcException(12)))
      .isEqualTo("VALIDATION_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(13)))
      .isEqualTo("AUTHORIZATION_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(14)))
      .isNull();// This test will drift with a new error code name if Dubbo adds one
  }
}
