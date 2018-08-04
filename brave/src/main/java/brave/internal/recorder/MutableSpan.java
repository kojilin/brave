package brave.internal.recorder;

import brave.Span.Kind;
import brave.Tracer;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import zipkin2.Endpoint;
import zipkin2.Span;

/**
 * This represents a span except for its {@link TraceContext}. It is mutable, for late adjustments.
 *
 * <p>While in-flight, the data is synchronized where necessary. When exposed to users, it can be
 * mutated without synchronization.
 */
public final class MutableSpan {
  static final MutableEndpoint EMPTY_ENDPOINT = new MutableEndpoint();
  /*
   * One of these objects is allocated for each in-flight span, so we try to be parsimonious on things
   * like array allocation and object reference size.
   */
  Kind kind;
  boolean shared;
  long startTimestamp, finishTimestamp;
  String name;
  MutableEndpoint remoteEndpoint;
  /**
   * To reduce the amount of allocation, collocate annotations with tags in a pair-indexed list.
   * This will be (startTimestamp, value) for annotations and (key, value) for tags.
   */
  final List<Object> pairs = new ArrayList<>(6); // assume 3 tags and no annotations

  /** @see brave.Span#start(long) */
  public void startTimestamp(long startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  /** @see brave.Span#name(String) */
  public void name(String name) {
    if (name == null) throw new NullPointerException("name == null");
    this.name = name;
  }

  /** @see brave.Span#kind(brave.Span.Kind) */
  public void kind(Kind kind) {
    if (kind == null) throw new NullPointerException("kind == null");
    this.kind = kind;
  }

  /** @see brave.Span#remoteServiceName(String) */
  public void remoteServiceName(String remoteServiceName) {
    if (remoteServiceName == null) throw new NullPointerException("remoteServiceName == null");
    if (remoteEndpoint == null) remoteEndpoint = new MutableEndpoint();
    remoteEndpoint.serviceName(remoteServiceName);
  }

  /** @see brave.Span#parseRemoteIpAndPort(String, int) */
  public boolean parseRemoteIpAndPort(@Nullable String remoteIp, int remotePort) {
    if (remoteIp == null) return false;
    if (remoteEndpoint == null) remoteEndpoint = new MutableEndpoint();
    remoteEndpoint.ip(remoteIp);
    remoteEndpoint.port(remotePort);
    return remoteEndpoint.ip != null;
  }

  /** @see brave.Span#annotate(String) */
  public void annotate(long timestamp, String value) {
    if (value == null) throw new NullPointerException("value == null");
    if (timestamp == 0L) return;
    pairs.add(timestamp);
    pairs.add(value);
  }

  /** @see brave.Span#tag(String, String) */
  public void tag(String key, String value) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key is empty");
    if (value == null) throw new NullPointerException("value == null");
    for (int i = 0, length = pairs.size(); i < length; i += 2) {
      if (key.equals(pairs.get(i))) {
        pairs.set(i + 1, value);
        return;
      }
    }
    pairs.add(key);
    pairs.add(value);
  }

  /**
   * Indicates we are contributing to a span started by another tracer (ex on a different host).
   * Defaults to false.
   *
   * @see Tracer#joinSpan(TraceContext)
   * @see zipkin2.Span#shared()
   */
  public void setShared() {
    shared = true;
  }

  /** @see brave.Span#finish(long) */
  public void finishTimestamp(long finishTimestamp) {
    this.finishTimestamp = finishTimestamp;
  }

  // Since this is not exposed, this class could be refactored later as needed to act in a pool
  // to reduce GC churn. This would involve calling span.clear and resetting the fields below.
  public void writeTo(zipkin2.Span.Builder result) {
    result.remoteEndpoint(toZipkinEndpoint(remoteEndpoint));
    result.name(name);
    result.timestamp(startTimestamp);
    if (startTimestamp != 0 && finishTimestamp != 0L) {
      result.duration(Math.max(finishTimestamp - startTimestamp, 1));
    }
    if (kind != null && kind.ordinal() < Span.Kind.values().length) { // defend against version skew
      result.kind(zipkin2.Span.Kind.values()[kind.ordinal()]);
    }
    for (int i = 0, length = pairs.size(); i < length; i += 2) {
      Object first = pairs.get(i);
      String second = pairs.get(i + 1).toString();
      if (first instanceof Long) {
        result.addAnnotation((Long) first, second);
      } else {
        result.putTag(first.toString(), second);
      }
    }
    if (shared) result.shared(true);
  }

  @Nullable static Endpoint toZipkinEndpoint(MutableEndpoint endpoint) {
    if (endpoint == null || EMPTY_ENDPOINT.equals(endpoint)) return null;
    return Endpoint.newBuilder()
        .serviceName(endpoint.serviceName)
        .ip(endpoint.ip)
        .port(endpoint.port)
        .build();
  }

  MutableSpan(){ // intentionally hidden
  }
}
