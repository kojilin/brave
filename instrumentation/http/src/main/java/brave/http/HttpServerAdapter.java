package brave.http;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.TraceContext;

public abstract class HttpServerAdapter<Req, Resp> extends HttpAdapter<Req, Resp> {

  /**
   * @deprecated {@link #parseClientIpPort} addresses this functionality. This will be removed in
   * Brave v6.
   */
  @Deprecated public boolean parseClientAddress(Req req, zipkin2.Endpoint.Builder builder) {
    return false;
  }

  /**
   * Used by {@link HttpServerHandler#handleReceive(TraceContext.Extractor, Object, Object)} to add
   * remote socket information about the client. By default, this adds the {@link
   * #xForwardedForIp(Object) forwarded IP}. Override to add client socket information when
   * forwarded info is not available.
   *
   * <p>Aside: the ability to parse socket information on server request objects is likely even if
   * it is not as likely on the client side. This is because client requests are often parsed before
   * a network route is chosen, whereas server requests are parsed after the network layer.
   *
   * @since 5.2
   */
  public boolean parseClientIpPort(Req req, Span span) {
    String xForwardedForIp = xForwardedForIp(req);
    return xForwardedForIp != null && span.parseRemoteIpAndPort(xForwardedForIp, 0);
  }

  /**
   * Returns the first value in the "X-Forwarded-For" header, or null if not present.
   *
   * @since 5.2
   */
  @Nullable protected String xForwardedForIp(Req req) {
    String forwardedFor = requestHeader(req, "X-Forwarded-For");
    if (forwardedFor == null) return null;
    int indexOfComma = forwardedFor.indexOf(',');
    return indexOfComma == -1 ? forwardedFor : forwardedFor.substring(0, indexOfComma);
  }
}
