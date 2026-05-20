package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LdapBrowseRequestFactory")
class LdapBrowseRequestFactoryTest {

  @Test
  @DisplayName("creates paged browse request with expected attributes and no cookie")
  void createsPagedBrowseRequestWithoutCookie() throws Exception {
    LdapBrowseRequestFactory factory = new LdapBrowseRequestFactory();

    SearchRequest request = factory.createPagedBrowseRequest(
        "dc=example,dc=com",
        Filter.create("(objectClass=*)"),
        100,
        null);

    assertThat(request.getBaseDN()).isEqualTo("dc=example,dc=com");
    assertThat(request.getScope()).isEqualTo(com.unboundid.ldap.sdk.SearchScope.ONE);
    assertThat(request.getAttributes()).containsExactly("objectClass", "cn", "ou", "dc");

    Control[] controls = request.getControls();
    assertThat(controls).hasSize(1);
    assertThat(controls[0]).isInstanceOf(SimplePagedResultsControl.class);
    SimplePagedResultsControl pagedControl = (SimplePagedResultsControl) controls[0];
    assertThat(pagedControl.getSize()).isEqualTo(100);
    assertThat(pagedControl.getCookie()).isNotNull();
    assertThat(pagedControl.getCookie().getValueLength()).isZero();
  }

  @Test
  @DisplayName("creates paged browse request with provided cookie")
  void createsPagedBrowseRequestWithCookie() throws Exception {
    LdapBrowseRequestFactory factory = new LdapBrowseRequestFactory();

    SearchRequest request = factory.createPagedBrowseRequest(
        "dc=example,dc=com",
        Filter.create("(cn=*)"),
        50,
        new byte[] {1, 2, 3});

    Control[] controls = request.getControls();
    assertThat(controls).hasSize(1);
    assertThat(controls[0]).isInstanceOf(SimplePagedResultsControl.class);
    SimplePagedResultsControl pagedControl = (SimplePagedResultsControl) controls[0];
    assertThat(pagedControl.getSize()).isEqualTo(50);
    assertThat(pagedControl.getCookie().getValue()).containsExactly(1, 2, 3);
  }
}
