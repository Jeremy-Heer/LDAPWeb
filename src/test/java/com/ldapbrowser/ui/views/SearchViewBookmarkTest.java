package com.ldapbrowser.ui.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ldapbrowser.model.EntryTemplate;
import com.ldapbrowser.model.EntryTemplate.SearchTemplateSection;
import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.TemplateService;
import com.ldapbrowser.service.TruststoreService;
import com.ldapbrowser.ui.MainLayout;
import com.unboundid.ldap.sdk.SearchScope;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Focused tests for SearchView URL bookmark parsing and serialization behavior.
 */
@DisplayName("SearchView URL bookmark behavior")
class SearchViewBookmarkTest {

  @Test
  @DisplayName("serializes LDAP mode fields into bookmark URL")
  void serializesLdapBookmarkUrl() throws Exception {
    ConfigurationService configService = mock(ConfigurationService.class);
    LdapService ldapService = mock(LdapService.class);
    TruststoreService truststoreService = mock(TruststoreService.class);
    TemplateService templateService = mock(TemplateService.class);

    when(templateService.loadTemplates()).thenReturn(List.of());

    LdapServerConfig cfg = new LdapServerConfig();
    cfg.setName("jeremy");
    when(configService.loadConfigurations()).thenReturn(List.of(cfg));

    LinkedHashSet<String> selectedServers =
        new LinkedHashSet<>(List.of("jeremy"));

    try (MockedStatic<MainLayout> mainLayout = mockStatic(MainLayout.class)) {
      mainLayout.when(MainLayout::getSelectedServers).thenReturn(selectedServers);

      SearchView view = new SearchView(configService, ldapService,
          truststoreService, templateService);

      getField(view, "baseTypeRadio", RadioButtonGroup.class)
          .setValue("Custom Base");
      getField(view, "searchBaseField", TextField.class)
          .setValue("dc=example,dc=com");
      getField(view, "filterField", TextField.class)
          .setValue("(uid=jdoe)");
      getField(view, "scopeSelect", Select.class)
          .setValue(SearchScope.ONE);

      MultiSelectComboBox<String> attrs =
          getField(view, "returnAttributesField", MultiSelectComboBox.class);
      attrs.setValue(new LinkedHashSet<>(List.of("uid", "mail")));

      getField(view, "sizeLimitField", IntegerField.class)
          .setValue(25);
      getField(view, "timeLimitField", IntegerField.class)
          .setValue(10);

      String relativeUrl = invokeStringMethod(view, "buildBookmarkRelativeUrl");
      Map<String, String> params = parseQuery(relativeUrl);

      assertThat(relativeUrl).startsWith("/search?");
      assertThat(params.get("baseMode")).isEqualTo("CUSTOM");
      assertThat(params.get("searchBase")).isEqualTo("dc=example,dc=com");
      assertThat(params.get("filter")).isEqualTo("(uid=jdoe)");
      assertThat(params.get("scope")).isEqualTo("ONE");
      assertThat(params.get("sizeLimit")).isEqualTo("25");
      assertThat(params.get("timeLimit")).isEqualTo("10");
      assertThat(parseCsv(params.get("returnAttributes")))
          .containsExactlyInAnyOrder("uid", "mail");
      assertThat(parseCsv(params.get("selectedServers")))
          .containsExactly("jeremy");
      assertThat(params).doesNotContainKeys("template", "templateSearch");
    }
  }

  @Test
  @DisplayName("applies LDAP bookmark parameters to form fields")
  void appliesLdapBookmarkParams() throws Exception {
    ConfigurationService configService = mock(ConfigurationService.class);
    LdapService ldapService = mock(LdapService.class);
    TruststoreService truststoreService = mock(TruststoreService.class);
    TemplateService templateService = mock(TemplateService.class);

    when(templateService.loadTemplates()).thenReturn(List.of());

    LdapServerConfig cfg = new LdapServerConfig();
    cfg.setName("jeremy");
    when(configService.loadConfigurations()).thenReturn(List.of(cfg));
    when(ldapService.getEntryMinimal(cfg, "dc=example,dc=com"))
        .thenReturn(new LdapEntry("dc=example,dc=com", "jeremy"));

    LinkedHashSet<String> selectedServers =
        new LinkedHashSet<>(List.of("jeremy"));

    try (MockedStatic<MainLayout> mainLayout = mockStatic(MainLayout.class)) {
      mainLayout.when(MainLayout::getSelectedServers).thenReturn(selectedServers);

      SearchView view = new SearchView(configService, ldapService,
          truststoreService, templateService);

      Map<String, List<String>> params = new HashMap<>();
      params.put("baseMode", List.of("CUSTOM"));
      params.put("searchBase", List.of("dc=example,dc=com"));
      params.put("filter", List.of("(uid=jdoe)"));
      params.put("scope", List.of("ONE"));
      params.put("returnAttributes", List.of("uid,mail"));
      params.put("sizeLimit", List.of("15"));
      params.put("timeLimit", List.of("5"));
      params.put("selectedServers", List.of("jeremy"));

      invokeVoidMethod(view, "applyBookmarkedSearchState", Map.class, params);

      assertThat(getField(view, "baseTypeRadio", RadioButtonGroup.class).getValue())
          .isEqualTo("Custom Base");
      assertThat(getField(view, "searchBaseField", TextField.class).getValue())
          .isEqualTo("dc=example,dc=com");
      assertThat(getField(view, "filterField", TextField.class).getValue())
          .isEqualTo("(uid=jdoe)");
      assertThat(getField(view, "scopeSelect", Select.class).getValue())
          .isEqualTo(SearchScope.ONE);
      assertThat(getField(view, "sizeLimitField", IntegerField.class).getValue())
          .isEqualTo(15);
      assertThat(getField(view, "timeLimitField", IntegerField.class).getValue())
          .isEqualTo(5);

      MultiSelectComboBox<String> attrs =
          getField(view, "returnAttributesField", MultiSelectComboBox.class);
      assertThat(attrs.getSelectedItems())
          .containsExactlyInAnyOrder("uid", "mail");
    }
  }

  @Test
  @DisplayName("serializes template bookmark fields")
  void serializesTemplateBookmarkUrl() throws Exception {
    ConfigurationService configService = mock(ConfigurationService.class);
    LdapService ldapService = mock(LdapService.class);
    TruststoreService truststoreService = mock(TruststoreService.class);
    TemplateService templateService = mock(TemplateService.class);

    EntryTemplate template = new EntryTemplate("People");
    template.setSearchSection(new SearchTemplateSection());
    when(templateService.loadTemplates()).thenReturn(List.of(template));

    try (MockedStatic<MainLayout> mainLayout = mockStatic(MainLayout.class)) {
      mainLayout.when(MainLayout::getSelectedServers).thenReturn(Set.of());

      SearchView view = new SearchView(configService, ldapService,
          truststoreService, templateService);

      getField(view, "searchTemplateCombo", ComboBox.class)
          .setValue("People");
      getField(view, "templateSearchField", TextField.class)
          .setValue("john doe");

      String relativeUrl = invokeStringMethod(view, "buildBookmarkRelativeUrl");
      Map<String, String> params = parseQuery(relativeUrl);

      assertThat(params.get("template")).isEqualTo("People");
      assertThat(params.get("templateSearch")).isEqualTo("john doe");
      assertThat(params).doesNotContainKeys(
          "filter", "scope", "sizeLimit", "timeLimit", "returnAttributes");
    }
  }

  @Test
  @DisplayName("applies template bookmark parameters")
  void appliesTemplateBookmarkParams() throws Exception {
    ConfigurationService configService = mock(ConfigurationService.class);
    LdapService ldapService = mock(LdapService.class);
    TruststoreService truststoreService = mock(TruststoreService.class);
    TemplateService templateService = mock(TemplateService.class);

    EntryTemplate template = new EntryTemplate("People");
    template.setSearchSection(new SearchTemplateSection());
    when(templateService.loadTemplates()).thenReturn(List.of(template));

    try (MockedStatic<MainLayout> mainLayout = mockStatic(MainLayout.class)) {
      mainLayout.when(MainLayout::getSelectedServers).thenReturn(Set.of());

      SearchView view = new SearchView(configService, ldapService,
          truststoreService, templateService);

      Map<String, List<String>> params = new HashMap<>();
      params.put("template", List.of("People"));
      params.put("templateSearch", List.of("jane"));

      invokeVoidMethod(view, "applyBookmarkedSearchState", Map.class, params);

      ComboBox<String> templateCombo =
          getField(view, "searchTemplateCombo", ComboBox.class);
      TextField templateSearch =
          getField(view, "templateSearchField", TextField.class);

      assertThat(templateCombo.getValue()).isEqualTo("People");
      assertThat(templateSearch.getValue()).isEqualTo("jane");
      assertThat(templateSearch.isVisible()).isTrue();
    }
  }

  private static List<String> parseCsv(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(v -> !v.isEmpty())
        .toList();
  }

  private static Map<String, String> parseQuery(String relativeUrl) {
    Map<String, String> result = new LinkedHashMap<>();
    int idx = relativeUrl.indexOf('?');
    if (idx < 0 || idx == relativeUrl.length() - 1) {
      return result;
    }

    String query = relativeUrl.substring(idx + 1);
    for (String pair : query.split("&")) {
      if (pair.isBlank()) {
        continue;
      }
      String[] parts = pair.split("=", 2);
      String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
      String value = parts.length > 1
          ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
      result.put(key, value);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static <T> T getField(Object target,
      String fieldName,
      Class<T> fieldType) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return (T) fieldType.cast(field.get(target));
  }

  private static String invokeStringMethod(Object target,
      String methodName) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName);
    method.setAccessible(true);
    return (String) method.invoke(target);
  }

  private static void invokeVoidMethod(Object target,
      String methodName,
      Class<?> argType,
      Object argValue) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName, argType);
    method.setAccessible(true);
    method.invoke(target, argValue);
  }
}
