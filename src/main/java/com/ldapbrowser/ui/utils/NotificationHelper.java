package com.ldapbrowser.ui.utils;

import com.ldapbrowser.service.LoggingService;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.VaadinSession;

/**
 * Utility class for displaying standardized notifications throughout the application.
 * Provides consistent notification behavior with predefined durations, positions, and themes.
 * All notifications are also logged to the activity log via LoggingService.
 *
 * <p>Usage examples:</p>
 * <pre>{@code
 * NotificationHelper.showSuccess("Operation completed successfully");
 * NotificationHelper.showError("Failed to connect to server");
 * NotificationHelper.showInfo("Processing your request...");
 * NotificationHelper.showWarning("Configuration may be incomplete");
 * }</pre>
 */
public final class NotificationHelper {

  // Standard durations in milliseconds
  private static final int SUCCESS_DURATION = 3000;
  private static final int ERROR_DURATION = 5000;
  private static final int INFO_DURATION = 4000;
  private static final int WARNING_DURATION = 4000;

  // Standard position for all notifications
  private static final Notification.Position POSITION = Notification.Position.TOP_END;

  // Session attribute key for LoggingService
  private static final String LOGGING_SERVICE_KEY = "notificationHelper.loggingService";

  /**
   * Private constructor to prevent instantiation of utility class.
   */
  private NotificationHelper() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Sets the LoggingService instance for this session.
   * This should be called once per session, typically in the layout or main view.
   *
   * @param loggingService the logging service instance
   */
  public static void setLoggingService(LoggingService loggingService) {
    VaadinSession.getCurrent().setAttribute(LOGGING_SERVICE_KEY, loggingService);
  }

  /**
   * Gets the LoggingService instance for this session.
   *
   * @return the logging service, or null if not set
   */
  private static LoggingService getLoggingService() {
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null) {
      return (LoggingService) session.getAttribute(LOGGING_SERVICE_KEY);
    }
    return null;
  }

  /**
   * Displays a success notification with green theme.
   * Duration: 3 seconds.
   *
   * @param message the success message to display
   */
  public static void showSuccess(String message) {
    Notification notification = Notification.show(message, SUCCESS_DURATION, POSITION);
    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    
    // Log to activity log
    LoggingService loggingService = getLoggingService();
    if (loggingService != null) {
      loggingService.logInfo("UI", message);
    }
  }

  /**
   * Displays an error notification with red theme.
   * Duration: 5 seconds.
   *
   * @param message the error message to display
   */
  public static void showError(String message) {
    Notification notification = Notification.show(message, ERROR_DURATION, POSITION);
    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    
    // Log to activity log
    LoggingService loggingService = getLoggingService();
    if (loggingService != null) {
      loggingService.logError("UI", message);
    }
  }

  /**
   * Displays an informational notification with blue theme.
   * Duration: 4 seconds.
   *
   * @param message the informational message to display
   */
  public static void showInfo(String message) {
    Notification notification = Notification.show(message, INFO_DURATION, POSITION);
    notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
    
    // Log to activity log
    LoggingService loggingService = getLoggingService();
    if (loggingService != null) {
      loggingService.logInfo("UI", message);
    }
  }

  /**
   * Displays a warning notification with contrast theme.
   * Duration: 4 seconds.
   *
   * @param message the warning message to display
   */
  public static void showWarning(String message) {
    Notification notification = Notification.show(message, WARNING_DURATION, POSITION);
    notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
    
    // Log to activity log
    LoggingService loggingService = getLoggingService();
    if (loggingService != null) {
      loggingService.logWarning("UI", message);
    }
  }

  /**
   * Displays a success notification with custom duration.
   *
   * @param message the success message to display
   * @param duration the duration in milliseconds
   */
  public static void showSuccess(String message, int duration) {
    Notification notification = Notification.show(message, duration, POSITION);
    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    
    // Log to activity log
    LoggingService loggingService = getLoggingService();
    if (loggingService != null) {
      loggingService.logInfo("UI", message);
    }
  }

  /**
   * Displays an error notification with custom duration.
   *
   * @param message the error message to display
   * @param duration the duration in milliseconds
   */
  public static void showError(String message, int duration) {
    Notification notification = Notification.show(message, duration, POSITION);
    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    
    // Log to activity log
    LoggingService loggingService = getLoggingService();
    if (loggingService != null) {
      loggingService.logError("UI", message);
    }
  }

  /**
   * Displays an informational notification with custom duration.
   *
   * @param message the informational message to display
   * @param duration the duration in milliseconds
   */
  public static void showInfo(String message, int duration) {
    Notification notification = Notification.show(message, duration, POSITION);
    notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
    
    // Log to activity log
    LoggingService loggingService = getLoggingService();
    if (loggingService != null) {
      loggingService.logInfo("UI", message);
    }
  }

  /**
   * Displays a warning notification with custom duration.
   *
   * @param message the warning message to display
   * @param duration the duration in milliseconds
   */
  public static void showWarning(String message, int duration) {
    Notification notification = Notification.show(message, duration, POSITION);
    notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
    
    // Log to activity log
    LoggingService loggingService = getLoggingService();
    if (loggingService != null) {
      loggingService.logWarning("UI", message);
    }
  }
}
