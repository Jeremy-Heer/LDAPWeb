package com.ldapbrowser.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an LDAP entry with its attributes.
 */
public class LdapEntry {
  
  private String dn;
  private String serverName;
  private Map<String, List<String>> attributes;
  private Map<String, List<String>> operationalAttributes;
  
  /**
   * Creates a new LDAP entry.
   */
  public LdapEntry() {
    this.attributes = new HashMap<>();
    this.operationalAttributes = new HashMap<>();
  }
  
  /**
   * Creates a new LDAP entry with DN and server.
   *
   * @param dn distinguished name
   * @param serverName server name
   */
  public LdapEntry(String dn, String serverName) {
    this();
    this.dn = dn;
    this.serverName = serverName;
  }
  
  public String getDn() {
    return dn;
  }
  
  public void setDn(String dn) {
    this.dn = dn;
  }
  
  public String getServerName() {
    return serverName;
  }
  
  public void setServerName(String serverName) {
    this.serverName = serverName;
  }
  
  public Map<String, List<String>> getAttributes() {
    return attributes;
  }
  
  public void setAttributes(Map<String, List<String>> attributes) {
    this.attributes = attributes;
  }
  
  public Map<String, List<String>> getOperationalAttributes() {
    return operationalAttributes;
  }
  
  public void setOperationalAttributes(Map<String, List<String>> operationalAttributes) {
    this.operationalAttributes = operationalAttributes;
  }
  
  /**
   * Adds an attribute value.
   *
   * @param name attribute name
   * @param value attribute value
   */
  public void addAttribute(String name, String value) {
    attributes.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
  }
  
  /**
   * Adds an operational attribute value.
   *
   * @param name attribute name
   * @param value attribute value
   */
  public void addOperationalAttribute(String name, String value) {
    operationalAttributes.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
  }
  
  /**
   * Gets all attribute values for a name.
   *
   * @param name attribute name
   * @return list of values
   */
  public List<String> getAttributeValues(String name) {
    return attributes.getOrDefault(name, new ArrayList<>());
  }
  
  /**
   * Gets first attribute value.
   *
   * @param name attribute name
   * @return first value or null
   */
  public String getFirstAttributeValue(String name) {
    List<String> values = getAttributeValues(name);
    return values.isEmpty() ? null : values.get(0);
  }
}
