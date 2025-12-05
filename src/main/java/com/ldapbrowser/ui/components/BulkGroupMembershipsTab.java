package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.service.TruststoreService;
import com.ldapbrowser.ui.dialogs.DnBrowserDialog;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.server.StreamResource;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.controls.PermissiveModifyRequestControl;
import com.unboundid.ldap.sdk.LDAPURL;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Group Memberships sub-tab for bulk operations on group memberships
 */
public class BulkGroupMembershipsTab extends VerticalLayout {

  private final LdapService ldapService;
  private final LoggingService loggingService;
  private final TruststoreService truststoreService;

  // Server configurations (multi-server support)
  private List<LdapServerConfig> serverConfigs;

  // UI Components
  private TextField groupDnField;
  private Button groupDnBrowseButton;
  private TextField userBaseDnField;
  private Button userBaseDnBrowseButton;
  private ComboBox<String> operationComboBox;
  private TextArea userListArea;
  private Upload fileUpload;
  private MemoryBuffer memoryBuffer;
  private Checkbox continueOnErrorCheckbox;
  private Checkbox permissiveModifyCheckbox;
  private ComboBox<String> operationModeCombo;
  private Button runButton;

  // Progress
  private ProgressBar progressBar;
  private VerticalLayout progressContainer;
  private Anchor downloadLink;

  public BulkGroupMembershipsTab(LdapService ldapService, LoggingService loggingService,
      TruststoreService truststoreService) {
    this.ldapService = ldapService;
    this.loggingService = loggingService;
    this.truststoreService = truststoreService;
    this.serverConfigs = new ArrayList<>();

    initializeComponents();
    setupLayout();
  }

  private void initializeComponents() {
    // Group DN field
    groupDnField = new TextField("Group DN");
    groupDnField.setWidthFull();
    groupDnField.setPlaceholder("cn=admins,ou=groups,dc=example,dc=com");
    groupDnField.setRequired(true);

    groupDnBrowseButton = new Button(new Icon(VaadinIcon.FOLDER_OPEN));
    groupDnBrowseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    groupDnBrowseButton.setTooltipText("Browse LDAP directory");
    groupDnBrowseButton.addClickListener(e -> showDnBrowserDialog(groupDnField));

    // User Base DN field
    userBaseDnField = new TextField("User Base DN (Optional)");
    userBaseDnField.setWidthFull();
    userBaseDnField.setPlaceholder("If empty, uses server Base DN");

    userBaseDnBrowseButton = new Button(new Icon(VaadinIcon.FOLDER_OPEN));
    userBaseDnBrowseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    userBaseDnBrowseButton.setTooltipText("Browse LDAP directory");
    userBaseDnBrowseButton.addClickListener(e -> showDnBrowserDialog(userBaseDnField));

    // Operation selector
    operationComboBox = new ComboBox<>("Operation");
    operationComboBox.setItems("Add Members", "Remove Members");
    operationComboBox.setValue("Add Members");
    operationComboBox.setWidth("15em");

    // User list area
    userListArea = new TextArea("User List");
    userListArea.setWidthFull();
    userListArea.setHeight("200px");
    userListArea.setPlaceholder("jdoe\nmsmith\nabrown");
    userListArea.setHelperText("Enter user IDs (UIDs), one per line. You can also upload a text file.");

    // File upload
    memoryBuffer = new MemoryBuffer();
    fileUpload = new Upload(memoryBuffer);
    fileUpload.setUploadButton(new Button("Upload User List", new Icon(VaadinIcon.UPLOAD)));
    fileUpload.setDropLabel(new Span("Drop text file here"));
    fileUpload.setAcceptedFileTypes(".txt", ".csv");
    fileUpload.setMaxFiles(1);
    fileUpload.setMaxFileSize(1024 * 1024); // 1MB limit

    fileUpload.addSucceededListener(event -> {
      try {
        String content = new String(memoryBuffer.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        userListArea.setValue(content.trim());
        NotificationHelper.showInfo("File uploaded successfully: " + event.getFileName());
      } catch (Exception e) {
        NotificationHelper.showError("Failed to read file: " + e.getMessage());
      }
    });

    fileUpload.addFailedListener(event -> {
      NotificationHelper.showError("File upload failed: " + event.getReason().getMessage());
    });

    // Option checkboxes
    continueOnErrorCheckbox = new Checkbox("Continue on error");
    continueOnErrorCheckbox.setValue(true);

    permissiveModifyCheckbox = new Checkbox("Use Permissive Modify control");
    permissiveModifyCheckbox.setValue(true);

    // Operation mode selector
    operationModeCombo = new ComboBox<>("Operation Mode");
    operationModeCombo.setItems("Execute Change", "Create LDIF");
    operationModeCombo.setValue("Execute Change");
    operationModeCombo.setWidthFull();

    // Run button
    runButton = new Button("Run", new Icon(VaadinIcon.PLAY));
    runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    runButton.addClickListener(e -> performBulkGroupOperation());

    // Progress components
    progressBar = new ProgressBar();
    progressBar.setIndeterminate(true);

    progressContainer = new VerticalLayout();
    progressContainer.setPadding(false);
    progressContainer.setSpacing(true);
    progressContainer.setDefaultHorizontalComponentAlignment(Alignment.CENTER);
    progressContainer.add(new Span("Processing group membership operations..."), progressBar);
    progressContainer.setVisible(false);

    // Download link (for LDIF output)
    downloadLink = new Anchor();
    downloadLink.getElement().setAttribute("download", true);
    downloadLink.setVisible(false);
    downloadLink.add(new Button("Download LDIF", new Icon(VaadinIcon.DOWNLOAD)));
  }

  private void setupLayout() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);
    addClassName("bulk-group-memberships-tab");

    // Main content layout
    VerticalLayout contentLayout = new VerticalLayout();
    contentLayout.setPadding(true);
    contentLayout.setSpacing(true);
    contentLayout.addClassName("bulk-group-memberships-field-group");

    // Group DN layout (field + browse button)
    HorizontalLayout groupDnLayout = new HorizontalLayout();
    groupDnLayout.setSpacing(false);
    groupDnLayout.setAlignItems(Alignment.END);
    groupDnLayout.add(groupDnField, groupDnBrowseButton);
    groupDnLayout.setFlexGrow(1, groupDnField);

    // User Base DN layout (field + browse button)
    HorizontalLayout userBaseDnLayout = new HorizontalLayout();
    userBaseDnLayout.setSpacing(false);
    userBaseDnLayout.setAlignItems(Alignment.END);
    userBaseDnLayout.add(userBaseDnField, userBaseDnBrowseButton);
    userBaseDnLayout.setFlexGrow(1, userBaseDnField);

    // Combined layout: Group DN, Operation, User Base DN on single row
    HorizontalLayout topRowLayout = new HorizontalLayout();
    topRowLayout.setWidthFull();
    topRowLayout.setSpacing(true);
    topRowLayout.setAlignItems(Alignment.END);
    topRowLayout.add(groupDnLayout, operationComboBox, userBaseDnLayout);
    topRowLayout.setFlexGrow(1, groupDnLayout);
    topRowLayout.setFlexGrow(1, userBaseDnLayout);

    // Options layout
    HorizontalLayout optionsLayout = new HorizontalLayout();
    optionsLayout.setWidthFull();
    optionsLayout.setSpacing(true);
    optionsLayout.add(continueOnErrorCheckbox, permissiveModifyCheckbox);

    // Action layout
    HorizontalLayout actionLayout = new HorizontalLayout();
    actionLayout.setDefaultVerticalComponentAlignment(Alignment.END);
    actionLayout.setSpacing(true);
    actionLayout.add(operationModeCombo, runButton);

    contentLayout.add(
        new H4("Bulk Group Membership Operations"),
        new Span("Add or remove users to/from a group. Users are validated to exist before modification."),
        topRowLayout,
        userListArea,
        fileUpload,
        optionsLayout,
        actionLayout,
        progressContainer,
        downloadLink);

    add(contentLayout);
    setFlexGrow(1, contentLayout);
  }

  private void performBulkGroupOperation() {
    if (serverConfigs == null || serverConfigs.isEmpty()) {
      NotificationHelper.showError("Please select at least one LDAP server");
      return;
    }

    String groupDn = groupDnField.getValue();
    if (groupDn == null || groupDn.trim().isEmpty()) {
      NotificationHelper.showError("Group DN is required");
      return;
    }

    String userList = userListArea.getValue();
    if (userList == null || userList.trim().isEmpty()) {
      NotificationHelper.showError("User list is required");
      return;
    }

    String operation = operationComboBox.getValue();
    String operationMode = operationModeCombo.getValue();
    boolean isAddOperation = "Add Members".equals(operation);

    // Parse user list
    List<String> userIds = Arrays.stream(userList.split("\\n"))
        .map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .toList();

    if (userIds.isEmpty()) {
      NotificationHelper.showError("No valid user IDs found in the list");
      return;
    }

    loggingService.logInfo("BULK_GROUP_MEMBERSHIPS",
        "Starting bulk group membership operation - Mode: " + operationMode + ", Servers: " 
            + serverConfigs.size() + ", Group: " + groupDn + ", Operation: " + operation 
            + ", Users: " + userIds.size());

    showProgress();

    // Perform operation asynchronously across all servers
    CompletableFuture.runAsync(() -> {
      int totalSuccesses = 0;
      int totalErrors = 0;
      int totalLdifEntries = 0;
      StringBuilder allErrors = new StringBuilder();
      StringBuilder combinedLdif = new StringBuilder();

      for (LdapServerConfig config : serverConfigs) {
        try {
          if ("Create LDIF".equals(operationMode)) {
            String ldif = generateGroupMembershipLdif(config, groupDn, userIds, isAddOperation);
            if (ldif != null && !ldif.isEmpty()) {
              if (combinedLdif.length() > 0) {
                combinedLdif.append("\n\n");
              }
              combinedLdif.append(ldif);
              totalLdifEntries++;
            }
          } else {
            int[] results = processBulkGroupMembership(config, groupDn, userIds, isAddOperation);
            totalSuccesses += results[0];
            totalErrors += results[1];
            if (results[2] > 0) { // errors collected
              allErrors.append("\\nServer: ").append(config.getName()).append("\\n");
            }
          }
        } catch (Exception e) {
          totalErrors++;
          allErrors.append("\\nServer: ").append(config.getName())
              .append(" - Error: ").append(e.getMessage()).append("\\n");
          loggingService.logError("BULK_GROUP_MEMBERSHIPS",
              "Failed on server " + config.getName() + ": " + e.getMessage());
        }
      }

      final int finalSuccesses = totalSuccesses;
      final int finalErrors = totalErrors;
      final int finalLdifEntries = totalLdifEntries;
      final String errorReport = allErrors.toString();
      final String finalLdif = combinedLdif.toString();

      getUI().ifPresent(ui -> ui.access(() -> {
        hideProgress();
        if ("Create LDIF".equals(operationMode)) {
          if (finalLdifEntries > 0) {
            createDownloadLink(finalLdif, "group-memberships.ldif");
            NotificationHelper.showSuccess("LDIF generated successfully for " + finalLdifEntries + " server(s)");
            loggingService.logInfo("BULK_GROUP_MEMBERSHIPS", "LDIF generated for " + finalLdifEntries + " server(s)");
          } else {
            NotificationHelper.showError("Failed to generate LDIF");
          }
        } else {
          if (finalErrors > 0) {
            NotificationHelper.showError("Completed with " + finalSuccesses + " successes and " + 
                finalErrors + " errors across " + serverConfigs.size() + " server(s)");
            if (!errorReport.isEmpty()) {
              NotificationHelper.showInfo("Error details logged");
            }
          } else {
            NotificationHelper.showSuccess("Successfully processed " + finalSuccesses + 
                " operations across " + serverConfigs.size() + " server(s)");
          }
        }
      }));
    });
  }

  /**
   * Processes bulk group membership operation for a single server.
   * Returns array: [successCount, errorCount, errorListSize]
   */
  private int[] processBulkGroupMembership(LdapServerConfig serverConfig, String groupDn, 
      List<String> userIds, boolean isAddOperation) throws LDAPException {
    String userBaseDn = userBaseDnField.getValue();

    // Use server base DN if not specified
    if (userBaseDn == null || userBaseDn.trim().isEmpty()) {
      userBaseDn = serverConfig.getBaseDn();
    }

    List<String> errors = new ArrayList<>();
    int successCount = 0;
    int errorCount = 0;

    try {
      // Step 1: Validate all users exist and get their exact UIDs and DNs
      getUI().ifPresent(ui -> ui.access(() -> progressContainer.getChildren().forEach(component -> {
        if (component instanceof Span) {
          Span span = (Span) component;
          if (span.getText().startsWith("Processing")) {
            span.setText("Validating users...");
          }
        }
      })));

      List<UserInfo> validUsers = new ArrayList<>();

      for (String userId : userIds) {
        try {
          String userFilter = "(&(|(objectClass=posixAccount)(objectClass=inetOrgPerson))(uid="
              + escapeFilterValue(userId) + "))";
          List<LdapEntry> users = ldapService.search(serverConfig, userBaseDn, userFilter,
              com.unboundid.ldap.sdk.SearchScope.SUB, "uid", "dn");

          if (users.isEmpty()) {
            errors.add("User not found: " + userId);
            errorCount++;
            if (!continueOnErrorCheckbox.getValue()) {
              break;
            }
          } else if (users.size() > 1) {
            errors.add("Multiple users found for ID: " + userId);
            errorCount++;
            if (!continueOnErrorCheckbox.getValue()) {
              break;
            }
          } else {
            LdapEntry user = users.get(0);
            String actualUid = user.getFirstAttributeValue("uid");
            validUsers.add(new UserInfo(actualUid, user.getDn()));
          }
        } catch (LDAPException e) {
          errors.add("Error validating user " + userId + ": " + e.getMessage());
          errorCount++;
          if (!continueOnErrorCheckbox.getValue()) {
            break;
          }
        }
      }

      if (validUsers.isEmpty()) {
        throw new RuntimeException("No valid users found");
      }

      // Step 2: Retrieve the group entry using the provided DN
      getUI().ifPresent(ui -> ui.access(() -> progressContainer.getChildren().forEach(component -> {
        if (component instanceof Span) {
          Span span = (Span) component;
          if (span.getText().startsWith("Validating") || span.getText().startsWith("Processing")) {
            span.setText("Retrieving group entry...");
          }
        }
      })));

      // Retrieve the group entry directly using the DN
      LdapEntry group = ldapService.readEntry(serverConfig, groupDn, false);
      
      if (group == null) {
        throw new RuntimeException("Group not found at DN: " + groupDn);
      }

      List<String> objectClasses = group.getAttributeValues("objectClass");
      GroupType groupType = determineGroupType(objectClasses);

      // Step 3: Process based on group type
      getUI().ifPresent(ui -> ui.access(() -> progressContainer.getChildren().forEach(component -> {
        if (component instanceof Span) {
          Span span = (Span) component;
          if (span.getText().startsWith("Validating")) {
            span.setText("Processing " + groupType + " membership operations...");
          }
        }
      })));

      switch (groupType) {
        case POSIX_GROUP:
          successCount += processPosixGroup(serverConfig, groupDn, validUsers, isAddOperation, errors);
          break;
        case GROUP_OF_NAMES:
          successCount += processGroupOfNames(serverConfig, groupDn, validUsers, isAddOperation, errors);
          break;
        case GROUP_OF_UNIQUE_NAMES:
          successCount += processGroupOfUniqueNames(serverConfig, groupDn, validUsers, isAddOperation, errors);
          break;
        case GROUP_OF_URLS:
          successCount += processGroupOfUrls(serverConfig, group, validUsers, isAddOperation, errors);
          break;
        default:
          throw new RuntimeException("Unsupported group type: " + groupType);
      }

    } catch (Exception e) {
      errors.add("Fatal error: " + e.getMessage());
      errorCount++;
      loggingService.logError("BULK_GROUP_MEMBERSHIPS",
          "Fatal error on server " + serverConfig.getName() + ": " + e.getMessage());
    }

    // Log results for this server
    if (errorCount > 0) {
      loggingService.logWarning("BULK_GROUP_MEMBERSHIPS",
          "Server " + serverConfig.getName() + " - Successes: " + successCount + ", Errors: " + errorCount);
    } else {
      loggingService.logInfo("BULK_GROUP_MEMBERSHIPS",
          "Server " + serverConfig.getName() + " - Successfully processed " + successCount + " operations");
    }

    // Return results: [successCount, errorCount, errorListSize]
    return new int[] {successCount, errorCount, errors.size()};
  }

  private int processPosixGroup(LdapServerConfig serverConfig, String groupDn, List<UserInfo> users, 
      boolean isAddOperation, List<String> errors) {
    int successCount = 0;

    try {
      List<Modification> modifications = new ArrayList<>();

      for (UserInfo user : users) {
        if (isAddOperation) {
          modifications.add(new Modification(ModificationType.ADD, "memberUid", user.uid));
        } else {
          modifications.add(new Modification(ModificationType.DELETE, "memberUid", user.uid));
        }
      }

      if (!modifications.isEmpty()) {
        List<Control> controls = new ArrayList<>();
        if (permissiveModifyCheckbox.getValue()) {
          controls.add(new PermissiveModifyRequestControl());
        }

        ldapService.modifyEntry(serverConfig, groupDn, modifications, 
            controls.isEmpty() ? null : controls.toArray(new Control[0]));
        successCount = users.size();
      }

    } catch (Exception e) {
      errors.add("Failed to modify posixGroup: " + e.getMessage());
    }

    return successCount;
  }

  private int processGroupOfNames(LdapServerConfig serverConfig, String groupDn, List<UserInfo> users, 
      boolean isAddOperation, List<String> errors) {
    int successCount = 0;

    try {
      List<Modification> modifications = new ArrayList<>();

      for (UserInfo user : users) {
        if (isAddOperation) {
          modifications.add(new Modification(ModificationType.ADD, "member", user.dn));
        } else {
          modifications.add(new Modification(ModificationType.DELETE, "member", user.dn));
        }
      }

      if (!modifications.isEmpty()) {
        List<Control> controls = new ArrayList<>();
        if (permissiveModifyCheckbox.getValue()) {
          controls.add(new PermissiveModifyRequestControl());
        }

        ldapService.modifyEntry(serverConfig, groupDn, modifications, 
            controls.isEmpty() ? null : controls.toArray(new Control[0]));
        successCount = users.size();
      }

    } catch (Exception e) {
      errors.add("Failed to modify groupOfNames: " + e.getMessage());
    }

    return successCount;
  }

  private int processGroupOfUniqueNames(LdapServerConfig serverConfig, String groupDn, List<UserInfo> users, 
      boolean isAddOperation, List<String> errors) {
    int successCount = 0;

    try {
      List<Modification> modifications = new ArrayList<>();

      for (UserInfo user : users) {
        if (isAddOperation) {
          modifications.add(new Modification(ModificationType.ADD, "uniqueMember", user.dn));
        } else {
          modifications.add(new Modification(ModificationType.DELETE, "uniqueMember", user.dn));
        }
      }

      if (!modifications.isEmpty()) {
        List<Control> controls = new ArrayList<>();
        if (permissiveModifyCheckbox.getValue()) {
          controls.add(new PermissiveModifyRequestControl());
        }

        ldapService.modifyEntry(serverConfig, groupDn, modifications, 
            controls.isEmpty() ? null : controls.toArray(new Control[0]));
        successCount = users.size();
      }

    } catch (Exception e) {
      errors.add("Failed to modify groupOfUniqueNames: " + e.getMessage());
    }

    return successCount;
  }

  private int processGroupOfUrls(LdapServerConfig serverConfig, LdapEntry group, List<UserInfo> users, 
      boolean isAddOperation, List<String> errors) {
    int successCount = 0;

    try {
      List<String> memberUrls = group.getAttributeValues("memberURL");

      if (memberUrls.isEmpty()) {
        errors.add("groupOfUrls has no memberURL attribute");
        return 0;
      }

      if (memberUrls.size() > 1) {
        errors.add("groupOfUrls has multiple memberURL values - only single memberURL is supported");
        return 0;
      }

      String memberUrlStr = memberUrls.get(0);
      LDAPURL memberUrl = new LDAPURL(memberUrlStr);
      String filter = memberUrl.getFilter().toString();

      // Parse the filter to extract attribute-value pairs
      List<AttributeValuePair> attributePairs = parseFilterForAttributes(filter);

      if (attributePairs.isEmpty()) {
        errors.add("No modifiable attributes found in memberURL filter: " + filter);
        return 0;
      }

      // Validate constraints
      for (AttributeValuePair pair : attributePairs) {
        if ("uid".equalsIgnoreCase(pair.attribute) || "cn".equalsIgnoreCase(pair.attribute)) {
          errors.add("memberURL filter contains uid or cn attribute which is not allowed: " + pair.attribute);
          return 0;
        }
        if (pair.value.contains("*")) {
          errors.add("memberURL filter contains wildcard values which is not allowed: " + pair.value);
          return 0;
        }
      }

      // Process each user
      for (UserInfo user : users) {
        try {
          List<Modification> modifications = new ArrayList<>();

          for (AttributeValuePair pair : attributePairs) {
            if (isAddOperation) {
              modifications.add(new Modification(ModificationType.ADD, pair.attribute, pair.value));
            } else {
              // When removing, don't remove objectClass values
              if (!pair.attribute.equalsIgnoreCase("objectClass")) {
                modifications.add(new Modification(ModificationType.DELETE, pair.attribute, pair.value));
              }
            }
          }

          if (!modifications.isEmpty()) {
            List<Control> controls = new ArrayList<>();
            if (permissiveModifyCheckbox.getValue()) {
              controls.add(new PermissiveModifyRequestControl());
            }

            try {
              ldapService.modifyEntry(serverConfig, user.dn, modifications, 
                  controls.isEmpty() ? null : controls.toArray(new Control[0]));
              successCount++;
            } catch (com.unboundid.ldap.sdk.LDAPException e) {
              // Check if this is an "attribute value already exists" or "no such attribute" error
              if (e.getResultCode() == com.unboundid.ldap.sdk.ResultCode.ATTRIBUTE_OR_VALUE_EXISTS
                  || e.getResultCode() == com.unboundid.ldap.sdk.ResultCode.NO_SUCH_ATTRIBUTE) {
                // User already has the attributes or doesn't have attributes to remove - log info but continue
                loggingService.logInfo("BULK_GROUP_MEMBERSHIPS",
                    "User " + user.uid + " skipped - " + (isAddOperation 
                        ? "already has required attributes" 
                        : "does not have attributes to remove"));
                successCount++; // Count as success since permissive modify would have succeeded
              } else {
                throw e; // Re-throw other LDAP errors
              }
            }
          }

        } catch (Exception e) {
          errors.add("Failed to modify user " + user.uid + " for dynamic group: " + e.getMessage());
          if (!continueOnErrorCheckbox.getValue()) {
            break;
          }
        }
      }

    } catch (Exception e) {
      errors.add("Failed to process groupOfUrls: " + e.getMessage());
    }

    return successCount;
  }

  private List<AttributeValuePair> parseFilterForAttributes(String filter) {
    List<AttributeValuePair> pairs = new ArrayList<>();

    // Remove outer parentheses and & operator if present
    String cleanFilter = filter.trim();
    if (cleanFilter.startsWith("(&") && cleanFilter.endsWith(")")) {
      cleanFilter = cleanFilter.substring(2, cleanFilter.length() - 1);
    } else if (cleanFilter.startsWith("(") && cleanFilter.endsWith(")")) {
      cleanFilter = cleanFilter.substring(1, cleanFilter.length() - 1);
    }

    // Parse individual attribute=value pairs
    // Pattern to match (attribute=value) constructs
    Pattern pattern = Pattern.compile("\\(([^=]+)=([^)]+)\\)");
    Matcher matcher = pattern.matcher(cleanFilter);

    while (matcher.find()) {
      String attribute = matcher.group(1).trim();
      String value = matcher.group(2).trim();

      // Skip objectClass=* patterns or other wildcards
      if (!value.contains("*") && !attribute.equalsIgnoreCase("uid") && !attribute.equalsIgnoreCase("cn")) {
        pairs.add(new AttributeValuePair(attribute, value));
      }
    }

    return pairs;
  }

  private GroupType determineGroupType(List<String> objectClasses) {
    Set<String> ocSet = new HashSet<>();
    for (String oc : objectClasses) {
      ocSet.add(oc.toLowerCase());
    }

    if (ocSet.contains("groupofurls")) {
      return GroupType.GROUP_OF_URLS;
    } else if (ocSet.contains("posixgroup")) {
      return GroupType.POSIX_GROUP;
    } else if (ocSet.contains("groupofnames")) {
      return GroupType.GROUP_OF_NAMES;
    } else if (ocSet.contains("groupofuniquenames")) {
      return GroupType.GROUP_OF_UNIQUE_NAMES;
    } else {
      return GroupType.UNKNOWN;
    }
  }

  private String escapeFilterValue(String value) {
    if (value == null)
      return null;

    return value.replace("\\", "\\5c")
        .replace("*", "\\2a")
        .replace("(", "\\28")
        .replace(")", "\\29")
        .replace("\\0", "\\00");
  }

  private void showProgress() {
    progressContainer.setVisible(true);
    runButton.setEnabled(false);
    downloadLink.setVisible(false);
  }

  private void hideProgress() {
    progressContainer.setVisible(false);
    runButton.setEnabled(true);
  }

  /**
   * Sets the server configurations for multi-server support.
   *
   * @param serverConfigs the list of LDAP server configurations
   */
  public void setServerConfigs(List<LdapServerConfig> serverConfigs) {
    this.serverConfigs = serverConfigs != null ? serverConfigs : new ArrayList<>();
  }

  /**
   * Shows the DN browser dialog for selecting a DN.
   *
   * @param targetField the text field to populate with the selected DN
   */
  private void showDnBrowserDialog(TextField targetField) {
    if (serverConfigs == null || serverConfigs.isEmpty()) {
      NotificationHelper.showError("Please select at least one LDAP server");
      return;
    }

    new DnBrowserDialog(ldapService, truststoreService)
        .withServerConfigs(serverConfigs)
        .onDnSelected(dn -> targetField.setValue(dn))
        .open();
  }

  private String generateGroupMembershipLdif(LdapServerConfig serverConfig, String groupDn,
      List<String> userIds, boolean isAddOperation) throws LDAPException {
    String userBaseDn = userBaseDnField.getValue();

    // Use server base DN if not specified
    if (userBaseDn == null || userBaseDn.trim().isEmpty()) {
      userBaseDn = serverConfig.getBaseDn();
    }

    try {
      // Retrieve the group entry directly using the DN
      LdapEntry group = ldapService.readEntry(serverConfig, groupDn, false);
      
      if (group == null) {
        throw new RuntimeException("Group not found at DN: " + groupDn);
      }

      List<String> objectClasses = group.getAttributeValues("objectClass");
      GroupType groupType = determineGroupType(objectClasses);

      // Generate LDIF based on group type
      StringBuilder ldif = new StringBuilder();
      ldif.append("# Generated group membership LDIF for server: ").append(serverConfig.getName()).append("\n");
      ldif.append("# Group DN: ").append(groupDn).append("\n");
      ldif.append("# Operation: ").append(isAddOperation ? "Add Members" : "Remove Members").append("\n\n");

      String changeType = isAddOperation ? "add" : "delete";

      // Handle dynamic groups (groupOfUrls) differently
      if (groupType == GroupType.GROUP_OF_URLS) {
        // Parse memberURL to extract attributes to modify
        List<String> memberUrls = group.getAttributeValues("memberURL");
        if (memberUrls.isEmpty()) {
          throw new RuntimeException("groupOfUrls has no memberURL attribute");
        }
        if (memberUrls.size() > 1) {
          throw new RuntimeException("groupOfUrls has multiple memberURL values - only single memberURL is supported");
        }

        String memberUrlStr = memberUrls.get(0);
        LDAPURL memberUrl = new LDAPURL(memberUrlStr);
        String filter = memberUrl.getFilter().toString();

        // Parse the filter to extract attribute-value pairs
        List<AttributeValuePair> attributePairs = parseFilterForAttributes(filter);

        if (attributePairs.isEmpty()) {
          throw new RuntimeException("No modifiable attributes found in memberURL filter: " + filter);
        }

        // Validate constraints
        for (AttributeValuePair pair : attributePairs) {
          if ("uid".equalsIgnoreCase(pair.attribute) || "cn".equalsIgnoreCase(pair.attribute)) {
            throw new RuntimeException("memberURL filter contains uid or cn attribute which is not allowed: " + pair.attribute);
          }
          if (pair.value.contains("*")) {
            throw new RuntimeException("memberURL filter contains wildcard values which is not allowed: " + pair.value);
          }
        }

        // Generate LDIF for each user entry modification
        for (int i = 0; i < userIds.size(); i++) {
          String userId = userIds.get(i);
          String userDn = "uid=" + userId + "," + userBaseDn;

          if (i > 0) {
            ldif.append("\n");
          }

          ldif.append("dn: ").append(userDn).append("\n");
          ldif.append("changetype: modify\n");

          for (int j = 0; j < attributePairs.size(); j++) {
            AttributeValuePair pair = attributePairs.get(j);
            // Skip objectClass modifications when removing
            if (!isAddOperation && pair.attribute.equalsIgnoreCase("objectClass")) {
              continue;
            }

            if (j > 0) {
              ldif.append("-\n");
            }
            ldif.append(changeType).append(": ").append(pair.attribute).append("\n");
            ldif.append(pair.attribute).append(": ").append(pair.value).append("\n");
          }
        }
      } else {
        // Handle static groups (posixGroup, groupOfNames, groupOfUniqueNames)
        ldif.append("dn: ").append(groupDn).append("\n");
        ldif.append("changetype: modify\n");

        String attributeName;

        switch (groupType) {
          case POSIX_GROUP:
            attributeName = "memberUid";
            break;
          case GROUP_OF_NAMES:
            attributeName = "member";
            break;
          case GROUP_OF_UNIQUE_NAMES:
            attributeName = "uniqueMember";
            break;
          default:
            throw new RuntimeException("Unsupported group type for LDIF generation: " + groupType);
        }

        for (int i = 0; i < userIds.size(); i++) {
          String userId = userIds.get(i);
          if (i > 0) {
            ldif.append("-\n");
          }
          ldif.append(changeType).append(": ").append(attributeName).append("\n");

          if (groupType == GroupType.POSIX_GROUP) {
            ldif.append(attributeName).append(": ").append(userId).append("\n");
          } else {
            // For GROUP_OF_NAMES and GROUP_OF_UNIQUE_NAMES, we need DNs
            // Generate DN based on user base DN
            String userDn = "uid=" + userId + "," + userBaseDn;
            ldif.append(attributeName).append(": ").append(userDn).append("\n");
          }
        }
      }

      return ldif.toString();

    } catch (Exception e) {
      loggingService.logError("BULK_GROUP_MEMBERSHIPS",
          "Failed to generate LDIF on server " + serverConfig.getName() + ": " + e.getMessage());
      throw new LDAPException(com.unboundid.ldap.sdk.ResultCode.LOCAL_ERROR,
          "Failed to generate LDIF: " + e.getMessage());
    }
  }

  private void createDownloadLink(String content, String fileName) {
    StreamResource resource = new StreamResource(fileName,
        (outputStream, vaadinSession) -> {
          try {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
          } catch (Exception e) {
            loggingService.logError("BulkGroupMemberships", "Failed to write download content: " + e.getMessage());
          }
        });

    downloadLink.setHref(resource);
    downloadLink.setVisible(true);
  }

  public void clear() {
    groupDnField.clear();
    userBaseDnField.clear();
    userListArea.clear();
    operationComboBox.setValue("Add Members");
    operationModeCombo.setValue("Execute Change");
    continueOnErrorCheckbox.setValue(true);
    permissiveModifyCheckbox.setValue(true);
    downloadLink.setVisible(false);
    hideProgress();
  }

  // Helper classes
  private enum GroupType {
    POSIX_GROUP, GROUP_OF_NAMES, GROUP_OF_UNIQUE_NAMES, GROUP_OF_URLS, UNKNOWN
  }

  private static class UserInfo {
    final String uid;
    final String dn;

    UserInfo(String uid, String dn) {
      this.uid = uid;
      this.dn = dn;
    }
  }

  private static class AttributeValuePair {
    final String attribute;
    final String value;

    AttributeValuePair(String attribute, String value) {
      this.attribute = attribute;
      this.value = value;
    }
  }
}
