package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ldapbrowser.model.BrowseResult;
import com.ldapbrowser.model.LdapEntry;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LdapBrowseResultFactory")
class LdapBrowseResultFactoryTest {

  @Test
  @DisplayName("createPageResult preserves page metadata")
  void createPageResultPreservesMetadata() {
    LdapBrowseResultFactory factory = new LdapBrowseResultFactory();
    List<LdapEntry> entries = List.of(new LdapEntry("cn=a,dc=example,dc=com", "serverA"));

    BrowseResult result = factory.createPageResult(entries, 2, 100, false);

    assertThat(result.getEntries()).hasSize(1);
    assertThat(result.getCurrentPage()).isEqualTo(2);
    assertThat(result.getPageSize()).isEqualTo(100);
    assertThat(result.hasNextPage()).isFalse();
    assertThat(result.hasPrevPage()).isTrue();
    assertThat(result.getTotalReturned()).isEqualTo(1);
  }

  @Test
  @DisplayName("createSizeLimitResult forces next-page true")
  void createSizeLimitResultForcesNextPage() {
    LdapBrowseResultFactory factory = new LdapBrowseResultFactory();

    BrowseResult result = factory.createSizeLimitResult(Collections.emptyList(), 0, 50);

    assertThat(result.getEntries()).isEmpty();
    assertThat(result.getCurrentPage()).isEqualTo(0);
    assertThat(result.getPageSize()).isEqualTo(50);
    assertThat(result.hasNextPage()).isTrue();
    assertThat(result.hasPrevPage()).isFalse();
    assertThat(result.getTotalReturned()).isZero();
  }
}
