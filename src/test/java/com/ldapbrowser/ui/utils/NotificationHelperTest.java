package com.ldapbrowser.ui.utils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.ldapbrowser.service.LoggingService;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Unit tests for {@link NotificationHelper}.
 *
 * <p>Uses Mockito static mocking to isolate Vaadin runtime dependencies
 * ({@code Notification.show} and {@code VaadinSession.getCurrent}).
 */
class NotificationHelperTest {

  /** Session attribute key mirrored from NotificationHelper source. */
  private static final String LOGGING_KEY = "notificationHelper.loggingService";

  // ---------------------------------------------------------------------------
  // Helper
  // ---------------------------------------------------------------------------

  /**
   * Creates a mock VaadinSession that returns the given LoggingService
   * when the LOGGING_KEY attribute is requested.
   */
  private static VaadinSession sessionWith(LoggingService svc) {
    VaadinSession session = mock(VaadinSession.class);
    doReturn(svc).when(session).getAttribute(LOGGING_KEY);
    return session;
  }

  // ---------------------------------------------------------------------------
  // Nested test classes
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("utility class contract")
  class UtilityClass {

    @Test
    @DisplayName("constructor throws AssertionError")
    void constructorThrows() throws Exception {
      var ctor = NotificationHelper.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      assertThatThrownBy(ctor::newInstance)
          .cause().isInstanceOf(AssertionError.class);
    }
  }

  @Nested
  @DisplayName("null VaadinSession - no NPE")
  class NullSession {

    private void prepareNotification(
        MockedStatic<Notification> notif,
        MockedStatic<VaadinSession> vs) {
      Notification mockNotif = mock(Notification.class);
      notif.when(() -> Notification.show(any(), anyInt(), any()))
          .thenReturn(mockNotif);
      vs.when(VaadinSession::getCurrent).thenReturn(null);
    }

    @Test
    @DisplayName("showSuccess does not throw when session is null")
    void showSuccessNullSession() {
      try (MockedStatic<Notification> notif = mockStatic(Notification.class);
          MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        prepareNotification(notif, vs);
        NotificationHelper.showSuccess("ok");
      }
    }

    @Test
    @DisplayName("showError does not throw when session is null")
    void showErrorNullSession() {
      try (MockedStatic<Notification> notif = mockStatic(Notification.class);
          MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        prepareNotification(notif, vs);
        NotificationHelper.showError("err");
      }
    }

    @Test
    @DisplayName("showInfo does not throw when session is null")
    void showInfoNullSession() {
      try (MockedStatic<Notification> notif = mockStatic(Notification.class);
          MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        prepareNotification(notif, vs);
        NotificationHelper.showInfo("fyi");
      }
    }

    @Test
    @DisplayName("showWarning does not throw when session is null")
    void showWarningNullSession() {
      try (MockedStatic<Notification> notif = mockStatic(Notification.class);
          MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        prepareNotification(notif, vs);
        NotificationHelper.showWarning("heads up");
      }
    }
  }

  @Nested
  @DisplayName("logging service delegation")
  class LoggingDelegation {

    @Test
    @DisplayName("showSuccess delegates to LoggingService.logInfo")
    void showSuccessLogsInfo() {
      LoggingService svc = mock(LoggingService.class);
      VaadinSession session = sessionWith(svc);
      Notification mockNotif = mock(Notification.class);
      try (MockedStatic<Notification> notif = mockStatic(Notification.class);
          MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        notif.when(() -> Notification.show(any(), anyInt(), any()))
            .thenReturn(mockNotif);
        vs.when(VaadinSession::getCurrent).thenReturn(session);
        NotificationHelper.showSuccess("saved");
        verify(svc).logInfo("UI", "saved");
      }
    }

    @Test
    @DisplayName("showError delegates to LoggingService.logError")
    void showErrorLogsError() {
      LoggingService svc = mock(LoggingService.class);
      VaadinSession session = sessionWith(svc);
      Notification mockNotif = mock(Notification.class);
      try (MockedStatic<Notification> notif = mockStatic(Notification.class);
          MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        notif.when(() -> Notification.show(any(), anyInt(), any()))
            .thenReturn(mockNotif);
        vs.when(VaadinSession::getCurrent).thenReturn(session);
        NotificationHelper.showError("boom");
        verify(svc).logError("UI", "boom");
      }
    }

    @Test
    @DisplayName("showInfo delegates to LoggingService.logInfo")
    void showInfoLogsInfo() {
      LoggingService svc = mock(LoggingService.class);
      VaadinSession session = sessionWith(svc);
      Notification mockNotif = mock(Notification.class);
      try (MockedStatic<Notification> notif = mockStatic(Notification.class);
          MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        notif.when(() -> Notification.show(any(), anyInt(), any()))
            .thenReturn(mockNotif);
        vs.when(VaadinSession::getCurrent).thenReturn(session);
        NotificationHelper.showInfo("fyi");
        verify(svc).logInfo("UI", "fyi");
      }
    }

    @Test
    @DisplayName("showWarning delegates to LoggingService.logWarning")
    void showWarningLogsWarning() {
      LoggingService svc = mock(LoggingService.class);
      VaadinSession session = sessionWith(svc);
      Notification mockNotif = mock(Notification.class);
      try (MockedStatic<Notification> notif = mockStatic(Notification.class);
          MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        notif.when(() -> Notification.show(any(), anyInt(), any()))
            .thenReturn(mockNotif);
        vs.when(VaadinSession::getCurrent).thenReturn(session);
        NotificationHelper.showWarning("watch out");
        verify(svc).logWarning("UI", "watch out");
      }
    }

    @Test
    @DisplayName("showModifySuccess delegates to LoggingService.logInfo")
    void showModifySuccessLogsInfo() {
      LoggingService svc = mock(LoggingService.class);
      VaadinSession session = sessionWith(svc);
      Notification mockNotif = mock(Notification.class);
      try (MockedStatic<Notification> notif = mockStatic(Notification.class);
          MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        notif.when(() -> Notification.show(any(), anyInt(), any()))
            .thenReturn(mockNotif);
        vs.when(VaadinSession::getCurrent).thenReturn(session);
        NotificationHelper.showModifySuccess("modified");
        verify(svc).logInfo("UI", "modified");
      }
    }

    @Test
    @DisplayName("showSuccess(msg, duration) suppresses UI but logs info")
    void showSuccessCustomDurationLogsInfo() {
      LoggingService svc = mock(LoggingService.class);
      VaadinSession session = sessionWith(svc);
      try (MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        vs.when(VaadinSession::getCurrent).thenReturn(session);
        NotificationHelper.showSuccess("quiet", 2000);
        verify(svc).logInfo("UI", "quiet");
      }
    }

    @Test
    @DisplayName("showInfo(msg, duration) suppresses UI but logs info")
    void showInfoCustomDurationLogsInfo() {
      LoggingService svc = mock(LoggingService.class);
      VaadinSession session = sessionWith(svc);
      try (MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        vs.when(VaadinSession::getCurrent).thenReturn(session);
        NotificationHelper.showInfo("quiet fyi", 3000);
        verify(svc).logInfo("UI", "quiet fyi");
      }
    }

    @Test
    @DisplayName("showWarning(msg, duration) suppresses UI but logs warning")
    void showWarningCustomDurationLogsWarning() {
      LoggingService svc = mock(LoggingService.class);
      VaadinSession session = sessionWith(svc);
      try (MockedStatic<VaadinSession> vs = mockStatic(VaadinSession.class)) {
        vs.when(VaadinSession::getCurrent).thenReturn(session);
        NotificationHelper.showWarning("quiet warn", 3000);
        verify(svc).logWarning("UI", "quiet warn");
      }
    }
  }
}
