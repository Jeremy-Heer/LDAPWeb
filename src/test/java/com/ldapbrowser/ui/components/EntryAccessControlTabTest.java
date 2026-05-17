package com.ldapbrowser.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for LDIF export helpers in {@link EntryAccessControlTab}. */
@DisplayName("EntryAccessControlTab")
class EntryAccessControlTabTest {

  private static final String ACI_ALPHA =
      "(version 3.0; acl \"Alpha\"; allow (read) userdn=\"ldap:///anyone\";)";
  private static final String ACI_BETA =
      "(version 3.0; acl \"Beta\"; allow (search) userdn=\"ldap:///anyone\";)";
  private static final String ACI_GAMMA =
      "(version 3.0; acl \"Gamma\"; deny (write) userdn=\"ldap:///anyone\";)";

  @Nested
  @DisplayName("buildAddAciLdifForRows")
  class BuildAddAciLdifForRows {

    @Test
    @DisplayName("returns comment for empty rows")
    void emptyRows() {
      String ldif = EntryAccessControlTab.buildAddAciLdifForRows(List.of());
      assertThat(ldif).isEqualTo("# No visible ACI entries to export\n");
    }

    @Test
    @DisplayName("groups by DN and emits add aci values")
    void groupsByDn() {
      List<EntryAccessControlTab.EntryAciInfo> rows = List.of(
          row("ou=z,dc=example,dc=com", ACI_GAMMA),
          row("ou=a,dc=example,dc=com", ACI_BETA),
          row("ou=a,dc=example,dc=com", ACI_ALPHA));

      String ldif = EntryAccessControlTab.buildAddAciLdifForRows(rows);

      assertThat(ldif).contains("dn: ou=a,dc=example,dc=com");
      assertThat(ldif).contains("dn: ou=z,dc=example,dc=com");
      assertThat(ldif).contains("changetype: modify");
      assertThat(ldif).contains("add: aci");
      assertThat(ldif).contains("aci: " + ACI_ALPHA);
      assertThat(ldif).contains("aci: " + ACI_BETA);
      assertThat(ldif).contains("aci: " + ACI_GAMMA);
      assertThat(ldif.indexOf("dn: ou=a,dc=example,dc=com"))
          .isLessThan(ldif.indexOf("dn: ou=z,dc=example,dc=com"));
    }
  }

  @Nested
  @DisplayName("buildDeleteAciLdifForRows")
  class BuildDeleteAciLdifForRows {

    @Test
    @DisplayName("returns comment for empty rows")
    void emptyRows() {
      String ldif = EntryAccessControlTab.buildDeleteAciLdifForRows(List.of());
      assertThat(ldif).isEqualTo("# No visible ACI entries to export\n");
    }

    @Test
    @DisplayName("emits delete aci using exact values")
    void deletesExactValues() {
      List<EntryAccessControlTab.EntryAciInfo> rows = List.of(
          row("ou=people,dc=example,dc=com", ACI_ALPHA),
          row("ou=people,dc=example,dc=com", ACI_BETA));

      String ldif = EntryAccessControlTab.buildDeleteAciLdifForRows(rows);

      assertThat(ldif).contains("dn: ou=people,dc=example,dc=com");
      assertThat(ldif).contains("changetype: modify");
      assertThat(ldif).contains("delete: aci");
      assertThat(ldif).contains("aci: " + ACI_ALPHA);
      assertThat(ldif).contains("aci: " + ACI_BETA);
      assertThat(ldif).doesNotContain("delete: aci\n-\n");
    }
  }

  private static EntryAccessControlTab.EntryAciInfo row(String dn, String aciValue) {
    return new EntryAccessControlTab.EntryAciInfo(dn, aciValue, "ServerA", "ServerA");
  }
}
