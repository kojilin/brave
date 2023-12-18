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

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DubboServerRequestTest {
  Invoker invoker = mock(Invoker.class);
  Invocation invocation = mock(Invocation.class);
  URL url = URL.valueOf("dubbo://localhost:6666?scope=remote&interface=brave.dubbo.GreeterService");
  DubboServerRequest request = new DubboServerRequest(invoker, invocation);

  @Test void service() {
    when(invocation.getInvoker()).thenReturn(invoker);
    when(invoker.getUrl()).thenReturn(url);

    assertThat(request.service())
      .isEqualTo("brave.dubbo.GreeterService");
  }

  @Test void method() {
    when(invocation.getMethodName()).thenReturn("sayHello");

    assertThat(request.method()).isEqualTo("sayHello");
  }

  @Test void unwrap() {
    assertThat(request.unwrap()).isSameAs(invocation);
  }

  @Test void invoker() {
    assertThat(request.invoker()).isSameAs(invoker);
  }

  @Test void invocation() {
    assertThat(request.invocation()).isSameAs(invocation);
  }

  @Test void propagationField() {
    when(invocation.getAttachment("b3")).thenReturn("d");

    assertThat(request.propagationField("b3")).isEqualTo("d");
  }
}
