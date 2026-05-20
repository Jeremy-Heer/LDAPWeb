package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("LdapOperationExecutor")
class LdapOperationExecutorTest {

  private static LdapServerConfig config(String name) {
    LdapServerConfig cfg = new LdapServerConfig();
    cfg.setName(name);
    return cfg;
  }

  @Test
  @DisplayName("Retries once on connection-related result code")
  void retriesOnceOnConnectionFailure() throws Exception {
    LdapOperationExecutor executor = new LdapOperationExecutor();
    LDAPConnectionPool firstPool = Mockito.mock(LDAPConnectionPool.class);
    LDAPConnectionPool freshPool = Mockito.mock(LDAPConnectionPool.class);

    int[] attempts = {0};
    String result = executor.executeWithRetry(
        config("retry"),
        () -> firstPool,
        pool -> {
          attempts[0]++;
          if (attempts[0] == 1) {
            throw new LDAPException(ResultCode.SERVER_DOWN, "down");
          }
          return "ok";
        },
        () -> freshPool);

    assertThat(result).isEqualTo("ok");
    assertThat(attempts[0]).isEqualTo(2);
  }

  @Test
  @DisplayName("Does not retry on non-connection result code")
  void noRetryOnNonConnectionFailure() {
    LdapOperationExecutor executor = new LdapOperationExecutor();

    assertThatThrownBy(() -> executor.executeWithRetry(
        config("no-retry"),
        () -> Mockito.mock(LDAPConnectionPool.class),
        pool -> {
          throw new LDAPException(ResultCode.INVALID_CREDENTIALS, "bad creds");
        },
        () -> {
          throw new AssertionError("recreate should not be called");
        }))
        .isInstanceOf(LDAPException.class)
        .hasMessageContaining("bad creds");
  }

  @Test
  @DisplayName("Propagates retry failure when second attempt fails")
  void propagatesRetryFailure() {
    LdapOperationExecutor executor = new LdapOperationExecutor();

    assertThatThrownBy(() -> executor.executeWithRetry(
        config("retry-fail"),
        () -> Mockito.mock(LDAPConnectionPool.class),
        pool -> {
          throw new LDAPException(ResultCode.SERVER_DOWN, "first fail");
        },
        () -> Mockito.mock(LDAPConnectionPool.class)))
        .isInstanceOf(LDAPException.class)
        .hasMessageContaining("first fail");
  }
}
