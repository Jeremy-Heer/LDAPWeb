package com.ldapbrowser.ui.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ldapbrowser.model.Role;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.EncryptionService;
import com.ldapbrowser.service.KeystoreService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.RoleService;
import com.ldapbrowser.service.TemplateService;
import com.ldapbrowser.service.TruststoreService;
import com.ldapbrowser.service.UserService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.components.EntryEditor;
import com.ldapbrowser.ui.components.LdapTreeBrowser;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Smoke tests for critical UI views.
 */
@DisplayName("View smoke tests")
class ViewSmokeTest {

  @Test
  @DisplayName("BrowseView has route/security metadata and core components")
  void browseViewSmoke() {
    LdapService ldapService = mock(LdapService.class);
    ConfigurationService configService = mock(ConfigurationService.class);
    TruststoreService truststoreService = mock(TruststoreService.class);
    TemplateService templateService = mock(TemplateService.class);

    BrowseView view = new BrowseView(
        ldapService,
        configService,
        truststoreService,
        templateService);

    assertThat(getRouteValue(BrowseView.class)).isEqualTo("browse");
    assertThat(getRoles(BrowseView.class)).contains("USER");
    assertThat(containsComponent(view, SplitLayout.class)).isTrue();
    assertThat(containsComponent(view, LdapTreeBrowser.class)).isTrue();
    assertThat(containsComponent(view, EntryEditor.class)).isTrue();
  }

  @Test
  @DisplayName("SearchView has route/security metadata and core components")
  void searchViewSmoke() {
    ConfigurationService configService = mock(ConfigurationService.class);
    LdapService ldapService = mock(LdapService.class);
    TruststoreService truststoreService = mock(TruststoreService.class);
    TemplateService templateService = mock(TemplateService.class);

    when(templateService.loadTemplates()).thenReturn(List.of());

    try (MockedStatic<MainLayout> mainLayout = mockStatic(MainLayout.class)) {
      mainLayout.when(MainLayout::getSelectedServers).thenReturn(Set.of());

      SearchView view = new SearchView(
          configService,
          ldapService,
          truststoreService,
          templateService);

      assertThat(getRouteValue(SearchView.class)).isEqualTo("search");
      assertThat(getRoles(SearchView.class)).contains("USER");
      assertThat(containsComponent(view, SplitLayout.class)).isTrue();
      assertThat(containsComponent(view, Grid.class)).isTrue();
      assertThat(containsComponent(view, EntryEditor.class)).isTrue();
    }
  }

  @Test
  @DisplayName("SettingsView has route/security metadata and core components")
  void settingsViewSmoke() throws Exception {
    TruststoreService truststoreService = mock(TruststoreService.class);
    KeystoreService keystoreService = mock(KeystoreService.class);
    EncryptionService encryptionService = mock(EncryptionService.class);
    ConfigurationService configurationService = mock(ConfigurationService.class);
    TemplateService templateService = mock(TemplateService.class);
    RoleService roleService = mock(RoleService.class);

    when(truststoreService.getTruststoreStats()).thenReturn("Truststore ok");
    when(truststoreService.listCertificates()).thenReturn(List.of());
    when(keystoreService.isInitialized()).thenReturn(false);
    when(keystoreService.getKeystoreStats()).thenReturn("Keystore ok");
    when(encryptionService.isEncryptionEnabled()).thenReturn(true);
    when(templateService.loadTemplates()).thenReturn(List.of());
    when(roleService.loadRoles()).thenReturn(List.<Role>of());

    SettingsView view = new SettingsView(
        truststoreService,
        keystoreService,
        encryptionService,
        configurationService,
        templateService,
        roleService,
        Optional.<UserService>empty());

    assertThat(getRouteValue(SettingsView.class)).isEqualTo("settings");
    assertThat(getRoles(SettingsView.class)).contains("USER");
    assertThat(containsComponent(view, H2.class)).isTrue();
    assertThat(containsComponent(view, TabSheet.class)).isTrue();
  }

  private static String getRouteValue(Class<?> viewClass) {
    Route route = viewClass.getAnnotation(Route.class);
    assertThat(route).isNotNull();
    return route.value();
  }

  private static List<String> getRoles(Class<?> viewClass) {
    RolesAllowed rolesAllowed = viewClass.getAnnotation(RolesAllowed.class);
    assertThat(rolesAllowed).isNotNull();
    return List.of(rolesAllowed.value());
  }

  private static boolean containsComponent(
      Component root,
      Class<? extends Component> type) {
    if (type.isInstance(root)) {
      return true;
    }
    return root.getChildren().anyMatch(child -> containsComponent(child, type));
  }
}
