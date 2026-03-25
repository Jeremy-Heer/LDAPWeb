package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ldapbrowser.model.EntryTemplate;
import com.ldapbrowser.model.EntryTemplate.CreateTemplateSection;
import com.ldapbrowser.model.EntryTemplate.FieldType;
import com.ldapbrowser.model.EntryTemplate.SearchTemplateSection;
import com.ldapbrowser.model.EntryTemplate.TemplateAttribute;
import com.ldapbrowser.model.EntryTemplate.ViewEditTemplateSection;
import com.ldapbrowser.model.LdapServerConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link TemplateService}.
 * Uses a temporary directory to isolate test file I/O.
 */
class TemplateServiceTest {

  @TempDir
  Path tempDir;

  private TemplateService service;

  @BeforeEach
  void setUp() {
    ObjectMapper mapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();
    Path templatePath = tempDir.resolve("templates.json");
    service = new TemplateService(templatePath, mapper);
  }

  // ---- helpers --------------------------------------------------------

  private EntryTemplate makeTemplate(String name) {
    return new EntryTemplate(name);
  }

  private EntryTemplate makeFullTemplate(String name) {
    EntryTemplate t = new EntryTemplate(name);

    CreateTemplateSection create = new CreateTemplateSection();
    create.setRdn("uid={UID}");
    create.setParentFilter("(objectClass=organizationalUnit)");
    TemplateAttribute attr = new TemplateAttribute("User Name", "uid");
    attr.setRequired(true);
    attr.setFieldType(FieldType.TEXT);
    create.setAttributes(List.of(attr));
    t.setCreateSection(create);

    ViewEditTemplateSection viewEdit = new ViewEditTemplateSection();
    viewEdit.setMatchingFilter("(objectClass=inetOrgPerson)");
    viewEdit.setAttributes(List.of(
        new TemplateAttribute("Common Name", "cn")));
    t.setViewEditSection(viewEdit);

    SearchTemplateSection search = new SearchTemplateSection();
    search.setSearchFilter(
        "(&(objectClass=inetOrgPerson)(uid={SEARCH}))");
    search.setBaseFilter("(objectClass=organizationalUnit)");
    search.setScope("sub");
    search.setReturnAttributes(List.of("cn", "uid", "mail"));
    t.setSearchSection(search);

    return t;
  }

  // ---- loadTemplates --------------------------------------------------

  @Nested
  class LoadTemplates {

    @Test
    void emptyListWhenFileNotPresent() {
      List<EntryTemplate> result = service.loadTemplates();
      assertThat(result).isEmpty();
    }

    @Test
    void loadsMultipleTemplates() throws IOException {
      EntryTemplate a = makeTemplate("User");
      EntryTemplate b = makeTemplate("Group");
      service.saveTemplates(List.of(a, b));

      List<EntryTemplate> loaded = service.loadTemplates();
      assertThat(loaded).hasSize(2);
      assertThat(loaded).extracting(EntryTemplate::getName)
          .containsExactlyInAnyOrder("User", "Group");
    }

    @Test
    void returnsEmptyListOnCorruptJson() throws IOException {
      Files.writeString(service.getTemplatePath(),
          "{ not valid json !!!}");

      List<EntryTemplate> result = service.loadTemplates();
      assertThat(result).isEmpty();
    }

    @Test
    void preservesAllSectionsOnLoad() throws IOException {
      EntryTemplate full = makeFullTemplate("Full");
      service.saveTemplates(List.of(full));

      List<EntryTemplate> loaded = service.loadTemplates();
      assertThat(loaded).hasSize(1);
      EntryTemplate got = loaded.get(0);

      assertThat(got.getName()).isEqualTo("Full");
      assertThat(got.getCreateSection()).isNotNull();
      assertThat(got.getCreateSection().getRdn())
          .isEqualTo("uid={UID}");
      assertThat(got.getCreateSection().getAttributes()).hasSize(1);
      assertThat(got.getViewEditSection()).isNotNull();
      assertThat(got.getViewEditSection().getMatchingFilter())
          .isEqualTo("(objectClass=inetOrgPerson)");
      assertThat(got.getSearchSection()).isNotNull();
      assertThat(got.getSearchSection().getScope()).isEqualTo("sub");
      assertThat(got.getSearchSection().getReturnAttributes())
          .containsExactly("cn", "uid", "mail");
    }
  }

  // ---- saveTemplates --------------------------------------------------

  @Nested
  class SaveTemplates {

    @Test
    void createsJsonFileOnSave() throws IOException {
      service.saveTemplates(List.of(makeTemplate("T1")));
      assertThat(service.getTemplatePath()).exists();
    }

    @Test
    void savesEmptyListWithoutError() throws IOException {
      service.saveTemplates(List.of());
      List<EntryTemplate> loaded = service.loadTemplates();
      assertThat(loaded).isEmpty();
    }

    @Test
    void roundTripPreservesName() throws IOException {
      service.saveTemplates(List.of(makeTemplate("MyTemplate")));

      List<EntryTemplate> loaded = service.loadTemplates();
      assertThat(loaded).hasSize(1);
      assertThat(loaded.get(0).getName()).isEqualTo("MyTemplate");
    }
  }

  // ---- saveTemplate (single) ------------------------------------------

  @Nested
  class SaveSingleTemplate {

    @Test
    void addsNewWhenNotExists() throws IOException {
      service.saveTemplate(makeTemplate("New"));

      List<EntryTemplate> loaded = service.loadTemplates();
      assertThat(loaded).hasSize(1);
      assertThat(loaded.get(0).getName()).isEqualTo("New");
    }

    @Test
    void updatesExistingByName() throws IOException {
      EntryTemplate original = makeTemplate("Shared");
      service.saveTemplate(original);

      EntryTemplate updated = makeTemplate("Shared");
      SearchTemplateSection search = new SearchTemplateSection();
      search.setScope("one");
      updated.setSearchSection(search);
      service.saveTemplate(updated);

      List<EntryTemplate> loaded = service.loadTemplates();
      assertThat(loaded).hasSize(1);
      assertThat(loaded.get(0).getSearchSection()).isNotNull();
      assertThat(loaded.get(0).getSearchSection().getScope())
          .isEqualTo("one");
    }

    @Test
    void accumulatesMultipleTemplates() throws IOException {
      service.saveTemplate(makeTemplate("Alpha"));
      service.saveTemplate(makeTemplate("Beta"));
      service.saveTemplate(makeTemplate("Gamma"));

      assertThat(service.loadTemplates()).hasSize(3);
    }
  }

  // ---- deleteTemplate -------------------------------------------------

  @Nested
  class DeleteTemplate {

    @Test
    void removesByName() throws IOException {
      service.saveTemplates(List.of(
          makeTemplate("Keep"), makeTemplate("Remove")));
      service.deleteTemplate("Remove");

      List<EntryTemplate> loaded = service.loadTemplates();
      assertThat(loaded).hasSize(1);
      assertThat(loaded.get(0).getName()).isEqualTo("Keep");
    }

    @Test
    void silentlyIgnoresMissingName() throws IOException {
      service.saveTemplates(List.of(makeTemplate("Existing")));
      service.deleteTemplate("DoesNotExist");
      assertThat(service.loadTemplates()).hasSize(1);
    }
  }

  // ---- getTemplate / templateExists -----------------------------------

  @Nested
  class QueryMethods {

    @Test
    void findsExistingByName() throws IOException {
      service.saveTemplates(List.of(makeFullTemplate("Target")));
      Optional<EntryTemplate> result = service.getTemplate("Target");
      assertThat(result).isPresent();
      assertThat(result.get().getName()).isEqualTo("Target");
    }

    @Test
    void returnsEmptyForMissingName() {
      Optional<EntryTemplate> result = service.getTemplate("Ghost");
      assertThat(result).isEmpty();
    }

    @Test
    void existsTrueWhenPresent() throws IOException {
      service.saveTemplates(List.of(makeTemplate("Present")));
      assertThat(service.templateExists("Present")).isTrue();
    }

    @Test
    void existsFalseWhenAbsent() {
      assertThat(service.templateExists("Absent")).isFalse();
    }
  }

  // ---- getTemplatesForServer ------------------------------------------

  @Nested
  class GetTemplatesForServer {

    @Test
    void returnsAllWhenConfigIsNull() throws IOException {
      service.saveTemplates(List.of(
          makeTemplate("A"), makeTemplate("B")));
      List<EntryTemplate> result =
          service.getTemplatesForServer(null);
      assertThat(result).hasSize(2);
    }

    @Test
    void returnsAllWhenAllowedListEmpty() throws IOException {
      service.saveTemplates(List.of(
          makeTemplate("A"), makeTemplate("B")));
      LdapServerConfig cfg = new LdapServerConfig();
      cfg.setAllowedTemplates(new ArrayList<>());
      List<EntryTemplate> result =
          service.getTemplatesForServer(cfg);
      assertThat(result).hasSize(2);
    }

    @Test
    void filtersToAllowedNames() throws IOException {
      service.saveTemplates(List.of(
          makeTemplate("User"), makeTemplate("Group"),
          makeTemplate("OU")));
      LdapServerConfig cfg = new LdapServerConfig();
      cfg.setAllowedTemplates(List.of("User", "OU"));
      List<EntryTemplate> result =
          service.getTemplatesForServer(cfg);
      assertThat(result).hasSize(2);
      assertThat(result).extracting(EntryTemplate::getName)
          .containsExactlyInAnyOrder("User", "OU");
    }

    @Test
    void returnsEmptyWhenNoMatch() throws IOException {
      service.saveTemplates(List.of(makeTemplate("User")));
      LdapServerConfig cfg = new LdapServerConfig();
      cfg.setAllowedTemplates(List.of("NonExistent"));
      List<EntryTemplate> result =
          service.getTemplatesForServer(cfg);
      assertThat(result).isEmpty();
    }
  }
}
