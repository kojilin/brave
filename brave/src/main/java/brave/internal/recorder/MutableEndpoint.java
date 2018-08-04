package brave.internal.recorder;

import brave.internal.Nullable;

/** The network context of a node in the service graph. */
public final class MutableEndpoint {

  String serviceName, ip;
  int port; // zero means null

  public MutableEndpoint() {
  }

  /** @see brave.Span#remoteServiceName(String) */
  @Nullable public String serviceName() {
    return serviceName;
  }

  /** @see #serviceName */
  public void serviceName(@Nullable String serviceName) {
    this.serviceName = serviceName == null || serviceName.isEmpty() ? null : serviceName;
  }

  /**
   * The text representation of the primary IPv4 or IPv6 address associated with this connection.
   * Ex. 192.168.99.100 null if unknown.
   */
  @Nullable public String ip() {
    return ip;
  }

  /** @see #ip */
  public void ip(@Nullable String ip) {
    if (ip == null || ip.isEmpty()) {
      this.ip = null;
      return;
    }
    IpFamily format = detectFamily(ip);
    if (format == IpFamily.IPv4 || format == IpFamily.IPv6) {
      this.ip = ip;
    } else if (format == IpFamily.IPv4Embedded) {
      this.ip = ip.substring(ip.lastIndexOf(':') + 1);
    } else {
      this.ip = null;
    }
  }

  /**
   * Port of the IP's socket or 0, if not known.
   *
   * @see java.net.InetSocketAddress#getPort()
   */
  public int port() {
    return port;
  }

  /** @see MutableEndpoint#port() */
  public void port(int port) {
    if (port > 0xffff) throw new IllegalArgumentException("invalid port " + port);
    if (port < 0) port = 0;
    this.port = port;
  }

  @Override public String toString() {
    return "MutableEndpoint{serviceName=" + serviceName + ", ip=" + ip + ", port=" + port + "}";
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof MutableEndpoint)) return false;
    MutableEndpoint that = (MutableEndpoint) o;
    return ((serviceName == null)
        ? (that.serviceName == null) : serviceName.equals(that.serviceName))
        && ((ip == null) ? (that.ip == null) : ip.equals(that.ip))
        && port == that.port;
  }

  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (serviceName == null) ? 0 : serviceName.hashCode();
    h *= 1000003;
    h ^= (ip == null) ? 0 : ip.hashCode();
    h *= 1000003;
    h ^= port;
    return h;
  }

  // All the below code is from zipkin2.Endpoint, copy/pasted here to prevent a depedency.
  enum IpFamily {
    Unknown,
    IPv4,
    IPv4Embedded,
    IPv6
  }

  /**
   * Adapted from code in {@code com.google.common.net.InetAddresses.ipStringToBytes}. This version
   * separates detection from parsing and checks more carefully about embedded addresses.
   */
  static IpFamily detectFamily(String ipString) {
    boolean hasColon = false;
    boolean hasDot = false;
    for (int i = 0, length = ipString.length(); i < length; i++) {
      char c = ipString.charAt(i);
      if (c == '.') {
        hasDot = true;
      } else if (c == ':') {
        if (hasDot) return IpFamily.Unknown; // Colons must not appear after dots.
        hasColon = true;
      } else if (notHex(c)) {
        return IpFamily.Unknown; // Everything else must be a decimal or hex digit.
      }
    }

    // Now decide which address family to parse.
    if (hasColon) {
      if (hasDot) {
        int lastColon = ipString.lastIndexOf(':');
        if (!isValidIpV4Address(ipString, lastColon + 1, ipString.length())) {
          return IpFamily.Unknown;
        }
        if (lastColon == 1 && ipString.charAt(0) == ':') {// compressed like ::1.2.3.4
          return IpFamily.IPv4Embedded;
        }
        if (lastColon != 6 || ipString.charAt(0) != ':' || ipString.charAt(1) != ':') {
          return IpFamily.Unknown;
        }
        for (int i = 2; i < 6; i++) {
          char c = ipString.charAt(i);
          if (c != 'f' && c != 'F' && c != '0') return IpFamily.Unknown;
        }
        return IpFamily.IPv4Embedded;
      }
      return IpFamily.IPv6;
    } else if (hasDot && isValidIpV4Address(ipString, 0, ipString.length())) {
      return IpFamily.IPv4;
    }
    return IpFamily.Unknown;
  }

  private static boolean notHex(char c) {
    return (c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F');
  }

  // Begin code from io.netty.util.NetUtil 4.1
  private static boolean isValidIpV4Address(String ip, int from, int toExcluded) {
    int len = toExcluded - from;
    int i;
    return len <= 15 && len >= 7 &&
        (i = ip.indexOf('.', from + 1)) > 0 && isValidIpV4Word(ip, from, i) &&
        (i = ip.indexOf('.', from = i + 2)) > 0 && isValidIpV4Word(ip, from - 1, i) &&
        (i = ip.indexOf('.', from = i + 2)) > 0 && isValidIpV4Word(ip, from - 1, i) &&
        isValidIpV4Word(ip, i + 1, toExcluded);
  }

  private static boolean isValidIpV4Word(CharSequence word, int from, int toExclusive) {
    int len = toExclusive - from;
    char c0, c1, c2;
    if (len < 1 || len > 3 || (c0 = word.charAt(from)) < '0') {
      return false;
    }
    if (len == 3) {
      return (c1 = word.charAt(from + 1)) >= '0' &&
          (c2 = word.charAt(from + 2)) >= '0' &&
          ((c0 <= '1' && c1 <= '9' && c2 <= '9') ||
              (c0 == '2' && c1 <= '5' && (c2 <= '5' || (c1 < '5' && c2 <= '9'))));
    }
    return c0 <= '9' && (len == 1 || isValidNumericChar(word.charAt(from + 1)));
  }

  private static boolean isValidNumericChar(char c) {
    return c >= '0' && c <= '9';
  }
  // End code from io.netty.util.NetUtil 4.1
}
