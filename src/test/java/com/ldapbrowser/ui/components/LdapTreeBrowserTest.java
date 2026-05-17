package com.ldapbrowser.ui.components;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.TruststoreService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LdapTreeBrowser} refresh behavior.
 */
@DisplayName("LdapTreeBrowser")
class LdapTreeBrowserTest {

  @Test
  @DisplayName("refreshTree clears browse caches before reloading")
  void refreshTreeClearsCaches() {
    LdapService ldapService = mock(LdapService.class);
    TruststoreService truststoreService = mock(TruststoreService.class);
    LdapTreeBrowser browser = new LdapTreeBrowser(ldapService, truststoreService);

    LdapServerConfig config = new LdapServerConfig();
    config.setName("example");
    config.setHost("ldap.example.com");
    config.setPort(389);

    browser.setServerConfigs(List.of(config));
    browser.refreshTree();

    verify(ldapService).clearAllBrowseCaches();
  }
}