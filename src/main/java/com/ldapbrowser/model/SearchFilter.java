package com.ldapbrowser.model;

import com.unboundid.ldap.sdk.Filter;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an LDAP search filter for the filter builder.
 */
public class SearchFilter {
  
  /**
   * Filter operator types.
   */
  public enum Operator {
    AND("&", "And"),
    OR("|", "Or"),
    NOT("!", "Not"),
    EQUAL("=", "Equals"),
    APPROX("~=", "Approximately Equals"),
    GREATER_OR_EQUAL(">=", "Greater Than or Equal"),
    LESS_OR_EQUAL("<=", "Less Than or Equal"),
    PRESENT("=*", "Present"),
    SUBSTRING("=*", "Substring");
    
    private final String ldapSymbol;
    private final String displayName;
    
    Operator(String ldapSymbol, String displayName) {
      this.ldapSymbol = ldapSymbol;
      this.displayName = displayName;
    }
    
    public String getLdapSymbol() {
      return ldapSymbol;
    }
    
    public String getDisplayName() {
      return displayName;
    }
  }
  
  private Operator operator;
  private String attribute;
  private String value;
  private List<SearchFilter> children;
  
  /**
   * Creates a simple filter.
   *
   * @param operator filter operator
   * @param attribute attribute name
   * @param value attribute value
   */
  public SearchFilter(Operator operator, String attribute, String value) {
    this.operator = operator;
    this.attribute = attribute;
    this.value = value;
    this.children = new ArrayList<>();
  }
  
  /**
   * Creates a composite filter.
   *
   * @param operator filter operator (AND, OR, NOT)
   */
  public SearchFilter(Operator operator) {
    this.operator = operator;
    this.children = new ArrayList<>();
  }
  
  public Operator getOperator() {
    return operator;
  }
  
  public void setOperator(Operator operator) {
    this.operator = operator;
  }
  
  public String getAttribute() {
    return attribute;
  }
  
  public void setAttribute(String attribute) {
    this.attribute = attribute;
  }
  
  public String getValue() {
    return value;
  }
  
  public void setValue(String value) {
    this.value = value;
  }
  
  public List<SearchFilter> getChildren() {
    return children;
  }
  
  public void addChild(SearchFilter filter) {
    children.add(filter);
  }
  
  /**
   * Converts filter to LDAP filter string.
   *
   * @return LDAP filter string
   */
  public String toLdapFilter() {
    if (children.isEmpty()) {
      // Simple filter — escape value to prevent LDAP filter injection
      String attr = attribute != null ? attribute : "null";
      if (operator == Operator.PRESENT) {
        return "(" + attr + "=*)";
      } else if (operator == Operator.SUBSTRING) {
        String encoded = value != null ? Filter.encodeValue(value) : "null";
        return "(" + attr + "=*" + encoded + "*)";
      } else {
        String encoded = value != null ? Filter.encodeValue(value) : "null";
        return "(" + attr + operator.getLdapSymbol() + encoded + ")";
      }
    } else {
      // Composite filter
      StringBuilder sb = new StringBuilder();
      sb.append("(").append(operator.getLdapSymbol());
      for (SearchFilter child : children) {
        sb.append(child.toLdapFilter());
      }
      sb.append(")");
      return sb.toString();
    }
  }
}
