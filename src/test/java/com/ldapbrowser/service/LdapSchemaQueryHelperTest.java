package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.schema.Schema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

@DisplayName("LdapSchemaQueryHelper")
class LdapSchemaQueryHelperTest {

  private final LdapSchemaQueryHelper helper =
      new LdapSchemaQueryHelper(mock(Logger.class));

  @Test
  @DisplayName("returns null when schema DN is empty")
  void returnsNullWhenSchemaDnEmpty() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);

    Schema schema = helper.tryRetrieveWithExtendedControl(pool, "");

    assertThat(schema).isNull();
  }

  @Test
  @DisplayName("returns schema when search returns parseable schema subentry")
  void returnsSchemaWhenParseable() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);

    SearchResultEntry entry = new SearchResultEntry(
        "cn=schema",
        new Attribute[]{
            new Attribute("attributeTypes",
                "( 2.5.4.3 NAME 'cn' DESC 'Common Name' EQUALITY caseIgnoreMatch "
                    + "SUBSTR caseIgnoreSubstringsMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 "
                    + "SINGLE-VALUE )"),
            new Attribute("objectClasses",
                "( 2.5.6.0 NAME 'top' ABSTRACT MUST objectClass )")
        }
    );

    SearchResult result = mock(SearchResult.class);
    when(result.getEntryCount()).thenReturn(1);
    when(result.getSearchEntries()).thenReturn(List.of(entry));
    when(pool.search(any(com.unboundid.ldap.sdk.SearchRequest.class))).thenReturn(result);

    Schema schema = helper.tryRetrieveWithExtendedControl(pool, "cn=schema");

    assertThat(schema).isNotNull();
  }

  @Test
  @DisplayName("returns null when schema search returns no entries")
  void returnsNullWhenNoEntries() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);

    SearchResult result = mock(SearchResult.class);
    when(result.getEntryCount()).thenReturn(0);
    when(result.getSearchEntries()).thenReturn(List.of());
    when(pool.search(any(com.unboundid.ldap.sdk.SearchRequest.class))).thenReturn(result);

    Schema schema = helper.tryRetrieveWithExtendedControl(pool, "cn=schema");

    assertThat(schema).isNull();
  }
}
