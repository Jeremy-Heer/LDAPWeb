package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ldapbrowser.model.LogEntry;
import com.ldapbrowser.model.LogEntry.LogLevel;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for LoggingService.
 */
@DisplayName("LoggingService")
class LoggingServiceTest {

  @TempDir
  Path tempDir;

  private LoggingService service;

  @AfterEach
  void tearDown() {
    if (service != null) {
      service.cleanup();
      awaitExecutorTermination(service);
    }
  }

  @Test
  @DisplayName("stores and filters recent logs")
  void storesAndFiltersRecentLogs() throws Exception {
    service = createService();

    service.logInfo("LDAP", "connected");
    service.logError("LDAP", "failed bind");
    service.logDebug("UI", "opened settings");

    assertThat(service.getLogCount()).isEqualTo(3);
    assertThat(service.getLogsByCategory("LDAP")).hasSize(2);
    assertThat(service.getLogsByLevel(LogLevel.ERROR)).hasSize(1);
    assertThat(service.getRecentLogs(2)).hasSize(2);
  }

  @Test
  @DisplayName("notifies listeners when log entries are added")
  void notifiesListeners() throws Exception {
    service = createService();

    List<LogEntry> received = new ArrayList<>();
    LoggingService.LogUpdateListener listener = received::add;
    service.addListener(listener);

    service.logInfo("TEST", "listener event");

    assertThat(received).hasSize(1);
    assertThat(received.getFirst().getMessage()).isEqualTo("listener event");

    service.removeListener(listener);
    service.logInfo("TEST", "second event");

    assertThat(received).hasSize(1);
  }

  @Test
  @DisplayName("clearLogs removes all buffered entries")
  void clearLogsRemovesEntries() throws Exception {
    service = createService();

    service.logInfo("A", "one");
    service.logInfo("A", "two");
    assertThat(service.getLogCount()).isEqualTo(2);

    service.clearLogs();

    assertThat(service.getLogCount()).isZero();
    assertThat(service.getRecentLogs(0)).isEmpty();
  }

  private LoggingService createService() throws Exception {
    LoggingService loggingService = new LoggingService();
    setPrivateField(loggingService, "settingsDir", tempDir.toString());
    loggingService.init();
    return loggingService;
  }

  private static void setPrivateField(Object target, String fieldName, Object value)
      throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void awaitExecutorTermination(LoggingService loggingService) {
    try {
      Field executorField = LoggingService.class.getDeclaredField("executorService");
      executorField.setAccessible(true);
      ExecutorService executor = (ExecutorService) executorField.get(loggingService);
      executor.awaitTermination(1, TimeUnit.SECONDS);
    } catch (Exception ignored) {
      // Best-effort cleanup for test noise reduction.
    }
  }
}
