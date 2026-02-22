package com.ldapbrowser.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LdifGenerator}.
 */
class LdifGeneratorTest {

  private static final String DN = "cn=alice,dc=example,dc=com";

  @Test
  void replaceProducesCorrectLdif() {
    String ldif = LdifGenerator.replace(DN, "mail", List.of("alice@example.com"));
    assertThat(ldif).isEqualTo(
        "dn: cn=alice,dc=example,dc=com\n"
            + "changetype: modify\n"
            + "replace: mail\n"
            + "mail: alice@example.com\n"
            + "-"
    );
  }

  @Test
  void replaceMultipleValues() {
    String ldif = LdifGenerator.replace(DN, "telephoneNumber",
        List.of("+1-555-0100", "+1-555-0101"));
    assertThat(ldif).contains("replace: telephoneNumber");
    assertThat(ldif).contains("telephoneNumber: +1-555-0100\n");
    assertThat(ldif).contains("telephoneNumber: +1-555-0101\n");
    assertThat(ldif).endsWith("-");
  }

  @Test
  void addProducesCorrectLdif() {
    String ldif = LdifGenerator.add(DN, "description", List.of("test user"));
    assertThat(ldif).isEqualTo(
        "dn: cn=alice,dc=example,dc=com\n"
            + "changetype: modify\n"
            + "add: description\n"
            + "description: test user\n"
            + "-"
    );
  }

  @Test
  void addMultipleValues() {
    String ldif = LdifGenerator.add(DN, "cn", List.of("Alice", "Alice Smith"));
    assertThat(ldif).contains("add: cn");
    assertThat(ldif).contains("cn: Alice\n");
    assertThat(ldif).contains("cn: Alice Smith\n");
  }

  @Test
  void deleteProducesCorrectLdif() {
    String ldif = LdifGenerator.delete(DN, "description");
    assertThat(ldif).isEqualTo(
        "dn: cn=alice,dc=example,dc=com\n"
            + "changetype: modify\n"
            + "delete: description\n"
            + "-"
    );
  }

  @Test
  void deleteContainsNoValueLines() {
    String ldif = LdifGenerator.delete(DN, "description");
    long valueLineCount = ldif.lines()
        .filter(line -> line.startsWith("description:"))
        .count();
    assertThat(valueLineCount).isZero();
  }

  @Test
  void allMethodsStartWithDnLine() {
    assertThat(LdifGenerator.replace(DN, "a", List.of("v"))).startsWith("dn: " + DN);
    assertThat(LdifGenerator.add(DN, "a", List.of("v"))).startsWith("dn: " + DN);
    assertThat(LdifGenerator.delete(DN, "a")).startsWith("dn: " + DN);
  }

  @Test
  void allMethodsEndWithDash() {
    assertThat(LdifGenerator.replace(DN, "a", List.of("v"))).endsWith("-");
    assertThat(LdifGenerator.add(DN, "a", List.of("v"))).endsWith("-");
    assertThat(LdifGenerator.delete(DN, "a")).endsWith("-");
  }

  @Test
  void replaceEmptyValueList() {
    String ldif = LdifGenerator.replace(DN, "description", List.of());
    assertThat(ldif).isEqualTo(
        "dn: cn=alice,dc=example,dc=com\n"
            + "changetype: modify\n"
            + "replace: description\n"
            + "-"
    );
  }
}
