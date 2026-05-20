package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ldapbrowser.model.LdapEntry;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.schema.Schema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LdapEntryMapper")
class LdapEntryMapperTest {

  private final LdapEntryMapper mapper = new LdapEntryMapper();

  @Test
  @DisplayName("maps one entry with multi-valued attributes")
  void mapsSingleEntry() {
    SearchResultEntry entry = new SearchResultEntry(
        "uid=alice,ou=people,dc=example,dc=com",
      new Attribute[]{
        new Attribute("cn", "Alice"),
        new Attribute("memberOf", "cn=group1,dc=example,dc=com", "cn=group2,dc=example,dc=com")
      }
    );

    LdapEntry mapped = mapper.mapEntry(entry, "ServerA");

    assertThat(mapped.getDn()).isEqualTo("uid=alice,ou=people,dc=example,dc=com");
    assertThat(mapped.getServerName()).isEqualTo("ServerA");
    assertThat(mapped.getFirstAttributeValue("cn")).isEqualTo("Alice");
    assertThat(mapped.getAttributeValues("memberOf")).containsExactly(
        "cn=group1,dc=example,dc=com",
        "cn=group2,dc=example,dc=com"
    );
  }

  @Test
  @DisplayName("maps a list of entries")
  void mapsEntryList() {
    SearchResultEntry first = new SearchResultEntry(
        "uid=one,dc=example,dc=com",
      new Attribute[]{new Attribute("uid", "one")}
    );
    SearchResultEntry second = new SearchResultEntry(
        "uid=two,dc=example,dc=com",
      new Attribute[]{new Attribute("uid", "two")}
    );

    List<LdapEntry> mapped = mapper.mapEntries(List.of(first, second), "ServerB");

    assertThat(mapped).hasSize(2);
    assertThat(mapped.get(0).getFirstAttributeValue("uid")).isEqualTo("one");
    assertThat(mapped.get(1).getFirstAttributeValue("uid")).isEqualTo("two");
  }

  @Test
  @DisplayName("read-entry mapping separates operational attributes when schema is available")
  void mapsReadEntryWithOperationalAttributes() throws LDAPException {
    SearchResultEntry entry = new SearchResultEntry(
        "uid=alice,ou=people,dc=example,dc=com",
        new Attribute[] {
            new Attribute("cn", "Alice"),
            new Attribute("createTimestamp", "20260519000000Z")
        }
    );

    Schema schema = Schema.getDefaultStandardSchema();
    LdapEntry mapped = mapper.mapReadEntry(entry, "ServerC", true, schema);

    assertThat(mapped.getAttributes()).containsKey("cn");
    assertThat(mapped.getAttributes()).doesNotContainKey("createTimestamp");
    assertThat(mapped.getOperationalAttributes()).containsKey("createTimestamp");
  }

  @Test
  @DisplayName("read-entry mapping excludes operational attributes when includeOperational is false")
  void excludesOperationalAttributesWhenDisabled() throws LDAPException {
    SearchResultEntry entry = new SearchResultEntry(
        "uid=alice,ou=people,dc=example,dc=com",
        new Attribute[] {
            new Attribute("cn", "Alice"),
            new Attribute("createTimestamp", "20260519000000Z")
        }
    );

    Schema schema = Schema.getDefaultStandardSchema();
    LdapEntry mapped = mapper.mapReadEntry(entry, "ServerD", false, schema);

    assertThat(mapped.getAttributes()).containsKey("cn");
    assertThat(mapped.getAttributes()).doesNotContainKey("createTimestamp");
    assertThat(mapped.getOperationalAttributes()).doesNotContainKey("createTimestamp");
  }
}
