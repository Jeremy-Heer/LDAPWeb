package com.ldapbrowser.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.ldapbrowser.model.LdapEntry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link LdapExportFormatter}. */
@DisplayName("LdapExportFormatter")
class LdapExportFormatterTest {

  @Test
  @DisplayName("generates CSV with header DN and quoted values")
  void generatesCsvWithConfiguredOptions() {
    LdapEntry entry = new LdapEntry("uid=jdoe,dc=example,dc=com", "jeremy");
    entry.addAttribute("cn", "John Doe");
    entry.addAttribute("mail", "john@example.com");

    String csv = LdapExportFormatter.generateExportData(
        List.of(entry),
        "CSV",
        List.of("cn", "mail"),
        true,
        true,
        true);

    assertThat(csv).isEqualTo(
        "dn,cn,mail\n"
            + "\"uid=jdoe,dc=example,dc=com\",\"John Doe\","
            + "\"john@example.com\"\n");
  }

  @Test
  @DisplayName("generates JSON using requested attributes only")
  void generatesJsonUsingRequestedAttributesOnly() {
    LdapEntry entry = new LdapEntry("uid=jdoe,dc=example,dc=com", "jeremy");
    entry.addAttribute("cn", "John Doe");
    entry.addAttribute("mail", "john@example.com");
    entry.addAttribute("memberOf", "cn=group1,dc=example,dc=com");
    entry.addAttribute("memberOf", "cn=group2,dc=example,dc=com");

    String json = LdapExportFormatter.generateExportData(
        List.of(entry),
        "JSON",
        List.of("memberOf"),
        true,
        true,
        true);

    assertThat(json).contains("\"dn\": \"uid=jdoe,dc=example,dc=com\"");
    assertThat(json).contains(
        "\"memberOf\": [\"cn=group1,dc=example,dc=com\", "
            + "\"cn=group2,dc=example,dc=com\"]");
    assertThat(json).doesNotContain("\"mail\"");
    assertThat(json).doesNotContain("\"cn\"");
  }

  @Test
  @DisplayName("parses return attributes preserving order and trimming whitespace")
  void parsesReturnAttributes() {
    assertThat(LdapExportFormatter.parseReturnAttributes(" cn, mail , uid "))
        .containsExactly("cn", "mail", "uid");
  }
}