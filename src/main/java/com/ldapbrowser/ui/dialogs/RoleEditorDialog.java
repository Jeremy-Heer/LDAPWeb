package com.ldapbrowser.ui.dialogs;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.model.Role;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.RoleService;
import com.ldapbrowser.service.UserService;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dialog for creating and editing application roles.
 *
 * <p>A role defines server members, user members, and which
 * application views are accessible. Follows the same fluent
 * callback pattern as {@link TemplateEditorDialog}.
 */
public class RoleEditorDialog extends Dialog {

  private final RoleService roleService;
  private final ConfigurationService configurationService;
  private final UserService userService;
  private final boolean isEdit;
  private final String originalName;
  private Consumer<Role> onSave;

  private TextField nameField;
  private Grid<String> serverGrid;
  private List<String> selectedServers = new ArrayList<>();
  private Grid<String> userGrid;
  private List<String> selectedUsers = new ArrayList<>();
  private CheckboxGroup<String> viewsGroup;

  /**
   * Creates a role editor dialog.
   *
   * @param roleService role service
   * @param configurationService configuration service for server list
   * @param userService user service for user list (may be null)
   * @param role existing role to edit, or null for new
   */
  public RoleEditorDialog(
      RoleService roleService,
      ConfigurationService configurationService,
      UserService userService,
      Role role) {
    this.roleService = roleService;
    this.configurationService = configurationService;
    this.userService = userService;
    this.isEdit = role != null;
    this.originalName = isEdit ? role.getName() : null;

    setHeaderTitle(isEdit ? "Edit Role" : "New Role");
    setWidth("700px");
    setHeight("650px");
    setModality(ModalityMode.VISUAL);
    setDraggable(true);
    setResizable(true);

    VerticalLayout content = new VerticalLayout();
    content.setSizeFull();
    content.setPadding(false);
    content.setSpacing(true);

    // Name
    nameField = new TextField("Role Name");
    nameField.setWidthFull();
    nameField.setRequired(true);
    content.add(nameField);

    // Server Members
    content.add(createServerSection());

    // User Members
    content.add(createUserSection());

    // Application Views
    viewsGroup = new CheckboxGroup<>("Application Views");
    viewsGroup.setItems(Role.ALL_VIEWS);
    viewsGroup.setWidthFull();
    content.add(viewsGroup);

    // Populate if editing
    if (isEdit) {
      nameField.setValue(role.getName());
      selectedServers.addAll(role.getServerMembers());
      serverGrid.setItems(selectedServers);
      selectedUsers.addAll(role.getUserMembers());
      userGrid.setItems(selectedUsers);
      viewsGroup.select(
          role.getAllowedViews().toArray(new String[0]));
    }

    add(content);

    // Footer buttons
    Button saveButton = new Button("Save", event -> save());
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    Button cancelButton = new Button("Cancel",
        event -> close());
    getFooter().add(cancelButton, saveButton);
  }

  /**
   * Sets the save callback.
   *
   * @param onSave callback invoked after successful save
   * @return this dialog (fluent)
   */
  public RoleEditorDialog onSave(Consumer<Role> onSave) {
    this.onSave = onSave;
    return this;
  }

  // ------------------------------------------------------------------
  // Server members section
  // ------------------------------------------------------------------

  private VerticalLayout createServerSection() {
    VerticalLayout section = new VerticalLayout();
    section.setPadding(false);
    section.setSpacing(true);

    Span label = new Span("Server Members");
    label.getStyle().set("font-weight", "500");

    // Selector + Add button
    List<String> allServers = configurationService
        .loadConfigurations().stream()
        .map(LdapServerConfig::getName).toList();

    Select<String> serverSelector = new Select<>();
    serverSelector.setItems(allServers);
    serverSelector.setPlaceholder("Select server...");
    serverSelector.setWidth("250px");

    Button addBtn = new Button(new Icon(VaadinIcon.PLUS),
        e -> {
          String val = serverSelector.getValue();
          if (val != null && !selectedServers.contains(val)) {
            selectedServers.add(val);
            serverGrid.setItems(selectedServers);
          }
        });
    addBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);

    Button removeBtn = new Button(new Icon(VaadinIcon.MINUS),
        e -> {
          String sel = serverGrid.asSingleSelect().getValue();
          if (sel != null) {
            selectedServers.remove(sel);
            serverGrid.setItems(selectedServers);
          }
        });
    removeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    removeBtn.setEnabled(false);

    HorizontalLayout toolbar = new HorizontalLayout(
        serverSelector, addBtn, removeBtn);
    toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);

    serverGrid = new Grid<>();
    serverGrid.addColumn(s -> s).setHeader("Server Name")
        .setFlexGrow(1);
    serverGrid.setHeight("120px");
    serverGrid.setWidthFull();
    serverGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
    serverGrid.addSelectionListener(
        e -> removeBtn.setEnabled(
            e.getFirstSelectedItem().isPresent()));

    section.add(label, toolbar, serverGrid);
    return section;
  }

  // ------------------------------------------------------------------
  // User members section
  // ------------------------------------------------------------------

  private VerticalLayout createUserSection() {
    VerticalLayout section = new VerticalLayout();
    section.setPadding(false);
    section.setSpacing(true);

    Span label = new Span("User Members");
    label.getStyle().set("font-weight", "500");

    List<String> allUsers = new ArrayList<>();
    if (userService != null) {
      allUsers.addAll(userService.loadUsers().stream()
          .map(UserService.UserRecord::username).toList());
    }

    Select<String> userSelector = new Select<>();
    userSelector.setItems(allUsers);
    userSelector.setPlaceholder("Select user...");
    userSelector.setWidth("250px");

    Button addBtn = new Button(new Icon(VaadinIcon.PLUS),
        e -> {
          String val = userSelector.getValue();
          if (val != null && !selectedUsers.contains(val)) {
            selectedUsers.add(val);
            userGrid.setItems(selectedUsers);
          }
        });
    addBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);

    Button removeBtn = new Button(new Icon(VaadinIcon.MINUS),
        e -> {
          String sel = userGrid.asSingleSelect().getValue();
          if (sel != null) {
            // Warn if this would leave the user role-less
            if (!canRemoveUserFromRole(sel)) {
              NotificationHelper.showError(
                  "Cannot remove '" + sel
                      + "' — they must remain in at least one role",
                  3000);
              return;
            }
            selectedUsers.remove(sel);
            userGrid.setItems(selectedUsers);
          }
        });
    removeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
    removeBtn.setEnabled(false);

    HorizontalLayout toolbar = new HorizontalLayout(
        userSelector, addBtn, removeBtn);
    toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);

    userGrid = new Grid<>();
    userGrid.addColumn(s -> s).setHeader("Username")
        .setFlexGrow(1);
    userGrid.setHeight("120px");
    userGrid.setWidthFull();
    userGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
    userGrid.addSelectionListener(
        e -> removeBtn.setEnabled(
            e.getFirstSelectedItem().isPresent()));

    section.add(label, toolbar, userGrid);
    return section;
  }

  // ------------------------------------------------------------------
  // Save
  // ------------------------------------------------------------------

  private void save() {
    String name = nameField.getValue();
    if (name == null || name.trim().isEmpty()) {
      NotificationHelper.showError(
          "Role name is required", 3000);
      return;
    }
    name = name.trim();

    // Uniqueness check (allow same name when editing)
    if (!name.equals(originalName)
        && roleService.roleExists(name)) {
      NotificationHelper.showError(
          "A role with this name already exists", 3000);
      return;
    }

    Set<String> views = viewsGroup.getSelectedItems();
    if (views.isEmpty()) {
      NotificationHelper.showError(
          "At least one view must be selected", 3000);
      return;
    }

    Role role = new Role(name);
    role.setServerMembers(new ArrayList<>(selectedServers));
    role.setUserMembers(new ArrayList<>(selectedUsers));
    role.setAllowedViews(new ArrayList<>(
        new LinkedHashSet<>(views)));

    try {
      // Handle rename: delete old name first
      if (isEdit && originalName != null
          && !originalName.equals(name)) {
        roleService.deleteRole(originalName);
      }
      roleService.saveRole(role);
      NotificationHelper.showSuccess(
          "Role '" + name + "' saved", 3000);
      if (onSave != null) {
        onSave.accept(role);
      }
      close();
    } catch (Exception e) {
      NotificationHelper.showError(
          "Failed to save role: " + e.getMessage(), 5000);
    }
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  /**
   * Checks whether removing a user from this role would leave them
   * without any role assignment.
   */
  private boolean canRemoveUserFromRole(String username) {
    // If editing an existing role, check other roles
    List<Role> otherRoles = roleService.getRolesForUser(username)
        .stream()
        .filter(r -> !r.getName().equals(
            isEdit ? originalName : ""))
        .toList();
    return !otherRoles.isEmpty();
  }
}
