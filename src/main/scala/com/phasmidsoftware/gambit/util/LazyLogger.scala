/*
 * Copyright (c) 2026. Phasmid Software
 */

package com.phasmidsoftware.gambit.util

import org.slf4j.{Logger, LoggerFactory}

/**
  * A wrapper around the standard logging mechanism that defers the evaluation of log messages
  * until it's determined necessary.
  * This approach ensures that the computational cost of
  * generating log messages is only incurred when the corresponding log level is enabled.
  *
  * @constructor Creates a new LazyLogger instance with the provided underlying logger.
  * @param logger The underlying Logger instance used for actual logging operations.
  * @define debug Logs a message at the  "DEBUG" level if debug logging is enabled.
  *                                     The message is lazily evaluated.
  *
  * @define info  Logs a message at the  "INFO" level if info logging is enabled.
  *                                     The message is lazily evaluated.
  *
  * @define warn  Logs a message at the  "WARN" level if warn logging is enabled.
  *                                     The message is lazily evaluated.
  *
  * @define error Logs a message at the  "ERROR" level if error logging is enabled.
  *                                     The message is lazily evaluated.
  */
case class LazyLogger(logger: Logger):
  /**
    * Logs a message at the "DEBUG" level if debug logging is enabled.
    * The message is lazily evaluated, meaning it will only be constructed
    * if the debug level is enabled, saving computation when not necessary.
    *
    * @param msg The message to be logged at the debug level.
    *            The message is provided as a by-name parameter, allowing for lazy evaluation.
    *
    * @return Unit, as this method performs side effects of logging the debug message.
    */
  def debug(msg: => String): Unit =
    if logger.isDebugEnabled then logger.debug(msg)

  /**
    * Logs a message at the "INFO" level if info logging is enabled.
    * The message is lazily evaluated to avoid unnecessary computation.
    *
    * @param msg The log message to be evaluated and logged if the "INFO" level is enabled.
    * @return Unit
    */
  def info(msg: => String): Unit =
    if logger.isInfoEnabled then logger.info(msg)

  /**
    * Logs a message at the "WARN" level if warn logging is enabled.
    * The message is lazily evaluated, meaning it will only be constructed
    * if the warn level is enabled, saving computation when not necessary.
    *
    * @param msg The message to be logged at the warn level.
    *            The message is provided as a by-name parameter, allowing for lazy evaluation.
    *
    * @return Unit, as this method performs side effects of logging the warn message.
    */
  def warn(msg: => String): Unit =
    if logger.isWarnEnabled then logger.warn(msg)

  /**
    * Logs a message at the "ERROR" level if error logging is enabled.
    * The message is lazily evaluated, ensuring that it is only constructed
    * if the error level is enabled, thereby avoiding unnecessary computation.
    *
    * @param msg The message to be logged at the error level.
    *            The message is provided as a by-name parameter, allowing for lazy evaluation.
    *
    * @return Unit, as this method performs the side effect of logging the error message.
    */
  def error(msg: => String): Unit =
    if logger.isErrorEnabled then logger.error(msg)

/**
  * A logger designed to support lazy message evaluation for optimal performance, particularly
  * in scenarios where the log message construction is resource-intensive.
  * The logger wraps an underlying logger instance and provides methods for logging messages
  * at various log levels, such as DEBUG, INFO, WARN, and ERROR.
  *
  * Lazy evaluation ensures that log messages are only constructed when the corresponding
  * log level is enabled, reducing unnecessary computation and improving efficiency.
  *
  * The logger operates using SLF4J as its backend, enabling compatibility with a wide range
  * of logging frameworks.
  */
object LazyLogger:
  /**
    * Creates a new instance of `LazyLogger` for the given class.
    *
    * @param clazz The class for which the logger will be created. Typically, this is
    *              the class where the logger is used, allowing log messages to
    *              include the class name for better traceability.
    *
    * @return A `LazyLogger` instance wrapping the underlying logger for the specified class.
    */
  def apply(clazz: Class[?]): LazyLogger = LazyLogger(LoggerFactory.getLogger(clazz))

