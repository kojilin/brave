/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.test.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.util.log.AbstractLogger;

public final class Log4J2Log extends AbstractLogger {
  final Logger logger;

  public Log4J2Log() {
    this("org.eclipse.jetty.util.log");
  }

  Log4J2Log(String name) {
    this.logger = LogManager.getLogger(name);
  }

  @Override public String getName() {
    return logger.getName();
  }

  @Override public void warn(String msg, Object... args) {
    logger.warn(msg, args);
  }

  @Override public void warn(Throwable thrown) {
    warn("", thrown);
  }

  @Override public void warn(String msg, Throwable thrown) {
    logger.warn(msg, thrown);
  }

  @Override public void info(String msg, Object... args) {
    logger.info(msg, args);
  }

  @Override public void info(Throwable thrown) {
    this.info("", thrown);
  }

  @Override public void info(String msg, Throwable thrown) {
    logger.info(msg, thrown);
  }

  @Override public void debug(String msg, Object... args) {
    logger.debug(msg, args);
  }

  @Override public void debug(Throwable thrown) {
    this.debug("", thrown);
  }

  @Override public void debug(String msg, Throwable thrown) {
    logger.debug(msg, thrown);
  }

  @Override public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  @Override public void setDebugEnabled(boolean enabled) {
    this.warn("setDebugEnabled not implemented");
  }

  @Override protected org.eclipse.jetty.util.log.Logger newLogger(String fullname) {
    return new Log4J2Log(fullname);
  }

  @Override public void ignore(Throwable ignored) {
  }

  @Override public String toString() {
    return logger.toString();
  }
}
