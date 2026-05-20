package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

@DisplayName("LdapAttributeModificationHelper")
class LdapAttributeModificationHelperTest {

  private final LoggingService loggingService = mock(LoggingService.class);
  private final Logger logger = mock(Logger.class);
  private final LdapAttributeModificationHelper helper =
      new LdapAttributeModificationHelper(loggingService, logger);

  @Test
  @DisplayName("modifyAttribute applies REPLACE and logs LDIF")
  void modifyAttributeAppliesReplace() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);

    helper.modifyAttribute(
        pool,
        "ServerA",
        "uid=alice,ou=people,dc=example,dc=com",
        "description",
        List.of("updated")
    );

    ArgumentCaptor<Modification> modCaptor = ArgumentCaptor.forClass(Modification.class);
    verify(pool).modify(eq("uid=alice,ou=people,dc=example,dc=com"), modCaptor.capture());
    assertThat(modCaptor.getValue().getModificationType()).isEqualTo(ModificationType.REPLACE);
    verify(loggingService).logModification(
        eq("ServerA"),
        eq("Modified attribute 'description' on entry"),
        eq("uid=alice,ou=people,dc=example,dc=com"),
        any(String.class)
    );
  }

  @Test
  @DisplayName("addAttribute applies ADD and logs LDIF")
  void addAttributeAppliesAdd() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);

    helper.addAttribute(
        pool,
        "ServerB",
        "uid=alice,ou=people,dc=example,dc=com",
        "mail",
        List.of("alice@example.com")
    );

    ArgumentCaptor<Modification> modCaptor = ArgumentCaptor.forClass(Modification.class);
    verify(pool).modify(eq("uid=alice,ou=people,dc=example,dc=com"), modCaptor.capture());
    assertThat(modCaptor.getValue().getModificationType()).isEqualTo(ModificationType.ADD);
    verify(loggingService).logModification(
        eq("ServerB"),
        eq("Added attribute 'mail' to entry"),
        eq("uid=alice,ou=people,dc=example,dc=com"),
        any(String.class)
    );
  }

  @Test
  @DisplayName("deleteAttribute applies DELETE and logs LDIF")
  void deleteAttributeAppliesDelete() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);

    helper.deleteAttribute(
        pool,
        "ServerC",
        "uid=alice,ou=people,dc=example,dc=com",
        "description"
    );

    ArgumentCaptor<Modification> modCaptor = ArgumentCaptor.forClass(Modification.class);
    verify(pool).modify(eq("uid=alice,ou=people,dc=example,dc=com"), modCaptor.capture());
    assertThat(modCaptor.getValue().getModificationType()).isEqualTo(ModificationType.DELETE);
    verify(loggingService).logModification(
        eq("ServerC"),
        eq("Deleted attribute 'description' from entry"),
        eq("uid=alice,ou=people,dc=example,dc=com"),
        any(String.class)
    );
  }
}