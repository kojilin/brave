/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal;

import brave.Clock;
import brave.Tracer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Access to platform-specific features.
 *
 * <p>Note: Logging is centralized here to avoid classloader problems.
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.Platform}
 */
public abstract class Platform implements Clock {
  private static final Platform PLATFORM = findPlatform();

  volatile String linkLocalIp;

  /** Returns the same value as {@link System#nanoTime()}. */
  @Nullable public abstract long nanoTime();


  /** Guards {@link InetSocketAddress#getHostString()}, as it isn't available until Java 7 */
  @Nullable public abstract String getHostString(InetSocketAddress socket);

  @Nullable public String linkLocalIp() {
    // uses synchronized variant of double-checked locking as getting the endpoint can be expensive
    if (linkLocalIp != null) return linkLocalIp;
    synchronized (this) {
      if (linkLocalIp == null) {
        linkLocalIp = produceLinkLocalIp();
      }
    }
    return linkLocalIp;
  }

  String produceLinkLocalIp() {
    try {
      Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
      while (nics.hasMoreElements()) {
        NetworkInterface nic = nics.nextElement();
        Enumeration<InetAddress> addresses = nic.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress address = addresses.nextElement();
          if (address.isSiteLocalAddress()) return address.getHostAddress();
        }
      }
    } catch (Exception e) {
      // don't crash the caller if there was a problem reading nics.
      log("error reading nics", e);
    }
    return null;
  }

  public AssertionError assertionError(String message, Throwable cause) {
    AssertionError error = new AssertionError(message);
    error.initCause(cause);
    throw error;
  }

  public static Platform get() {
    return PLATFORM;
  }

  // Use nested class to ensure logger isn't initialized unless it is accessed once.
  private static final class LoggerHolder {
    static final String LOGGER_NAME = Tracer.class.getName();
    static final Logger LOG = Logger.getLogger(LOGGER_NAME);
  }

  /** Like {@link Logger#log(Level, String) */
  public void log(String msg, @Nullable Throwable thrown) {
    Logger logger = LoggerHolder.LOG;
    if (!logger.isLoggable(Level.FINE)) return; // fine level to not fill logs
    logger.log(Level.FINE, msg, thrown);
  }

  /** Like {@link Logger#log(Level, String, Object)}, except with a throwable arg */
  public void log(String msg, Object param1, @Nullable Throwable thrown) {
    Logger logger = LoggerHolder.LOG;
    if (!logger.isLoggable(Level.FINE)) return; // fine level to not fill logs
    LogRecord lr = new LogRecord(Level.FINE, msg);
    lr.setLoggerName(LoggerHolder.LOGGER_NAME);
    Object[] params = {param1};
    lr.setParameters(params);
    if (thrown != null) lr.setThrown(thrown);
    logger.log(lr);
  }

  /** Attempt to match the host runtime to a capable Platform implementation. */
  static Platform findPlatform() {
    // Find JRE 9 new methods
    try {
      Class zoneId = Class.forName("java.time.ZoneId");
      Class.forName("java.time.Clock").getMethod("tickMillis", zoneId);
      return new Jre9(); // intentionally doesn't access the type prior to the above guard
    } catch (ClassNotFoundException e) {
      // pre JRE 8
    } catch (NoSuchMethodException e) {
      // pre JRE 9
    }

    // Find JRE 7 new methods
    try {
      Class.forName("java.util.concurrent.ThreadLocalRandom");
      return new Jre7(); // intentionally doesn't access the type prior to the above guard
    } catch (ClassNotFoundException e) {
      // pre JRE 7
    }

    // compatible with JRE 6
    return new Jre6();
  }

  /**
   * This class uses pseudo-random number generators to provision IDs.
   *
   * <p>This optimizes speed over full coverage of 64-bits, which is why it doesn't share a {@link
   * SecureRandom}. It will use {@link java.util.concurrent.ThreadLocalRandom} unless used in JRE 6
   * which doesn't have the class.
   */
  public abstract long randomLong();

  /**
   * Returns the high 8-bytes for {@link brave.Tracing.Builder#traceId128Bit(boolean) 128-bit trace
   * IDs}.
   *
   * <p>The upper 4-bytes are epoch seconds and the lower 4-bytes are random. This makes it
   * convertible to <a href="http://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-request-tracing.html"></a>Amazon
   * X-Ray trace ID format v1</a>.
   */
  public abstract long nextTraceIdHigh();

  public Clock clock() {
    return new Clock() {
      @Override public long currentTimeMicroseconds() {
        return System.currentTimeMillis() * 1000;
      }

      @Override public String toString() {
        return "System.currentTimeMillis()";
      }
    };
  }

  static class Jre9 extends Jre7 {
    @Override public long currentTimeMicroseconds() {
      java.time.Instant instant = java.time.Clock.systemUTC().instant();
      return (instant.getEpochSecond() * 1000000) + (instant.getNano() / 1000);
    }

    @Override public Clock clock() {
      return new Clock() {
        // we could use jdk.internal.misc.VM to do this more efficiently, but it is internal
        @Override public long currentTimeMicroseconds() {
          return Jre9.this.currentTimeMicroseconds();
        }

        @Override public String toString() {
          return "Clock.systemUTC().instant()";
        }
      };
    }

    @Override public String toString() {
      return "Jre9{}";
    }
  }

  static class Jre7 extends Platform {
    @Override public long currentTimeMicroseconds() {
      return System.currentTimeMillis() * 1000;
    }

    @Override public long nanoTime() {
      return System.nanoTime();
    }

    @Override public String getHostString(InetSocketAddress socket) {
      return socket.getHostString();
    }

    @Override public long randomLong() {
      return java.util.concurrent.ThreadLocalRandom.current().nextLong();
    }

    @Override public long nextTraceIdHigh() {
      return nextTraceIdHigh(currentTimeMicroseconds(), java.util.concurrent.ThreadLocalRandom.current().nextInt());
    }

    @Override
    public AssertionError assertionError(String message, Throwable cause) {
      return new AssertionError(message, cause);
    }

    @Override public String toString() {
      return "Jre7{}";
    }
  }

  static long nextTraceIdHigh(long currentTimeMicroseconds, int random) {
    long epochSeconds = currentTimeMicroseconds / 1000000;
    return (epochSeconds & 0xffffffffL) << 32
      | (random & 0xffffffffL);
  }

  static class Jre6 extends Platform {
    @Override public long currentTimeMicroseconds() {
      return System.currentTimeMillis() * 1000;
    }

    @Override public long nanoTime() {
      return System.nanoTime();
    }

    @Override public String getHostString(InetSocketAddress socket) {
      return socket.getAddress().getHostAddress();
    }

    @Override public long randomLong() {
      return prng.nextLong();
    }

    @Override public long nextTraceIdHigh() {
      return nextTraceIdHigh(currentTimeMicroseconds(), prng.nextInt());
    }

    final Random prng;

    Jre6() {
      this.prng = new Random(nanoTime());
    }

    @Override public String toString() {
      return "Jre6{}";
    }
  }
}
