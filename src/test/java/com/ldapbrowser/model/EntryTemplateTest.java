package com.ldapbrowser.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.ldapbrowser.model.EntryTemplate.CreateTemplateSection;
import com.ldapbrowser.model.EntryTemplate.FieldType;
import com.ldapbrowser.model.EntryTemplate.SearchTemplateSection;
import com.ldapbrowser.model.EntryTemplate.TemplateAttribute;
import com.ldapbrowser.model.EntryTemplate.ViewEditTemplateSection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link EntryTemplate}.
 */
@DisplayName("EntryTemplate")
class EntryTemplateTest {

  private final ObjectMapper mapper = JsonMapper.builder()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .build();

  // ---- helpers --------------------------------------------------------

  private EntryTemplate makeTemplate(String name) {
    EntryTemplate template = new EntryTemplate(name);
    return template;
  }

  private TemplateAttribute makeAttribute(String display, String ldap) {
    TemplateAttribute attr = new TemplateAttribute(display, ldap);
    attr.setRequired(true);
    attr.setFieldType(FieldType.TEXT);
    return attr;
  }

  // ---- equality -------------------------------------------------------

  @Nested
  @DisplayName("Equality")
  class Equality {

    @Test
    @DisplayName("equal by name")
    void equalByName() {
      EntryTemplate a = makeTemplate("User");
      EntryTemplate b = makeTemplate("User");
      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("not equal with different names")
    void notEqualDifferentNames() {
      EntryTemplate a = makeTemplate("User");
      EntryTemplate b = makeTemplate("Group");
      assertThat(a).isNotEqualTo(b);
    }
  }

  // ---- JSON round-trip ------------------------------------------------

  @Nested
  @DisplayName("JSON serialization")
  class JsonSerialization {

    @Test
    @DisplayName("round-trip with all sections")
    void roundTripAllSections() throws Exception {
      EntryTemplate template = makeTemplate("User");

      // Create section
      CreateTemplateSection create = new CreateTemplateSection();
      create.setRdn("uid={UID}");
      create.setParentFilter(
          "(&(objectClass=organizationalUnit)(ou=people))");
      TemplateAttribute oc = new TemplateAttribute(
          "Object Class", "objectClass");
      oc.setFieldType(FieldType.MULTI_VALUED_TEXT);
      oc.setHidden(true);
      oc.setValues(List.of("inetOrgPerson", "person"));
      TemplateAttribute uid = makeAttribute("User Name", "uid");
      create.setAttributes(List.of(oc, uid));
      template.setCreateSection(create);

      // View/Edit section
      ViewEditTemplateSection viewEdit = new ViewEditTemplateSection();
      viewEdit.setMatchingFilter("(objectClass=inetOrgPerson)");
      TemplateAttribute cn = makeAttribute("Common Name", "cn");
      viewEdit.setAttributes(List.of(cn));
      template.setViewEditSection(viewEdit);

      // Search section
      SearchTemplateSection search = new SearchTemplateSection();
      search.setSearchFilter(
          "(&(objectClass=inetOrgPerson)(uid={SEARCH}))");
      search.setBaseFilter("(objectClass=organizationalUnit)");
      search.setScope("sub");
      search.setReturnAttributes(List.of("cn", "uid", "mail"));
      template.setSearchSection(search);

      // Serialize and deserialize
      String json = mapper.writeValueAsString(template);
      EntryTemplate loaded = mapper.readValue(json, EntryTemplate.class);

      assertThat(loaded.getName()).isEqualTo("User");

      // Create section
      assertThat(loaded.getCreateSection()).isNotNull();
      assertThat(loaded.getCreateSection().getRdn())
          .isEqualTo("uid={UID}");
      assertThat(loaded.getCreateSection().getParentFilter())
          .contains("organizationalUnit");
      assertThat(loaded.getCreateSection().getAttributes()).hasSize(2);

      TemplateAttribute loadedOc =
          loaded.getCreateSection().getAttributes().get(0);
      assertThat(loadedOc.getDisplayName()).isEqualTo("Object Class");
      assertThat(loadedOc.getLdapAttributeName())
          .isEqualTo("objectClass");
      assertThat(loadedOc.getFieldType())
          .isEqualTo(FieldType.MULTI_VALUED_TEXT);
      assertThat(loadedOc.isHidden()).isTrue();
      assertThat(loadedOc.getValues())
          .containsExactly("inetOrgPerson", "person");

      // View/Edit section
      assertThat(loaded.getViewEditSection()).isNotNull();
      assertThat(loaded.getViewEditSection().getMatchingFilter())
          .isEqualTo("(objectClass=inetOrgPerson)");
      assertThat(loaded.getViewEditSection().getAttributes())
          .hasSize(1);

      // Search section
      assertThat(loaded.getSearchSection()).isNotNull();
      assertThat(loaded.getSearchSection().getSearchFilter())
          .contains("{SEARCH}");
      assertThat(loaded.getSearchSection().getScope())
          .isEqualTo("sub");
      assertThat(loaded.getSearchSection().getReturnAttributes())
          .containsExactly("cn", "uid", "mail");
    }

    @Test
    @DisplayName("round-trip with only create section")
    void roundTripCreateOnly() throws Exception {
      EntryTemplate template = makeTemplate("OU");
      CreateTemplateSection create = new CreateTemplateSection();
      create.setRdn("ou={OU}");
      create.setAttributes(List.of(
          makeAttribute("OU Name", "ou")));
      template.setCreateSection(create);

      String json = mapper.writeValueAsString(template);
      EntryTemplate loaded = mapper.readValue(json, EntryTemplate.class);

      assertThat(loaded.getCreateSection()).isNotNull();
      assertThat(loaded.getViewEditSection()).isNull();
      assertThat(loaded.getSearchSection()).isNull();
    }

    @Test
    @DisplayName("round-trip with null sections")
    void roundTripNullSections() throws Exception {
      EntryTemplate template = makeTemplate("Empty");

      String json = mapper.writeValueAsString(template);
      EntryTemplate loaded = mapper.readValue(json, EntryTemplate.class);

      assertThat(loaded.getName()).isEqualTo("Empty");
      assertThat(loaded.getCreateSection()).isNull();
      assertThat(loaded.getViewEditSection()).isNull();
      assertThat(loaded.getSearchSection()).isNull();
    }

    @Test
    @DisplayName("array round-trip")
    void arrayRoundTrip() throws Exception {
      EntryTemplate t1 = makeTemplate("User");
      EntryTemplate t2 = makeTemplate("Group");
      EntryTemplate[] arr = {t1, t2};

      String json = mapper.writeValueAsString(arr);
      EntryTemplate[] loaded = mapper.readValue(
          json, EntryTemplate[].class);

      assertThat(loaded).hasSize(2);
      assertThat(loaded[0].getName()).isEqualTo("User");
      assertThat(loaded[1].getName()).isEqualTo("Group");
    }
  }

  // ---- FieldType enum -------------------------------------------------

  @Nested
  @DisplayName("FieldType")
  class FieldTypeTest {

    @Test
    @DisplayName("all field types serializable")
    void allFieldTypesRoundTrip() throws Exception {
      for (FieldType ft : FieldType.values()) {
        TemplateAttribute attr = new TemplateAttribute();
        attr.setDisplayName("test");
        attr.setLdapAttributeName("test");
        attr.setFieldType(ft);

        String json = mapper.writeValueAsString(attr);
        TemplateAttribute loaded =
            mapper.readValue(json, TemplateAttribute.class);
        assertThat(loaded.getFieldType()).isEqualTo(ft);
      }
    }
  }

  // ---- TemplateAttribute defaults -------------------------------------

  @Nested
  @DisplayName("TemplateAttribute defaults")
  class AttributeDefaults {

    @Test
    @DisplayName("default field type is TEXT")
    void defaultFieldType() {
      TemplateAttribute attr = new TemplateAttribute();
      assertThat(attr.getFieldType()).isEqualTo(FieldType.TEXT);
    }

    @Test
    @DisplayName("default hidden is false")
    void defaultHidden() {
      TemplateAttribute attr = new TemplateAttribute();
      assertThat(attr.isHidden()).isFalse();
    }

    @Test
    @DisplayName("default required is false")
    void defaultRequired() {
      TemplateAttribute attr = new TemplateAttribute();
      assertThat(attr.isRequired()).isFalse();
    }

    @Test
    @DisplayName("default values is empty list")
    void defaultValues() {
      TemplateAttribute attr = new TemplateAttribute();
      assertThat(attr.getValues()).isEmpty();
    }

    @Test
    @DisplayName("setValues with null yields empty list")
    void setNullValues() {
      TemplateAttribute attr = new TemplateAttribute();
      attr.setValues(null);
      assertThat(attr.getValues()).isEmpty();
    }
  }

  // ---- SearchTemplateSection defaults ---------------------------------

  @Nested
  @DisplayName("SearchTemplateSection defaults")
  class SearchDefaults {

    @Test
    @DisplayName("default scope is sub")
    void defaultScope() {
      SearchTemplateSection search = new SearchTemplateSection();
      assertThat(search.getScope()).isEqualTo("sub");
    }

    @Test
    @DisplayName("default returnAttributes is empty list")
    void defaultReturnAttributes() {
      SearchTemplateSection search = new SearchTemplateSection();
      assertThat(search.getReturnAttributes()).isEmpty();
    }

    @Test
    @DisplayName("default baseDn is null")
    void defaultBaseDn() {
      SearchTemplateSection search = new SearchTemplateSection();
      assertThat(search.getBaseDn()).isNull();
    }
  }

  // ---- CreateTemplateSection baseDn -----------------------------------

  @Nested
  @DisplayName("CreateTemplateSection baseDn")
  class CreateBaseDn {

    @Test
    @DisplayName("default baseDn is null")
    void defaultBaseDn() {
      CreateTemplateSection create = new CreateTemplateSection();
      assertThat(create.getBaseDn()).isNull();
    }

    @Test
    @DisplayName("set and get baseDn")
    void setAndGet() {
      CreateTemplateSection create = new CreateTemplateSection();
      create.setBaseDn("kerberos");
      assertThat(create.getBaseDn()).isEqualTo("kerberos");
    }

    @Test
    @DisplayName("baseDn round-trips in JSON")
    void jsonRoundTrip() throws Exception {
      EntryTemplate template = makeTemplate("TestCreate");
      CreateTemplateSection cs = new CreateTemplateSection();
      cs.setRdn("uid={UID}");
      cs.setBaseDn("kerberos");
      cs.setAttributes(List.of(makeAttribute("Name", "cn")));
      template.setCreateSection(cs);

      String json = mapper.writeValueAsString(template);
      EntryTemplate loaded =
          mapper.readValue(json, EntryTemplate.class);
      assertThat(loaded.getCreateSection().getBaseDn())
          .isEqualTo("kerberos");
    }
  }

  // ---- SearchTemplateSection baseDn -----------------------------------

  @Nested
  @DisplayName("SearchTemplateSection baseDn")
  class SearchBaseDn {

    @Test
    @DisplayName("set and get baseDn")
    void setAndGet() {
      SearchTemplateSection search = new SearchTemplateSection();
      search.setBaseDn("dns");
      assertThat(search.getBaseDn()).isEqualTo("dns");
    }

    @Test
    @DisplayName("baseDn round-trips in JSON")
    void jsonRoundTrip() throws Exception {
      EntryTemplate template = makeTemplate("TestSearch");
      SearchTemplateSection ss = new SearchTemplateSection();
      ss.setSearchFilter("(uid={SEARCH})");
      ss.setBaseDn("kerberos");
      ss.setScope("sub");
      template.setSearchSection(ss);

      String json = mapper.writeValueAsString(template);
      EntryTemplate loaded =
          mapper.readValue(json, EntryTemplate.class);
      assertThat(loaded.getSearchSection().getBaseDn())
          .isEqualTo("kerberos");
    }
  }
}
