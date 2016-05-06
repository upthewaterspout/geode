/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gemstone.gemfire.internal.logging;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.async.AsyncLoggerConfigDelegate;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationListener;
import org.apache.logging.log4j.core.config.ConfigurationScheduler;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.CustomLevelConfig;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.ReliabilityStrategy;
import org.apache.logging.log4j.core.lookup.StrLookup;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.core.net.Advertiser;
import org.apache.logging.log4j.core.script.ScriptManager;
import org.apache.logging.log4j.core.util.WatchManager;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.MessageSupplier;
import org.apache.logging.log4j.util.Supplier;

import com.gemstone.gemfire.internal.logging.log4j.AppenderContext;
import com.gemstone.gemfire.internal.logging.log4j.ConfigLocator;
import com.gemstone.gemfire.internal.logging.log4j.Configurator;
import com.gemstone.gemfire.internal.logging.log4j.FastLogger;
import com.gemstone.gemfire.internal.logging.log4j.LogWriterLogger;
import com.gemstone.org.apache.logging.log4j.message.GemFireParameterizedMessageFactory;

/**
 * Centralizes log configuration and initialization.
 *
 */
@SuppressWarnings("unused")
public class LogService {
  // This is highest point in the hierarchy for all GemFire logging
  public static final String ROOT_LOGGER_NAME = "";
  public static final String BASE_LOGGER_NAME = "com.gemstone";
  public static final String MAIN_LOGGER_NAME = "com.gemstone.gemfire";
  public static final String SECURITY_LOGGER_NAME = "com.gemstone.gemfire.security";

  public static final String GEMFIRE_VERBOSE_FILTER = "{GEMFIRE_VERBOSE}";

  protected static final String STDOUT = "STDOUT";

  private static final PropertyChangeListener propertyChangeListener = new PropertyChangeListenerImpl();

  public static final String DEFAULT_CONFIG = "/log4j2.xml";
  public static final String CLI_CONFIG = "/log4j2-cli.xml";

  /**
   * Name of variable that is set to "true" in log4j2.xml to indicate that it is the default gemfire config xml.
   */
  private static final String GEMFIRE_DEFAULT_PROPERTY = "gemfire-default";

  /** Protected by static synchronization. Used for removal and adding stdout back in. */
  private static Appender stdoutAppender;

  static {
    init();
  }
  private static void init() {
//    LoggerContext contex//t = ((org.apache.logging.log4j.core.Logger) LogManager.getLogger(BASE_LOGGER_NAME, GemFireParameterizedMessageFactory.INSTANCE)).getContext();
//    context.removePropertyChangeListener(propertyChangeListener);
//    context.addPropertyChangeListener(propertyChangeListener);
//    context.reconfigure(); // propertyChangeListener invokes configureFastLoggerDelegating
//    configureLoggers(false, false);
  }

  public static void initialize() {
    new LogService();
  }

  public static void reconfigure() {
    init();
  }

  public static void configureLoggers(final boolean hasLogFile, final boolean hasSecurityLogFile) {
    Configurator.getOrCreateLoggerConfig(BASE_LOGGER_NAME, true, false);
    Configurator.getOrCreateLoggerConfig(MAIN_LOGGER_NAME, true, hasLogFile);
    final boolean useMainLoggerForSecurity = !hasSecurityLogFile;
    Configurator.getOrCreateLoggerConfig(SECURITY_LOGGER_NAME, useMainLoggerForSecurity, hasSecurityLogFile);
  }

  public static AppenderContext getAppenderContext() {
    return new AppenderContext();
  }

  public static AppenderContext getAppenderContext(final String name) {
    return new AppenderContext(name);
  }

  public static boolean isUsingGemFireDefaultConfig() {
    final Configuration config = ((org.apache.logging.log4j.core.Logger)
      LogManager.getLogger(ROOT_LOGGER_NAME, GemFireParameterizedMessageFactory.INSTANCE)).getContext().getConfiguration();

    final StrSubstitutor sub = config.getStrSubstitutor();
    final StrLookup resolver = sub.getVariableResolver();

    final String value = resolver.lookup(GEMFIRE_DEFAULT_PROPERTY);

    return "true".equals(value);
  }

  public static String getConfigInformation() {
    return getConfiguration().getConfigurationSource().toString();
  }

  /**
   * Finds a Log4j configuration file in the current directory.  The names of
   * the files to look for are the same as those that Log4j would look for on
   * the classpath.
   *
   * @return A File for the configuration file or null if one isn't found.
   */
  public static File findLog4jConfigInCurrentDir() {
    return ConfigLocator.findConfigInWorkingDirectory();
  }

  /**
   * Returns a Logger with the name of the calling class.
   * @return The Logger for the calling class.
   */
  public static Logger getLogger() {
//    return new FastLogger(LogManager.getLogger(getClassName(2), GemFireParameterizedMessageFactory.INSTANCE));
    return new Logger() {

      @Override public void catching(final Level level, final Throwable t) {

      }

      @Override public void catching(final Throwable t) {

      }

      @Override public void debug(final Marker marker, final Message msg) {

      }

      @Override public void debug(final Marker marker, final Message msg, final Throwable t) {

      }

      @Override public void debug(final Marker marker, final MessageSupplier msgSupplier) {

      }

      @Override public void debug(final Marker marker, final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void debug(final Marker marker, final Object message) {

      }

      @Override public void debug(final Marker marker, final Object message, final Throwable t) {

      }

      @Override public void debug(final Marker marker, final String message) {

      }

      @Override public void debug(final Marker marker, final String message, final Object... params) {

      }

      @Override public void debug(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void debug(final Marker marker, final String message, final Throwable t) {

      }

      @Override public void debug(final Marker marker, final Supplier<?> msgSupplier) {

      }

      @Override public void debug(final Marker marker, final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public void debug(final Message msg) {

      }

      @Override public void debug(final Message msg, final Throwable t) {

      }

      @Override public void debug(final MessageSupplier msgSupplier) {

      }

      @Override public void debug(final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void debug(final Object message) {

      }

      @Override public void debug(final Object message, final Throwable t) {

      }

      @Override public void debug(final String message) {

      }

      @Override public void debug(final String message, final Object... params) {

      }

      @Override public void debug(final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void debug(final String message, final Throwable t) {

      }

      @Override public void debug(final Supplier<?> msgSupplier) {

      }

      @Override public void debug(final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public void entry() {

      }

      @Override public void entry(final Object... params) {

      }

      @Override public void error(final Marker marker, final Message msg) {

      }

      @Override public void error(final Marker marker, final Message msg, final Throwable t) {

      }

      @Override public void error(final Marker marker, final MessageSupplier msgSupplier) {

      }

      @Override public void error(final Marker marker, final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void error(final Marker marker, final Object message) {

      }

      @Override public void error(final Marker marker, final Object message, final Throwable t) {

      }

      @Override public void error(final Marker marker, final String message) {

      }

      @Override public void error(final Marker marker, final String message, final Object... params) {

      }

      @Override public void error(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void error(final Marker marker, final String message, final Throwable t) {

      }

      @Override public void error(final Marker marker, final Supplier<?> msgSupplier) {

      }

      @Override public void error(final Marker marker, final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public void error(final Message msg) {

      }

      @Override public void error(final Message msg, final Throwable t) {

      }

      @Override public void error(final MessageSupplier msgSupplier) {

      }

      @Override public void error(final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void error(final Object message) {

      }

      @Override public void error(final Object message, final Throwable t) {

      }

      @Override public void error(final String message) {

      }

      @Override public void error(final String message, final Object... params) {

      }

      @Override public void error(final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void error(final String message, final Throwable t) {

      }

      @Override public void error(final Supplier<?> msgSupplier) {

      }

      @Override public void error(final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public void exit() {

      }

      @Override public <R> R exit(final R result) {
        return null;
      }

      @Override public void fatal(final Marker marker, final Message msg) {

      }

      @Override public void fatal(final Marker marker, final Message msg, final Throwable t) {

      }

      @Override public void fatal(final Marker marker, final MessageSupplier msgSupplier) {

      }

      @Override public void fatal(final Marker marker, final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void fatal(final Marker marker, final Object message) {

      }

      @Override public void fatal(final Marker marker, final Object message, final Throwable t) {

      }

      @Override public void fatal(final Marker marker, final String message) {

      }

      @Override public void fatal(final Marker marker, final String message, final Object... params) {

      }

      @Override public void fatal(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void fatal(final Marker marker, final String message, final Throwable t) {

      }

      @Override public void fatal(final Marker marker, final Supplier<?> msgSupplier) {

      }

      @Override public void fatal(final Marker marker, final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public void fatal(final Message msg) {

      }

      @Override public void fatal(final Message msg, final Throwable t) {

      }

      @Override public void fatal(final MessageSupplier msgSupplier) {

      }

      @Override public void fatal(final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void fatal(final Object message) {

      }

      @Override public void fatal(final Object message, final Throwable t) {

      }

      @Override public void fatal(final String message) {

      }

      @Override public void fatal(final String message, final Object... params) {

      }

      @Override public void fatal(final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void fatal(final String message, final Throwable t) {

      }

      @Override public void fatal(final Supplier<?> msgSupplier) {

      }

      @Override public void fatal(final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public Level getLevel() {
        return null;
      }

      @Override public MessageFactory getMessageFactory() {
        return null;
      }

      @Override public String getName() {
        return null;
      }

      @Override public void info(final Marker marker, final Message msg) {

      }

      @Override public void info(final Marker marker, final Message msg, final Throwable t) {

      }

      @Override public void info(final Marker marker, final MessageSupplier msgSupplier) {

      }

      @Override public void info(final Marker marker, final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void info(final Marker marker, final Object message) {

      }

      @Override public void info(final Marker marker, final Object message, final Throwable t) {

      }

      @Override public void info(final Marker marker, final String message) {

      }

      @Override public void info(final Marker marker, final String message, final Object... params) {

      }

      @Override public void info(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void info(final Marker marker, final String message, final Throwable t) {

      }

      @Override public void info(final Marker marker, final Supplier<?> msgSupplier) {

      }

      @Override public void info(final Marker marker, final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public void info(final Message msg) {

      }

      @Override public void info(final Message msg, final Throwable t) {

      }

      @Override public void info(final MessageSupplier msgSupplier) {

      }

      @Override public void info(final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void info(final Object message) {

      }

      @Override public void info(final Object message, final Throwable t) {

      }

      @Override public void info(final String message) {

      }

      @Override public void info(final String message, final Object... params) {

      }

      @Override public void info(final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void info(final String message, final Throwable t) {

      }

      @Override public void info(final Supplier<?> msgSupplier) {

      }

      @Override public void info(final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public boolean isDebugEnabled() {
        return false;
      }

      @Override public boolean isDebugEnabled(final Marker marker) {
        return false;
      }

      @Override public boolean isEnabled(final Level level) {
        return false;
      }

      @Override public boolean isEnabled(final Level level, final Marker marker) {
        return false;
      }

      @Override public boolean isErrorEnabled() {
        return false;
      }

      @Override public boolean isErrorEnabled(final Marker marker) {
        return false;
      }

      @Override public boolean isFatalEnabled() {
        return false;
      }

      @Override public boolean isFatalEnabled(final Marker marker) {
        return false;
      }

      @Override public boolean isInfoEnabled() {
        return false;
      }

      @Override public boolean isInfoEnabled(final Marker marker) {
        return false;
      }

      @Override public boolean isTraceEnabled() {
        return false;
      }

      @Override public boolean isTraceEnabled(final Marker marker) {
        return false;
      }

      @Override public boolean isWarnEnabled() {
        return false;
      }

      @Override public boolean isWarnEnabled(final Marker marker) {
        return false;
      }

      @Override public void log(final Level level, final Marker marker, final Message msg) {

      }

      @Override public void log(final Level level, final Marker marker, final Message msg, final Throwable t) {

      }

      @Override public void log(final Level level, final Marker marker, final MessageSupplier msgSupplier) {

      }

      @Override
      public void log(final Level level, final Marker marker, final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void log(final Level level, final Marker marker, final Object message) {

      }

      @Override public void log(final Level level, final Marker marker, final Object message, final Throwable t) {

      }

      @Override public void log(final Level level, final Marker marker, final String message) {

      }

      @Override public void log(final Level level, final Marker marker, final String message, final Object... params) {

      }

      @Override
      public void log(final Level level,
                      final Marker marker,
                      final String message,
                      final Supplier<?>... paramSuppliers)
      {

      }

      @Override public void log(final Level level, final Marker marker, final String message, final Throwable t) {

      }

      @Override public void log(final Level level, final Marker marker, final Supplier<?> msgSupplier) {

      }

      @Override
      public void log(final Level level, final Marker marker, final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public void log(final Level level, final Message msg) {

      }

      @Override public void log(final Level level, final Message msg, final Throwable t) {

      }

      @Override public void log(final Level level, final MessageSupplier msgSupplier) {

      }

      @Override public void log(final Level level, final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void log(final Level level, final Object message) {

      }

      @Override public void log(final Level level, final Object message, final Throwable t) {

      }

      @Override public void log(final Level level, final String message) {

      }

      @Override public void log(final Level level, final String message, final Object... params) {

      }

      @Override public void log(final Level level, final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void log(final Level level, final String message, final Throwable t) {

      }

      @Override public void log(final Level level, final Supplier<?> msgSupplier) {

      }

      @Override public void log(final Level level, final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override
      public void printf(final Level level, final Marker marker, final String format, final Object... params) {

      }

      @Override public void printf(final Level level, final String format, final Object... params) {

      }

      @Override public <T extends Throwable> T throwing(final Level level, final T t) {
        return null;
      }

      @Override public <T extends Throwable> T throwing(final T t) {
        return null;
      }

      @Override public void trace(final Marker marker, final Message msg) {

      }

      @Override public void trace(final Marker marker, final Message msg, final Throwable t) {

      }

      @Override public void trace(final Marker marker, final MessageSupplier msgSupplier) {

      }

      @Override public void trace(final Marker marker, final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void trace(final Marker marker, final Object message) {

      }

      @Override public void trace(final Marker marker, final Object message, final Throwable t) {

      }

      @Override public void trace(final Marker marker, final String message) {

      }

      @Override public void trace(final Marker marker, final String message, final Object... params) {

      }

      @Override public void trace(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void trace(final Marker marker, final String message, final Throwable t) {

      }

      @Override public void trace(final Marker marker, final Supplier<?> msgSupplier) {

      }

      @Override public void trace(final Marker marker, final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public void trace(final Message msg) {

      }

      @Override public void trace(final Message msg, final Throwable t) {

      }

      @Override public void trace(final MessageSupplier msgSupplier) {

      }

      @Override public void trace(final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void trace(final Object message) {

      }

      @Override public void trace(final Object message, final Throwable t) {

      }

      @Override public void trace(final String message) {

      }

      @Override public void trace(final String message, final Object... params) {

      }

      @Override public void trace(final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void trace(final String message, final Throwable t) {

      }

      @Override public void trace(final Supplier<?> msgSupplier) {

      }

      @Override public void trace(final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public void warn(final Marker marker, final Message msg) {

      }

      @Override public void warn(final Marker marker, final Message msg, final Throwable t) {

      }

      @Override public void warn(final Marker marker, final MessageSupplier msgSupplier) {

      }

      @Override public void warn(final Marker marker, final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void warn(final Marker marker, final Object message) {

      }

      @Override public void warn(final Marker marker, final Object message, final Throwable t) {

      }

      @Override public void warn(final Marker marker, final String message) {

      }

      @Override public void warn(final Marker marker, final String message, final Object... params) {

      }

      @Override public void warn(final Marker marker, final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void warn(final Marker marker, final String message, final Throwable t) {

      }

      @Override public void warn(final Marker marker, final Supplier<?> msgSupplier) {

      }

      @Override public void warn(final Marker marker, final Supplier<?> msgSupplier, final Throwable t) {

      }

      @Override public void warn(final Message msg) {

      }

      @Override public void warn(final Message msg, final Throwable t) {

      }

      @Override public void warn(final MessageSupplier msgSupplier) {

      }

      @Override public void warn(final MessageSupplier msgSupplier, final Throwable t) {

      }

      @Override public void warn(final Object message) {

      }

      @Override public void warn(final Object message, final Throwable t) {

      }

      @Override public void warn(final String message) {

      }

      @Override public void warn(final String message, final Object... params) {

      }

      @Override public void warn(final String message, final Supplier<?>... paramSuppliers) {

      }

      @Override public void warn(final String message, final Throwable t) {

      }

      @Override public void warn(final Supplier<?> msgSupplier) {

      }

      @Override public void warn(final Supplier<?> msgSupplier, final Throwable t) {

      }
    };
  }

  public static Logger getLogger(final String name) {
//    return new FastLogger(LogManager.getLogger(name, GemFireParameterizedMessageFactory.INSTANCE));
    return getLogger();
  }

  /**
   * Returns a LogWriterLogger that is decorated with the LogWriter and LogWriterI18n
   * methods.
   *
   * This is the bridge to LogWriter and LogWriterI18n that we need to eventually
   * stop using in phase 1. We will switch over from a shared LogWriterLogger instance
   * to having every GemFire class own its own private static GemFireLogger
   *
   * @return The LogWriterLogger for the calling class.
   */
  public static LogWriterLogger createLogWriterLogger(final String name, final String connectionName, final boolean isSecure) {
    return LogWriterLogger.create(name, connectionName, isSecure);
  }

  /**
   * Return the Log4j Level associated with the int level.
   *
   * @param intLevel
   *          The int value of the Level to return.
   * @return The Level.
   * @throws java.lang.IllegalArgumentException if the Level int is not registered.
   */
  public static Level toLevel(final int intLevel) {
    for (Level level : Level.values()) {
      if (level.intLevel() == intLevel) {
        return level;
      }
    }

    throw new IllegalArgumentException("Unknown int level [" + intLevel + "].");
  }

  /**
   * Gets the class name of the caller in the current stack at the given {@code depth}.
   *
   * @param depth a 0-based index in the current stack.
   * @return a class name
   */
  public static String getClassName(final int depth) {
    return new Throwable().getStackTrace()[depth].getClassName();
  }

  public static Configuration getConfiguration() {
    return new Configuration() {
      @Override public String getName() {
        return null;
      }

      @Override public LoggerConfig getLoggerConfig(final String name) {
        return null;
      }

      @Override public <T extends Appender> T getAppender(final String name) {
        return null;
      }

      @Override public Map<String, Appender> getAppenders() {
        return null;
      }

      @Override public void addAppender(final Appender appender) {

      }

      @Override public Map<String, LoggerConfig> getLoggers() {
        return null;
      }

      @Override
      public void addLoggerAppender(final org.apache.logging.log4j.core.Logger logger, final Appender appender) {

      }

      @Override public void addLoggerFilter(final org.apache.logging.log4j.core.Logger logger, final Filter filter) {

      }

      @Override
      public void setLoggerAdditive(final org.apache.logging.log4j.core.Logger logger, final boolean additive) {

      }

      @Override public void addLogger(final String name, final LoggerConfig loggerConfig) {

      }

      @Override public void removeLogger(final String name) {

      }

      @Override public List<String> getPluginPackages() {
        return null;
      }

      @Override public Map<String, String> getProperties() {
        return null;
      }

      @Override public LoggerConfig getRootLogger() {
        return null;
      }

      @Override public void addListener(final ConfigurationListener listener) {

      }

      @Override public void removeListener(final ConfigurationListener listener) {

      }

      @Override public StrSubstitutor getStrSubstitutor() {
        return null;
      }

      @Override public void createConfiguration(final Node node, final LogEvent event) {

      }

      @Override public <T> T getComponent(final String name) {
        return null;
      }

      @Override public void addComponent(final String name, final Object object) {

      }

      @Override public void setAdvertiser(final Advertiser advertiser) {

      }

      @Override public Advertiser getAdvertiser() {
        return null;
      }

      @Override public boolean isShutdownHookEnabled() {
        return false;
      }

      @Override public ConfigurationScheduler getScheduler() {
        return null;
      }

      @Override public ConfigurationSource getConfigurationSource() {
        return null;
      }

      @Override public List<CustomLevelConfig> getCustomLevels() {
        return null;
      }

      @Override public ScriptManager getScriptManager() {
        return null;
      }

      @Override public AsyncLoggerConfigDelegate getAsyncLoggerConfigDelegate() {
        return null;
      }

      @Override public WatchManager getWatchManager() {
        return null;
      }

      @Override public ReliabilityStrategy getReliabilityStrategy(final LoggerConfig loggerConfig) {
        return null;
      }

      @Override public void addFilter(final Filter filter) {

      }

      @Override public void removeFilter(final Filter filter) {

      }

      @Override public Filter getFilter() {
        return null;
      }

      @Override public boolean hasFilter() {
        return false;
      }

      @Override public boolean isFiltered(final LogEvent event) {
        return false;
      }

      @Override public State getState() {
        return null;
      }

      @Override public void initialize() {

      }

      @Override public void start() {

      }

      @Override public void stop() {

      }

      @Override public boolean isStarted() {
        return false;
      }

      @Override public boolean isStopped() {
        return false;
      }
    };
  }

  public static void configureFastLoggerDelegating() {
//    final Configuration config = ((org.apache.logging.log4j.core.Logger)
//      LogManager.getLogger(ROOT_LOGGER_NAME, GemFireParameterizedMessageFactory.INSTANCE)).getContext().getConfiguration();
//
//    if (Configurator.hasContextWideFilter(config) ||
//      Configurator.hasAppenderFilter(config) ||
//      Configurator.hasDebugOrLower(config) ||
//      Configurator.hasLoggerFilter(config) ||
//      Configurator.hasAppenderRefFilter(config)) {
//      FastLogger.setDelegating(true);
//    } else {
//      FastLogger.setDelegating(false);
//    }
  }

  private static class PropertyChangeListenerImpl implements PropertyChangeListener {
    @SuppressWarnings("synthetic-access")
    @Override
    public void propertyChange(final PropertyChangeEvent evt) {
      StatusLogger.getLogger().debug("LogService responding to a property change event. Property name is {}.",
        evt.getPropertyName());

      if (evt.getPropertyName().equals(LoggerContext.PROPERTY_CONFIG)) {
        configureFastLoggerDelegating();
      }
    }
  }

  public static void setBaseLogLevel(Level level) {
    if (isUsingGemFireDefaultConfig()) {
      Configurator.setLevel(ROOT_LOGGER_NAME, level);
    }
    Configurator.setLevel(BASE_LOGGER_NAME, level);
    Configurator.setLevel(MAIN_LOGGER_NAME, level);
  }

  public static void setSecurityLogLevel(Level level) {
    Configurator.setLevel(SECURITY_LOGGER_NAME, level);
  }

  public static Level getBaseLogLevel() {
    return Configurator.getLevel(BASE_LOGGER_NAME);
  }

  public static LoggerConfig getRootLoggerConfig() {
    return Configurator.getLoggerConfig("root");
  }

  /**
   * Removes STDOUT ConsoleAppender from ROOT logger. Only called when using
   * the log4j2-default.xml configuration. This is done when creating the
   * LogWriterAppender for log-file. The Appender instance is stored in
   * stdoutAppender so it can be restored later using restoreConsoleAppender.
   */
  public static synchronized void removeConsoleAppender() {
    final AppenderContext appenderContext = LogService.getAppenderContext(LogService.ROOT_LOGGER_NAME);
    final LoggerConfig config = appenderContext.getLoggerConfig();
    Appender stdout = config.getAppenders().get(STDOUT);
    if (stdout != null) {
      config.removeAppender(STDOUT);
      stdoutAppender = stdout;
      appenderContext.getLoggerContext().updateLoggers();
    }
  }

  /**
   * Restores STDOUT ConsoleAppender to ROOT logger. Only called when using
   * the log4j2-default.xml configuration. This is done when the
   * LogWriterAppender for log-file is destroyed. The Appender instance stored
   * in stdoutAppender is used.
   */
  public static synchronized void restoreConsoleAppender() {
    if (stdoutAppender == null) {
      return;
    }
    final AppenderContext appenderContext = LogService.getAppenderContext(LogService.ROOT_LOGGER_NAME);
    final LoggerConfig config = appenderContext.getLoggerConfig();
    Appender stdout = config.getAppenders().get(STDOUT);
    if (stdout == null) {
      config.addAppender(stdoutAppender, Level.ALL, null);
      appenderContext.getLoggerContext().updateLoggers();
    }
  }

  private LogService() {
    // do not instantiate
  }
}