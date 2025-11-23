package com.ldapbrowser.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a log entry in the application activity log.
 * Contains information about operations performed, errors encountered,
 * and other significant events.
 */
public class LogEntry {
  
  /**
   * Log level enumeration.
   */
  public enum LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
  }
  
  private LocalDateTime timestamp;
  private LogLevel level;
  private String category;
  private String message;
  private String details;
  private String serverName;
  private String ldifData;
  
  /**
   * Default constructor for JSON deserialization.
   */
  public LogEntry() {
    this.timestamp = LocalDateTime.now();
  }
  
  /**
   * Constructor with all fields.
   *
   * @param level      the log level
   * @param category   the log category (e.g., IMPORT, EXPORT, BULK_GENERATE)
   * @param message    the log message
   * @param details    optional additional details
   * @param serverName optional server name context
   */
  public LogEntry(LogLevel level, String category, String message, 
                  String details, String serverName) {
    this(level, category, message, details, serverName, null);
  }
  
  /**
   * Constructor with all fields including LDIF data.
   *
   * @param level      the log level
   * @param category   the log category (e.g., IMPORT, EXPORT, BULK_GENERATE)
   * @param message    the log message
   * @param details    optional additional details
   * @param serverName optional server name context
   * @param ldifData   optional LDIF format data for modifications
   */
  public LogEntry(LogLevel level, String category, String message, 
                  String details, String serverName, String ldifData) {
    this.timestamp = LocalDateTime.now();
    this.level = level;
    this.category = category;
    this.message = message;
    this.details = details;
    this.serverName = serverName;
    this.ldifData = ldifData;
  }
  
  /**
   * Constructor without details and serverName.
   *
   * @param level    the log level
   * @param category the log category
   * @param message  the log message
   */
  public LogEntry(LogLevel level, String category, String message) {
    this(level, category, message, null, null);
  }
  
  // Getters and setters
  
  public LocalDateTime getTimestamp() {
    return timestamp;
  }
  
  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }
  
  public LogLevel getLevel() {
    return level;
  }
  
  public void setLevel(LogLevel level) {
    this.level = level;
  }
  
  public String getCategory() {
    return category;
  }
  
  public void setCategory(String category) {
    this.category = category;
  }
  
  public String getMessage() {
    return message;
  }
  
  public void setMessage(String message) {
    this.message = message;
  }
  
  public String getDetails() {
    return details;
  }
  
  public void setDetails(String details) {
    this.details = details;
  }
  
  public String getServerName() {
    return serverName;
  }
  
  public void setServerName(String serverName) {
    this.serverName = serverName;
  }
  
  public String getLdifData() {
    return ldifData;
  }
  
  public void setLdifData(String ldifData) {
    this.ldifData = ldifData;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LogEntry logEntry = (LogEntry) o;
    return Objects.equals(timestamp, logEntry.timestamp)
        && level == logEntry.level
        && Objects.equals(category, logEntry.category)
        && Objects.equals(message, logEntry.message);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(timestamp, level, category, message);
  }
  
  @Override
  public String toString() {
    return String.format("[%s] [%s] [%s] %s%s%s",
        timestamp,
        level,
        category,
        message,
        details != null ? " - " + details : "",
        serverName != null ? " (Server: " + serverName + ")" : "");
  }
}
