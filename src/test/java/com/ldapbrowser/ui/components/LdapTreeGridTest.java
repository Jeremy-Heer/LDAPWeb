package com.ldapbrowser.ui.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.TruststoreService;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LdapTreeGrid} reset behavior.
 */
@DisplayName("LdapTreeGrid")
class LdapTreeGridTest {

  @Test
  @DisplayName("clear removes filter state and clears paging caches")
  @SuppressWarnings("unchecked")
  void clearResetsInternalState() throws Exception {
    LdapService ldapService = mock(LdapService.class);
    TruststoreService truststoreService = mock(TruststoreService.class);
    LdapTreeGrid grid = new LdapTreeGrid(ldapService, truststoreService);

    LdapServerConfig config = new LdapServerConfig();
    config.setName("example");
    grid.setServerConfig(config);

    Field filtersField = LdapTreeGrid.class.getDeclaredField("entryFilters");
    filtersField.setAccessible(true);
    Map<String, String> filters = (Map<String, String>) filtersField.get(grid);
    filters.put("dc=example,dc=com", "(objectClass=person)");

    grid.clear();

    assertThat(filters).isEmpty();
    verify(ldapService).clearPagingState("example");
  }
}