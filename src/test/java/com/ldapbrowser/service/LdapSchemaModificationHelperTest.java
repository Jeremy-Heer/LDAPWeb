package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

@DisplayName("LdapSchemaModificationHelper")
class LdapSchemaModificationHelperTest {

  private final LdapSchemaModificationHelper helper =
      new LdapSchemaModificationHelper(mock(Logger.class));

  @Test
  @DisplayName("canAccessSchemaSubentry returns true when entry exists")
  void canAccessSchemaSubentryTrue() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
    SearchResult result = mock(SearchResult.class);
    SearchResultEntry schemaEntry = new SearchResultEntry(
      "cn=schema",
      new Attribute[]{new Attribute("objectClass", "top")}
    );
    when(result.getSearchEntries()).thenReturn(List.of(schemaEntry));
    when(pool.search(eq("cn=schema"), any(), eq("(objectClass=*)"), eq("objectClass")))
        .thenReturn(result);

    assertThat(helper.canAccessSchemaSubentry(pool, "cn=schema")).isTrue();
  }

  @Test
  @DisplayName("canAccessSchemaSubentry returns false for null DN or empty search result")
  void canAccessSchemaSubentryFalse() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
    SearchResult result = mock(SearchResult.class);
    when(result.getSearchEntries()).thenReturn(List.of());
    when(pool.search(eq("cn=schema"), any(), eq("(objectClass=*)"), eq("objectClass")))
        .thenReturn(result);

    assertThat(helper.canAccessSchemaSubentry(pool, null)).isFalse();
    assertThat(helper.canAccessSchemaSubentry(pool, "cn=schema")).isFalse();
  }

  @Test
  @DisplayName("applyAddModification issues ADD modification")
  void applyAddModification() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);

    assertThatCode(() -> helper.applyAddModification(
        pool,
        "cn=schema",
        "attributeTypes",
        "( 1.2.3 NAME 'exampleAttr' )"
    )).doesNotThrowAnyException();

    verify(pool).modify(eq("cn=schema"), any(com.unboundid.ldap.sdk.Modification.class));
  }

  @Test
  @DisplayName("applyAddModification propagates LDAPException")
  void applyAddModificationPropagatesError() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
    doThrow(new LDAPException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, "denied"))
        .when(pool)
        .modify(eq("cn=schema"), any(com.unboundid.ldap.sdk.Modification.class));

    assertThatThrownBy(() -> helper.applyAddModification(
        pool,
        "cn=schema",
        "objectClasses",
        "( 1.2.4 NAME 'exampleObjectClass' SUP top STRUCTURAL MUST cn )"
    )).isInstanceOf(LDAPException.class);
  }
}
