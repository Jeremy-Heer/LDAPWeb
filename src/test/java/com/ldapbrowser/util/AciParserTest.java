package com.ldapbrowser.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.ldapbrowser.util.AciParser.ParsedAci;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AciParser}. */
@DisplayName("AciParser")
class AciParserTest {

  // Minimal valid ACI: no targets, userdn bind rule
  private static final String SIMPLE_ACI =
      "(version 3.0; acl \"Read all\"; allow (read,search)"
          + " userdn=\"ldap:///anyone\";)";

  // Full ACI: all four target types, deny, groupdn bind rule
  private static final String FULL_ACI =
      "(targetattr=\"cn || sn\")"
          + "(target=\"ldap:///dc=example,dc=com\")"
          + "(targetscope=\"subtree\")"
          + "(targetfilter=\"(objectClass=person)\")"
          + "(version 3.0; acl \"Full ACI\"; deny (write,delete)"
          + " groupdn=\"ldap:///cn=admins,dc=example,dc=com\";)";

  @Nested
  @DisplayName("null and empty input")
  class NullAndEmpty {

    @Test
    @DisplayName("null returns default ParsedAci with unknown allowOrDeny")
    void nullInput() {
      ParsedAci result = AciParser.parseAci(null);
      assertThat(result.getDescription()).isEmpty();
      assertThat(result.getAllowOrDeny()).isEqualTo("unknown");
      assertThat(result.getPermissions()).isEmpty();
      assertThat(result.getTargets()).isEmpty();
      assertThat(result.getBindRules()).isEmpty();
    }

    @Test
    @DisplayName("empty string returns unknown ParsedAci")
    void emptyString() {
      ParsedAci result = AciParser.parseAci("");
      assertThat(result.getAllowOrDeny()).isEqualTo("unknown");
      assertThat(result.getPermissions()).isEmpty();
    }

    @Test
    @DisplayName("whitespace-only string returns unknown ParsedAci")
    void whitespaceOnly() {
      ParsedAci result = AciParser.parseAci("   ");
      assertThat(result.getAllowOrDeny()).isEqualTo("unknown");
      assertThat(result.getPermissions()).isEmpty();
    }
  }

  @Nested
  @DisplayName("well-formed ACI parsing")
  class WellFormedParsing {

    @Test
    @DisplayName("description is extracted correctly")
    void descriptionExtracted() {
      ParsedAci result = AciParser.parseAci(SIMPLE_ACI);
      assertThat(result.getDescription()).isEqualTo("Read all");
    }

    @Test
    @DisplayName("allow keyword is parsed to lowercase")
    void allowKeyword() {
      ParsedAci result = AciParser.parseAci(SIMPLE_ACI);
      assertThat(result.getAllowOrDeny()).isEqualTo("allow");
    }

    @Test
    @DisplayName("deny keyword is parsed to lowercase")
    void denyKeyword() {
      ParsedAci result = AciParser.parseAci(FULL_ACI);
      assertThat(result.getAllowOrDeny()).isEqualTo("deny");
    }

    @Test
    @DisplayName("permissions are extracted as individual trimmed entries")
    void permissionsExtracted() {
      ParsedAci result = AciParser.parseAci(SIMPLE_ACI);
      assertThat(result.getPermissions()).containsExactlyInAnyOrder("read", "search");
    }

    @Test
    @DisplayName("multiple permissions in deny ACI are all captured")
    void multiplePermissions() {
      ParsedAci result = AciParser.parseAci(FULL_ACI);
      assertThat(result.getPermissions()).containsExactlyInAnyOrder("write", "delete");
    }

    @Test
    @DisplayName("targetattr is extracted into targets list")
    void targetattrExtracted() {
      ParsedAci result = AciParser.parseAci(FULL_ACI);
      assertThat(result.getTargets())
          .anyMatch(t -> t.startsWith("Attributes:") && t.contains("cn"));
    }

    @Test
    @DisplayName("target DN is extracted into targets list")
    void targetDnExtracted() {
      ParsedAci result = AciParser.parseAci(FULL_ACI);
      assertThat(result.getTargets())
          .anyMatch(t -> t.startsWith("Target:") && t.contains("dc=example"));
    }

    @Test
    @DisplayName("targetscope is extracted into targets list")
    void targetscopeExtracted() {
      ParsedAci result = AciParser.parseAci(FULL_ACI);
      assertThat(result.getTargets())
          .anyMatch(t -> t.startsWith("Scope:") && t.contains("subtree"));
    }

    @Test
    @DisplayName("targetfilter is extracted into targets list")
    void targetfilterExtracted() {
      ParsedAci result = AciParser.parseAci(FULL_ACI);
      assertThat(result.getTargets())
          .anyMatch(t -> t.startsWith("Filter:") && t.contains("objectClass=person"));
    }

    @Test
    @DisplayName("userdn bind rule is extracted")
    void userdnBindRule() {
      ParsedAci result = AciParser.parseAci(SIMPLE_ACI);
      assertThat(result.getBindRules())
          .anyMatch(r -> r.startsWith("User DN:") && r.contains("anyone"));
    }

    @Test
    @DisplayName("groupdn bind rule is extracted")
    void groupdnBindRule() {
      ParsedAci result = AciParser.parseAci(FULL_ACI);
      assertThat(result.getBindRules())
          .anyMatch(r -> r.startsWith("Group DN:") && r.contains("admins"));
    }

    @Test
    @DisplayName("authmethod bind rule is extracted")
    void authmethodBindRule() {
      String aci = "(version 3.0; acl \"SSL only\"; allow (read) authmethod=\"ssl\";)";
      ParsedAci result = AciParser.parseAci(aci);
      assertThat(result.getBindRules())
          .anyMatch(r -> r.startsWith("Auth Method:") && r.contains("ssl"));
    }

    @Test
    @DisplayName("ip bind rule is extracted")
    void ipBindRule() {
      String aci = "(version 3.0; acl \"IP restrict\"; allow (read) ip=\"10.0.0.0/8\";)";
      ParsedAci result = AciParser.parseAci(aci);
      assertThat(result.getBindRules())
          .anyMatch(r -> r.startsWith("IP:") && r.contains("10.0.0.0"));
    }

    @Test
    @DisplayName("dns bind rule is extracted")
    void dnsBindRule() {
      String aci = "(version 3.0; acl \"DNS restrict\"; allow (read)"
          + " dns=\"*.example.com\";)";
      ParsedAci result = AciParser.parseAci(aci);
      assertThat(result.getBindRules())
          .anyMatch(r -> r.startsWith("DNS:") && r.contains("example.com"));
    }

    @Test
    @DisplayName("rawAci preserves the original trimmed string (round-trip)")
    void rawAciRoundTrip() {
      ParsedAci result = AciParser.parseAci(SIMPLE_ACI);
      assertThat(result.getRawAci()).isEqualTo(SIMPLE_ACI.trim());
    }

    @Test
    @DisplayName("ACI with no targets produces empty targets list")
    void noTargets() {
      ParsedAci result = AciParser.parseAci(SIMPLE_ACI);
      assertThat(result.getTargets()).isEmpty();
    }
  }

  @Nested
  @DisplayName("malformed ACI input")
  class MalformedInput {

    @Test
    @DisplayName("random text returns Unparseable ACI description")
    void randomText() {
      ParsedAci result = AciParser.parseAci("this is not an aci");
      assertThat(result.getDescription()).isEqualTo("Unparseable ACI");
      assertThat(result.getAllowOrDeny()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("partial ACI string returns Unparseable ACI")
    void partialAci() {
      ParsedAci result = AciParser.parseAci("(version 3.0; acl \"incomplete\"");
      assertThat(result.getDescription()).isEqualTo("Unparseable ACI");
    }

    @Test
    @DisplayName("rawAci is preserved even for unparseable input")
    void rawAciPreservedForUnparseable() {
      String input = "garbage aci string";
      ParsedAci result = AciParser.parseAci(input);
      assertThat(result.getRawAci()).isEqualTo(input);
    }

    @Test
    @DisplayName("permissions, targets, and bindRules are empty for malformed input")
    void collectionsEmptyForMalformed() {
      ParsedAci result = AciParser.parseAci("not an aci at all");
      assertThat(result.getPermissions()).isEmpty();
      assertThat(result.getTargets()).isEmpty();
      assertThat(result.getBindRules()).isEmpty();
    }
  }

  @Nested
  @DisplayName("ParsedAci helper methods")
  class ParsedAciHelpers {

    @Test
    @DisplayName("getName returns description when present")
    void getNameWithDescription() {
      ParsedAci result = AciParser.parseAci(SIMPLE_ACI);
      assertThat(result.getName()).isEqualTo("Read all");
    }

    @Test
    @DisplayName("getName returns 'Unnamed ACI' when description is blank")
    void getNameUnnamed() {
      ParsedAci parsed =
          new ParsedAci("", "allow", List.of("read"), List.of(), List.of(), "raw");
      assertThat(parsed.getName()).isEqualTo("Unnamed ACI");
    }

    @Test
    @DisplayName("getResourcesString returns 'All' when targets list is empty")
    void getResourcesStringEmpty() {
      ParsedAci parsed =
          new ParsedAci("desc", "allow", List.of("read"), List.of(), List.of(), "raw");
      assertThat(parsed.getResourcesString()).isEqualTo("All");
    }

    @Test
    @DisplayName("getResourcesString joins multiple targets with comma")
    void getResourcesStringWithTargets() {
      List<String> targets = List.of("Attributes: cn", "Scope: subtree");
      ParsedAci parsed = new ParsedAci("d", "allow", List.of(), targets, List.of(), "raw");
      assertThat(parsed.getResourcesString())
          .contains("Attributes: cn")
          .contains("Scope: subtree");
    }

    @Test
    @DisplayName("getRightsString combines allow/deny with permissions in parens")
    void getRightsString() {
      ParsedAci result = AciParser.parseAci(SIMPLE_ACI);
      String rights = result.getRightsString();
      assertThat(rights).startsWith("allow").contains("read");
    }

    @Test
    @DisplayName("getRightsString returns just allow/deny when no permissions")
    void getRightsStringNoPermissions() {
      ParsedAci parsed =
          new ParsedAci("d", "deny", List.of(), List.of(), List.of(), "raw");
      assertThat(parsed.getRightsString()).isEqualTo("deny");
    }

    @Test
    @DisplayName("getClientsString returns 'Any' when bind rules list is empty")
    void getClientsStringEmpty() {
      ParsedAci parsed =
          new ParsedAci("d", "allow", List.of("read"), List.of(), List.of(), "raw");
      assertThat(parsed.getClientsString()).isEqualTo("Any");
    }

    @Test
    @DisplayName("getClientsString contains bind rule entries")
    void getClientsStringWithRules() {
      List<String> rules = List.of("User DN: ldap:///anyone");
      ParsedAci parsed = new ParsedAci("d", "allow", List.of(), List.of(), rules, "raw");
      assertThat(parsed.getClientsString()).contains("User DN: ldap:///anyone");
    }

    @Test
    @DisplayName("getPermissions returns a defensive copy")
    void getPermissionsDefensiveCopy() {
      ParsedAci result = AciParser.parseAci(SIMPLE_ACI);
      List<String> perms = result.getPermissions();
      perms.add("write");
      assertThat(result.getPermissions()).doesNotContain("write");
    }
  }
}
