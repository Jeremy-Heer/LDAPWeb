package com.ldapbrowser.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ldapbrowser.model.LogEntry;
import com.ldapbrowser.model.LogEntry.LogLevel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for detailed logging of LDAP operations and schema comparisons.
 * Provides structured logging methods for tracking schema comparison
 * operations with varying levels of detail.
 * Also maintains an in-memory buffer and persists logs to a file for UI display.
 */
@Service
public class LoggingService {

  private static final Logger logger = LoggerFactory.getLogger(LoggingService.class);
  private static final int MAX_LOG_ENTRIES = 500;

  /** Listener interface for log entry updates. */
  public interface LogUpdateListener {
    void onLogAdded(LogEntry entry);
  }
  
  @Value("${ldapbrowser.settings.dir}")
  private String settingsDir;
  
  private final LinkedList<LogEntry> logBuffer = new LinkedList<>();
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private final ObjectMapper objectMapper;
  private final List<LogUpdateListener> listeners = new CopyOnWriteArrayList<>();
  private Path logFilePath;
  
  /**
   * Constructor initializes ObjectMapper with Java 8 time support.
   */
  public LoggingService() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
  }
  
  /**
   * Initializes the logging service after dependency injection.
   * Loads existing logs from file if available.
   */
  @PostConstruct
  public void init() {
    try {
      // Expand user home directory
      String expandedPath = settingsDir.replace("${user.home}", System.getProperty("user.home"));
      Path settingsDirPath = Paths.get(expandedPath);
      
      // Create settings directory if it doesn't exist
      if (!Files.exists(settingsDirPath)) {
        Files.createDirectories(settingsDirPath);
        logger.info("Created settings directory: {}", settingsDirPath);
      }
      
      logFilePath = settingsDirPath.resolve("activity.log");
      
      // Load existing logs if file exists
      if (Files.exists(logFilePath)) {
        loadLogsFromFile();
      }
      
      logger.info("LoggingService initialized. Log file: {}", logFilePath);
    } catch (Exception e) {
      logger.error("Failed to initialize LoggingService", e);
    }
  }
  
  /**
   * Cleanup when service is destroyed.
   */
  @PreDestroy
  public void cleanup() {
    executorService.shutdown();
  }
  
  /**
   * Loads logs from the activity log file.
   */
  private void loadLogsFromFile() {
    lock.writeLock().lock();
    try {
      List<LogEntry> entries = objectMapper.readValue(
          logFilePath.toFile(),
          new TypeReference<List<LogEntry>>() {}
      );
      
      // Load most recent entries up to MAX_LOG_ENTRIES
      logBuffer.clear();
      int startIndex = Math.max(0, entries.size() - MAX_LOG_ENTRIES);
      logBuffer.addAll(entries.subList(startIndex, entries.size()));
      
      logger.debug("Loaded {} log entries from file", logBuffer.size());
    } catch (IOException e) {
      logger.error("Failed to load logs from file: {}", logFilePath, e);
    } finally {
      lock.writeLock().unlock();
    }
  }
  
  /**
   * Saves logs to the activity log file asynchronously.
   */
  private void saveLogsToFile() {
    executorService.submit(() -> {
      lock.readLock().lock();
      try {
        List<LogEntry> entriesToSave = new ArrayList<>(logBuffer);
        lock.readLock().unlock();
        
        objectMapper.writeValue(logFilePath.toFile(), entriesToSave);
        logger.trace("Saved {} log entries to file", entriesToSave.size());
      } catch (IOException e) {
        logger.error("Failed to save logs to file: {}", logFilePath, e);
      } catch (Exception e) {
        lock.readLock().unlock();
        logger.error("Error during log file save", e);
      }
    });
  }
  
  /**
   * Adds a log entry to the buffer and persists to file.
   *
   * @param entry the log entry to add
   */
  private void addLogEntry(LogEntry entry) {
    lock.writeLock().lock();
    try {
      logBuffer.add(entry);
      
      // Remove oldest entries if buffer exceeds max size
      while (logBuffer.size() > MAX_LOG_ENTRIES) {
        logBuffer.removeFirst();
      }
    } finally {
      lock.writeLock().unlock();
    }
    
    // Save to file asynchronously
    saveLogsToFile();
    
    // Notify listeners outside the lock to avoid potential deadlocks
    notifyListeners(entry);
  }
  
  /**
   * Gets recent log entries.
   *
   * @param limit maximum number of entries to return (0 for all)
   * @return list of log entries
   */
  public List<LogEntry> getRecentLogs(int limit) {
    lock.readLock().lock();
    try {
      if (limit <= 0 || limit >= logBuffer.size()) {
        return new ArrayList<>(logBuffer);
      }
      
      // Return most recent entries
      int startIndex = logBuffer.size() - limit;
      return new ArrayList<>(logBuffer.subList(startIndex, logBuffer.size()));
    } finally {
      lock.readLock().unlock();
    }
  }
  
  /**
   * Gets log entries filtered by category.
   *
   * @param category the category to filter by
   * @return list of log entries matching the category
   */
  public List<LogEntry> getLogsByCategory(String category) {
    lock.readLock().lock();
    try {
      return logBuffer.stream()
          .filter(entry -> category.equals(entry.getCategory()))
          .collect(Collectors.toList());
    } finally {
      lock.readLock().unlock();
    }
  }
  
  /**
   * Gets log entries filtered by level.
   *
   * @param level the log level to filter by
   * @return list of log entries matching the level
   */
  public List<LogEntry> getLogsByLevel(LogLevel level) {
    lock.readLock().lock();
    try {
      return logBuffer.stream()
          .filter(entry -> level == entry.getLevel())
          .collect(Collectors.toList());
    } finally {
      lock.readLock().unlock();
    }
  }
  
  /**
   * Clears all log entries from memory and file.
   */
  public void clearLogs() {
    lock.writeLock().lock();
    try {
      logBuffer.clear();
      saveLogsToFile();
      logger.info("Cleared all log entries");
    } finally {
      lock.writeLock().unlock();
    }
  }
  
  /**
   * Gets the total count of log entries in the buffer.
   *
   * @return the number of log entries
   */
  public int getLogCount() {
    lock.readLock().lock();
    try {
      return logBuffer.size();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Registers a listener to receive notifications when new log entries are added.
   *
   * @param listener the listener to register
   */
  public void addListener(LogUpdateListener listener) {
    listeners.add(listener);
  }

  /**
   * Unregisters a listener from receiving log update notifications.
   *
   * @param listener the listener to unregister
   */
  public void removeListener(LogUpdateListener listener) {
    listeners.remove(listener);
  }

  /**
   * Notifies all registered listeners about a new log entry.
   *
   * @param entry the new log entry
   */
  private void notifyListeners(LogEntry entry) {
    for (LogUpdateListener listener : listeners) {
      try {
        listener.onLogAdded(entry);
      } catch (Exception e) {
        logger.error("Error notifying listener about log update", e);
      }
    }
  }
  
  /**
   * Gets the log file path.
   *
   * @return the log file path
   */
  public Path getLogFilePath() {
    return logFilePath;
  }

  /**
   * Logs general information message.
   *
   * @param category the category or context of the log
   * @param message the message to log
   */
  public void logInfo(String category, String message) {
    logger.info("[{}] {}", category, message);
    addLogEntry(new LogEntry(LogLevel.INFO, category, message));
  }

  /**
   * Logs a debug message.
   *
   * @param category the category or context of the log
   * @param message the message to log
   */
  public void logDebug(String category, String message) {
    logger.debug("[{}] {}", category, message);
    addLogEntry(new LogEntry(LogLevel.DEBUG, category, message));
  }

  /**
   * Logs an error message.
   *
   * @param category the category or context of the log
   * @param message the message to log
   */
  public void logError(String category, String message) {
    logger.error("[{}] {}", category, message);
    addLogEntry(new LogEntry(LogLevel.ERROR, category, message));
  }

  /**
   * Logs an error message with details.
   *
   * @param category the category or context of the log
   * @param message the message to log
   * @param details additional error details
   */
  public void logError(String category, String message, String details) {
    logger.error("[{}] {} - {}", category, message, details);
    addLogEntry(new LogEntry(LogLevel.ERROR, category, message, details, null));
  }

  /**
   * Logs the start of schema comparison for a server.
   *
   * @param serverName the server name
   * @param elementType the schema element type being compared
   * @param elementCount the number of elements to process
   */
  public void logSchemaComparisonStart(String serverName, String elementType, int elementCount) {
    logger.debug("[SCHEMA] Processing {} {} elements from server: {}", 
        elementCount, elementType, serverName);
  }

  /**
   * Logs the end of schema comparison for a server.
   *
   * @param serverName the server name
   * @param elementType the schema element type being compared
   * @param processedCount the number of elements successfully processed
   * @param errorCount the number of errors encountered
   */
  public void logSchemaComparisonEnd(String serverName, String elementType, 
      int processedCount, int errorCount) {
    if (errorCount > 0) {
      logger.debug("[SCHEMA] Completed {} comparison for {}: {} processed, {} errors", 
          elementType, serverName, processedCount, errorCount);
    } else {
      logger.debug("[SCHEMA] Completed {} comparison for {}: {} processed", 
          elementType, serverName, processedCount);
    }
  }

  /**
   * Logs detailed schema element information during comparison.
   *
   * @param serverName the server name
   * @param elementType the schema element type
   * @param elementName the element name
   * @param originalValue the original element definition
   * @param canonicalValue the canonicalized element definition
   * @param checksum the computed checksum
   */
  public void logSchemaElement(String serverName, String elementType, String elementName,
      String originalValue, String canonicalValue, String checksum) {
    logger.trace("[SCHEMA] Server: {} | Type: {} | Name: {} | Checksum: {}", 
        serverName, elementType, elementName, checksum);
    logger.trace("[SCHEMA] Original: {}", originalValue);
    logger.trace("[SCHEMA] Canonical: {}", canonicalValue);
  }

  /**
   * Logs schema element errors during comparison.
   *
   * @param serverName the server name
   * @param elementType the schema element type
   * @param elementName the element name
   * @param error the error message
   */
  public void logSchemaError(String serverName, String elementType, 
      String elementName, String error) {
    logger.debug("[SCHEMA] Error processing {} '{}' from {}: {}", 
        elementType, elementName, serverName, error);
  }

  /**
   * Logs a schema comparison mismatch.
   *
   * @param elementType the schema element type
   * @param elementName the element name
   * @param serverCount the number of servers with differing definitions
   */
  public void logSchemaMismatch(String elementType, String elementName, int serverCount) {
    logger.info("[SCHEMA] Mismatch detected for {} '{}' across {} servers", 
        elementType, elementName, serverCount);
  }

  /**
   * Logs schema element missing from a server.
   *
   * @param serverName the server name
   * @param elementType the schema element type
   * @param elementName the element name
   */
  public void logSchemaMissing(String serverName, String elementType, String elementName) {
    logger.debug("[SCHEMA] Element '{}' ({}) missing from server: {}", 
        elementName, elementType, serverName);
  }

  /**
   * Logs import operation.
   *
   * @param serverName the server name
   * @param source the import source
   * @param entriesProcessed the number of entries processed
   */
  public void logImport(String serverName, String source, int entriesProcessed) {
    logger.info("[IMPORT] Server: {} | Source: {} | Entries: {}", 
        serverName, source, entriesProcessed);
    String message = String.format("Source: %s | Entries: %d", source, entriesProcessed);
    addLogEntry(new LogEntry(LogLevel.INFO, "IMPORT", message, null, serverName));
  }

  /**
   * Logs LDAP modification operation with LDIF format.
   *
   * @param serverName the server name
   * @param message the modification message
   * @param dn the distinguished name
   * @param ldifData the LDIF format data
   */
  public void logModification(String serverName, String message, String dn, String ldifData) {
    logger.info("[MODIFY] {} on {} (Server: {})", message, dn, serverName);
    addLogEntry(new LogEntry(LogLevel.INFO, "MODIFY", message, 
        "DN: " + dn, serverName, ldifData));
  }

  /**
   * Logs warning message.
   *
   * @param category the category or context of the log
   * @param message the message to log
   */
  public void logWarning(String category, String message) {
    logger.warn("[{}] {}", category, message);
    addLogEntry(new LogEntry(LogLevel.WARNING, category, message));
  }
}
