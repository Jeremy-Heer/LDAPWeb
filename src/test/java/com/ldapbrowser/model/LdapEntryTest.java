package com.ldapbrowser.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LdapEntry} equals, hashCode, hasChildren,
 * defensive copies, and display name.
 */
class LdapEntryTest {

  @Test
  void equalsSameDnAndServer() {
    LdapEntry a = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    LdapEntry b = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    assertThat(a).isEqualTo(b);
  }

  @Test
  void equalsDifferentDn() {
    LdapEntry a = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    LdapEntry b = new LdapEntry("cn=bob,dc=example,dc=com", "server1");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equalsDifferentServer() {
    LdapEntry a = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    LdapEntry b = new LdapEntry("cn=alice,dc=example,dc=com", "server2");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equalsNullDnBothNull() {
    LdapEntry a = new LdapEntry(null, "server1");
    LdapEntry b = new LdapEntry(null, "server1");
    assertThat(a).isEqualTo(b);
  }

  @Test
  void equalsNullChecks() {
    LdapEntry a = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    assertThat(a).isNotEqualTo(null);
    assertThat(a).isNotEqualTo("not-an-ldap-entry");
  }

  @Test
  void equalsSelfReflexive() {
    LdapEntry a = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    assertThat(a).isEqualTo(a);
  }

  @Test
  void hashCodeConsistentWithEquals() {
    LdapEntry a = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    LdapEntry b = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  void hashCodeDifferentForDifferentDn() {
    LdapEntry a = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    LdapEntry b = new LdapEntry("cn=bob,dc=example,dc=com", "server1");
    assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
  }

  @Test
  void usableAsMapKey() {
    LdapEntry a = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    LdapEntry b = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    Map<LdapEntry, String> map = new HashMap<>();
    map.put(a, "value");
    assertThat(map.get(b)).isEqualTo("value");
  }

  @Test
  void multiServerSameEntryDistinctKeys() {
    LdapEntry s1 = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    LdapEntry s2 = new LdapEntry("cn=alice,dc=example,dc=com", "server2");
    Map<LdapEntry, String> map = new HashMap<>();
    map.put(s1, "from-server1");
    map.put(s2, "from-server2");
    assertThat(map).hasSize(2);
    assertThat(map.get(s1)).isEqualTo("from-server1");
    assertThat(map.get(s2)).isEqualTo("from-server2");
  }

  @Test
  void hasChildrenDefaultFalse() {
    LdapEntry entry = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    assertThat(entry.hasChildren()).isFalse();
  }

  @Test
  void hasChildrenAfterSet() {
    LdapEntry entry = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    entry.setHasChildren(true);
    assertThat(entry.hasChildren()).isTrue();
  }

  @Test
  void hasChildrenReset() {
    LdapEntry entry = new LdapEntry("cn=alice,dc=example,dc=com", "server1");
    entry.setHasChildren(true);
    entry.setHasChildren(false);
    assertThat(entry.hasChildren()).isFalse();
  }

  // ---------------------------------------------------------------
  // Unmodifiable attributes (defensive copy)
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Unmodifiable attribute maps")
  class UnmodifiableAttributes {

    @Test
    @DisplayName("getAttributes returns unmodifiable map")
    void attributesUnmodifiable() {
      LdapEntry entry = new LdapEntry("cn=test", "s1");
      entry.addAttribute("cn", "test");
      Map<String, List<String>> attrs = entry.getAttributes();

      assertThatThrownBy(() -> attrs.put("sn", List.of("value")))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getOperationalAttributes returns unmodifiable map")
    void operationalAttributesUnmodifiable() {
      LdapEntry entry = new LdapEntry("cn=test", "s1");
      entry.addOperationalAttribute("createTimestamp", "now");
      Map<String, List<String>> ops = entry.getOperationalAttributes();

      assertThatThrownBy(() -> ops.put("extra", List.of("v")))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  // ---------------------------------------------------------------
  // Display name null safety
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Display name null safety")
  class DisplayNameNullSafety {

    @Test
    @DisplayName("getDisplayName never returns null")
    void neverNull() {
      LdapEntry entry = new LdapEntry(null, null);
      assertThat(entry.getDisplayName()).isNotNull();
      assertThat(entry.getDisplayName()).isEqualTo("(unknown)");
    }

    @Test
    @DisplayName("getDisplayName returns RDN when available")
    void usesRdn() {
      LdapEntry entry = new LdapEntry("cn=test,dc=example", "s1");
      assertThat(entry.getDisplayName()).isEqualTo("cn=test");
    }

    @Test
    @DisplayName("getDisplayName returns DN when RDN is null")
    void usesDnWhenNoRdn() {
      LdapEntry entry = new LdapEntry("dc=example", "s1");
      // DN with no comma — getRdn() returns the full DN
      assertThat(entry.getDisplayName()).isEqualTo("dc=example");
    }
  }
}
