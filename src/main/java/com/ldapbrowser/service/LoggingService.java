package com.ldapbrowser.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for detailed logging of LDAP operations and schema comparisons.
 * Provides structured logging methods for tracking schema comparison
 * operations with varying levels of detail.
 */
@Service
public class LoggingService {

  private static final Logger logger = LoggerFactory.getLogger(LoggingService.class);

  /**
   * Logs general information message.
   *
   * @param category the category or context of the log
   * @param message the message to log
   */
  public void logInfo(String category, String message) {
    logger.info("[{}] {}", category, message);
  }

  /**
   * Logs a debug message.
   *
   * @param category the category or context of the log
   * @param message the message to log
   */
  public void logDebug(String category, String message) {
    logger.debug("[{}] {}", category, message);
  }

  /**
   * Logs an error message.
   *
   * @param category the category or context of the log
   * @param message the message to log
   */
  public void logError(String category, String message) {
    logger.error("[{}] {}", category, message);
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
  }

  /**
   * Logs warning message.
   *
   * @param category the category or context of the log
   * @param message the message to log
   */
  public void logWarning(String category, String message) {
    logger.warn("[{}] {}", category, message);
  }
}
