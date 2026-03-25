package com.ldapbrowser.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
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

  // ---- equals / hashCode with port ------------------------------------

  @Nested
  @DisplayName("Equals and hashCode include port")
  class EqualsHashCode {

    @Test
    @DisplayName("same name/host/port are equal")
    void sameFields() {
      LdapServerConfig a = valid();
      LdapServerConfig b = valid();
      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("different port makes configs unequal")
    void differentPort() {
      LdapServerConfig a = valid();
      LdapServerConfig b = valid();
      b.setPort(636);
      assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("hashCode differs when port differs")
    void hashCodeDiffersOnPort() {
      LdapServerConfig a = valid();
      LdapServerConfig b = valid();
      b.setPort(636);
      assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
    }
  }

  // ---- allowedTemplates -----------------------------------------------

  @Nested
  @DisplayName("AllowedTemplates field")
  class AllowedTemplates {

    @Test
    @DisplayName("defaults to LDAP")
    void defaultsToLdap() {
      LdapServerConfig cfg = new LdapServerConfig();
      assertThat(cfg.getAllowedTemplates())
          .isNotNull().containsExactly("LDAP");
    }

    @Test
    @DisplayName("getter returns set values")
    void setAndGet() {
      LdapServerConfig cfg = valid();
      cfg.setAllowedTemplates(List.of("User", "Group"));
      assertThat(cfg.getAllowedTemplates())
          .containsExactly("User", "Group");
    }

    @Test
    @DisplayName("null setter normalises to empty list")
    void nullNormalisesToEmpty() {
      LdapServerConfig cfg = valid();
      cfg.setAllowedTemplates(null);
      assertThat(cfg.getAllowedTemplates()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("copy preserves allowedTemplates")
    void copyPreserves() {
      LdapServerConfig cfg = valid();
      cfg.setAllowedTemplates(
          new ArrayList<>(List.of("T1", "T2")));
      LdapServerConfig copied = cfg.copy();
      assertThat(copied.getAllowedTemplates())
          .containsExactly("T1", "T2");
    }

    @Test
    @DisplayName("copy creates independent list")
    void copyIsIndependent() {
      LdapServerConfig cfg = valid();
      cfg.setAllowedTemplates(
          new ArrayList<>(List.of("T1")));
      LdapServerConfig copied = cfg.copy();
      copied.getAllowedTemplates().add("T2");
      assertThat(cfg.getAllowedTemplates()).containsExactly("T1");
    }
  }
}
