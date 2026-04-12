package com.ldapbrowser.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Model class representing an entry template with optional sections
 * for creating, viewing/editing, and searching LDAP entries.
 */
public class EntryTemplate implements Serializable {

  private static final long serialVersionUID = 1L;

  private String name;
  private CreateTemplateSection createSection;
  private ViewEditTemplateSection viewEditSection;
  private SearchTemplateSection searchSection;

  /**
   * Default constructor.
   */
  public EntryTemplate() {
  }

  /**
   * Constructor with name.
   *
   * @param name template name
   */
  public EntryTemplate(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public CreateTemplateSection getCreateSection() {
    return createSection;
  }

  public void setCreateSection(CreateTemplateSection createSection) {
    this.createSection = createSection;
  }

  public ViewEditTemplateSection getViewEditSection() {
    return viewEditSection;
  }

  public void setViewEditSection(
      ViewEditTemplateSection viewEditSection) {
    this.viewEditSection = viewEditSection;
  }

  public SearchTemplateSection getSearchSection() {
    return searchSection;
  }

  public void setSearchSection(SearchTemplateSection searchSection) {
    this.searchSection = searchSection;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EntryTemplate that = (EntryTemplate) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    return "EntryTemplate{name='" + name + "'}";
  }

  /**
   * Field type for template attributes.
   */
  public enum FieldType {
    TEXT,
    MULTI_VALUED_TEXT,
    BOOLEAN,
    SELECT_LIST,
    PASSWORD,
    SEARCH
  }

  /**
   * Represents a single attribute definition within a template.
   */
  public static class TemplateAttribute implements Serializable {

    private static final long serialVersionUID = 1L;

    private String displayName;
    private String ldapAttributeName;
    private boolean required;
    private FieldType fieldType = FieldType.TEXT;
    private boolean hidden;
    private List<String> values = new ArrayList<>();

    /**
     * Default constructor.
     */
    public TemplateAttribute() {
    }

    /**
     * Constructor with display name and LDAP attribute name.
     *
     * @param displayName human-readable label
     * @param ldapAttributeName LDAP attribute name
     */
    public TemplateAttribute(String displayName,
        String ldapAttributeName) {
      this.displayName = displayName;
      this.ldapAttributeName = ldapAttributeName;
    }

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }

    public String getLdapAttributeName() {
      return ldapAttributeName;
    }

    public void setLdapAttributeName(String ldapAttributeName) {
      this.ldapAttributeName = ldapAttributeName;
    }

    public boolean isRequired() {
      return required;
    }

    public void setRequired(boolean required) {
      this.required = required;
    }

    public FieldType getFieldType() {
      return fieldType;
    }

    public void setFieldType(FieldType fieldType) {
      this.fieldType = fieldType;
    }

    public boolean isHidden() {
      return hidden;
    }

    public void setHidden(boolean hidden) {
      this.hidden = hidden;
    }

    public List<String> getValues() {
      return values;
    }

    public void setValues(List<String> values) {
      this.values = values != null ? values : new ArrayList<>();
    }
  }

  /**
   * Section defining how to create entries using this template.
   */
  public static class CreateTemplateSection implements Serializable {

    private static final long serialVersionUID = 1L;

    private String rdn;
    private String parentFilter;
    private String baseDn;
    private List<TemplateAttribute> attributes = new ArrayList<>();

    /**
     * Default constructor.
     */
    public CreateTemplateSection() {
    }

    public String getRdn() {
      return rdn;
    }

    public void setRdn(String rdn) {
      this.rdn = rdn;
    }

    public String getParentFilter() {
      return parentFilter;
    }

    public void setParentFilter(String parentFilter) {
      this.parentFilter = parentFilter;
    }

    public String getBaseDn() {
      return baseDn;
    }

    public void setBaseDn(String baseDn) {
      this.baseDn = baseDn;
    }

    public List<TemplateAttribute> getAttributes() {
      return attributes;
    }

    public void setAttributes(List<TemplateAttribute> attributes) {
      this.attributes = attributes != null
          ? attributes : new ArrayList<>();
    }
  }

  /**
   * Section defining how to view/edit entries using this template.
   */
  public static class ViewEditTemplateSection implements Serializable {

    private static final long serialVersionUID = 1L;

    private String matchingFilter;
    private List<TemplateAttribute> attributes = new ArrayList<>();

    /**
     * Default constructor.
     */
    public ViewEditTemplateSection() {
    }

    public String getMatchingFilter() {
      return matchingFilter;
    }

    public void setMatchingFilter(String matchingFilter) {
      this.matchingFilter = matchingFilter;
    }

    public List<TemplateAttribute> getAttributes() {
      return attributes;
    }

    public void setAttributes(List<TemplateAttribute> attributes) {
      this.attributes = attributes != null
          ? attributes : new ArrayList<>();
    }
  }

  /**
   * Section defining how to search entries using this template.
   */
  public static class SearchTemplateSection implements Serializable {

    private static final long serialVersionUID = 1L;

    private String searchFilter;
    private String baseFilter;
    private String baseDn;
    private String scope = "sub";
    private List<String> returnAttributes = new ArrayList<>();

    /**
     * Default constructor.
     */
    public SearchTemplateSection() {
    }

    public String getSearchFilter() {
      return searchFilter;
    }

    public void setSearchFilter(String searchFilter) {
      this.searchFilter = searchFilter;
    }

    public String getBaseFilter() {
      return baseFilter;
    }

    public void setBaseFilter(String baseFilter) {
      this.baseFilter = baseFilter;
    }

    public String getBaseDn() {
      return baseDn;
    }

    public void setBaseDn(String baseDn) {
      this.baseDn = baseDn;
    }

    public String getScope() {
      return scope;
    }

    public void setScope(String scope) {
      this.scope = scope;
    }

    public List<String> getReturnAttributes() {
      return returnAttributes;
    }

    public void setReturnAttributes(List<String> returnAttributes) {
      this.returnAttributes = returnAttributes != null
          ? returnAttributes : new ArrayList<>();
    }
  }
}
