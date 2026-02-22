package com.ldapbrowser.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LdapServerConfig#validate()}.
 */
@DisplayName("LdapServerConfig")
class LdapServerConfigTest {

  // ---- helpers --------------------------------------------------------

  private LdapServerConfig valid() {
    return new LdapServerConfig(
        "test", "ldap.example.com", 389,
        "dc=example,dc=com",
        "cn=admin,dc=example,dc=com", "secret",
        false, false);
  }

  // ---- host -----------------------------------------------------------

  @Nested
  @DisplayName("Host validation")
  class HostValidation {

    @Test
    @DisplayName("valid host passes")
    void validHost() {
      assertThatCode(() -> valid().validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null host throws")
    void nullHost() {
      LdapServerConfig cfg = valid();
      cfg.setHost(null);
      assertThatThrownBy(cfg::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("host must not be empty");
    }

    @Test
    @DisplayName("empty host throws")
    void emptyHost() {
      LdapServerConfig cfg = valid();
      cfg.setHost("");
      assertThatThrownBy(cfg::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("host must not be empty");
    }

    @Test
    @DisplayName("blank host throws")
    void blankHost() {
      LdapServerConfig cfg = valid();
      cfg.setHost("   ");
      assertThatThrownBy(cfg::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("host must not be empty");
    }
  }

  // ---- port -----------------------------------------------------------

  @Nested
  @DisplayName("Port validation")
  class PortValidation {

    @Test
    @DisplayName("minimum boundary port 1 passes")
    void portMinBoundary() {
      LdapServerConfig cfg = valid();
      cfg.setPort(1);
      assertThatCode(cfg::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("maximum boundary port 65535 passes")
    void portMaxBoundary() {
      LdapServerConfig cfg = valid();
      cfg.setPort(65535);
      assertThatCode(cfg::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("port 0 throws")
    void portZero() {
      LdapServerConfig cfg = valid();
      cfg.setPort(0);
      assertThatThrownBy(cfg::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("port must be between 1 and 65535");
    }

    @Test
    @DisplayName("negative port throws")
    void portNegative() {
      LdapServerConfig cfg = valid();
      cfg.setPort(-1);
      assertThatThrownBy(cfg::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("port must be between 1 and 65535");
    }

    @Test
    @DisplayName("port 65536 throws")
    void portTooHigh() {
      LdapServerConfig cfg = valid();
      cfg.setPort(65536);
      assertThatThrownBy(cfg::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("port must be between 1 and 65535");
    }
  }

  // ---- bindDn ---------------------------------------------------------

  @Nested
  @DisplayName("Bind DN validation")
  class BindDnValidation {

    @Test
    @DisplayName("well-formed bindDn passes")
    void validBindDn() {
      LdapServerConfig cfg = valid();
      cfg.setBindDn("cn=admin,dc=example,dc=com");
      assertThatCode(cfg::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null bindDn (anonymous bind) passes")
    void nullBindDn() {
      LdapServerConfig cfg = valid();
      cfg.setBindDn(null);
      assertThatCode(cfg::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("empty bindDn (anonymous bind) passes")
    void emptyBindDn() {
      LdapServerConfig cfg = valid();
      cfg.setBindDn("");
      assertThatCode(cfg::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("malformed bindDn throws")
    void malformedBindDn() {
      LdapServerConfig cfg = valid();
      cfg.setBindDn("not-a-valid-dn-no-equals-sign!");
      assertThatThrownBy(cfg::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("bindDn is not a well-formed DN");
    }
  }

  // ---- multiple errors ------------------------------------------------

  @Nested
  @DisplayName("Multiple validation errors")
  class MultipleErrors {

    @Test
    @DisplayName("null host and invalid port both reported")
    void multipleErrors() {
      LdapServerConfig cfg = valid();
      cfg.setHost(null);
      cfg.setPort(0);
      assertThatThrownBy(cfg::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("host must not be empty")
          .hasMessageContaining("port must be between 1 and 65535");
    }
  }
}
