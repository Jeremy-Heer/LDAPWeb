package com.ldapbrowser.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Model class representing an application role.
 *
 * <p>A role defines which servers, users, and application views are
 * accessible to its members. Persisted as JSON in {@code roles.json}.
 */
public class Role implements Serializable {

  private static final long serialVersionUID = 1L;

  /** All view labels that can appear in {@link #allowedViews}. */
  public static final List<String> ALL_VIEWS = List.of(
      "Access", "Browse", "Bulk", "Create", "Export",
      "Schema", "Search", "Server", "Settings");

  private String name;
  private List<String> serverMembers = new ArrayList<>();
  private List<String> userMembers = new ArrayList<>();
  private List<String> allowedViews = new ArrayList<>();

  /** Default constructor for Jackson. */
  public Role() {
  }

  /**
   * Creates a role with the given name.
   *
   * @param name unique role name
   */
  public Role(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<String> getServerMembers() {
    return serverMembers;
  }

  public void setServerMembers(List<String> serverMembers) {
    this.serverMembers =
        serverMembers != null ? serverMembers : new ArrayList<>();
  }

  public List<String> getUserMembers() {
    return userMembers;
  }

  public void setUserMembers(List<String> userMembers) {
    this.userMembers =
        userMembers != null ? userMembers : new ArrayList<>();
  }

  public List<String> getAllowedViews() {
    return allowedViews;
  }

  public void setAllowedViews(List<String> allowedViews) {
    this.allowedViews =
        allowedViews != null ? allowedViews : new ArrayList<>();
  }

  /**
   * Creates a deep copy of this role.
   *
   * @return new instance with the same values
   */
  public Role copy() {
    Role copied = new Role(this.name + " (Copy)");
    copied.setServerMembers(new ArrayList<>(this.serverMembers));
    copied.setUserMembers(new ArrayList<>(this.userMembers));
    copied.setAllowedViews(new ArrayList<>(this.allowedViews));
    return copied;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Role role = (Role) o;
    return Objects.equals(name, role.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    return String.format("Role{name='%s', servers=%d, users=%d, views=%d}",
        name,
        serverMembers != null ? serverMembers.size() : 0,
        userMembers != null ? userMembers.size() : 0,
        allowedViews != null ? allowedViews.size() : 0);
  }
}
