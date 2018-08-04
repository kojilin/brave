package brave.servlet;

import brave.Span;
import javax.servlet.http.HttpServletRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServletAdapterTest {
  HttpServletAdapter adapter = new HttpServletAdapter();
  @Mock HttpServletRequest request;
  @Mock Span span;

  @Test public void path_doesntCrashOnNullUrl() {
    assertThat(adapter.path(request))
        .isNull();
  }

  @Test public void path_getRequestURI() {
    when(request.getRequestURI()).thenReturn("/bar");

    assertThat(adapter.path(request))
        .isEqualTo("/bar");
  }

  @Test public void url_derivedFromUrlAndQueryString() {
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://foo:8080/bar"));
    when(request.getQueryString()).thenReturn("hello=world");

    assertThat(adapter.url(request))
        .isEqualTo("http://foo:8080/bar?hello=world");
  }

  @Test public void parseClientIpPort_prefersXForwardedFor() {
    when(span.parseRemoteIpAndPort("1.2.3.4", 0)).thenReturn(true);
    when(adapter.requestHeader(request, "X-Forwarded-For")).thenReturn("1.2.3.4");
    when(request.getRemoteAddr()).thenReturn("3.4.5.6");

    adapter.parseClientIpPort(request, span);

    verify(span).parseRemoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseClientIpPort_skipsRemotePortOnXForwardedFor() {
    when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
    when(request.getRemotePort()).thenReturn(61687);
    when(span.parseRemoteIpAndPort("1.2.3.4", 0)).thenReturn(true);

    adapter.parseClientIpPort(request, span);

    verify(span).parseRemoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseClientIpPort_acceptsRemoteAddr() {
    when(request.getRemoteAddr()).thenReturn("1.2.3.4");
    when(request.getRemotePort()).thenReturn(61687);

    adapter.parseClientIpPort(request, span);

    verify(span).parseRemoteIpAndPort("1.2.3.4", 61687);
    verifyNoMoreInteractions(span);
  }
}
