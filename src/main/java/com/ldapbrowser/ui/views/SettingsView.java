package com.ldapbrowser.ui.views;

import com.ldapbrowser.service.TruststoreService;
import com.ldapbrowser.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.List;

/**
 * Settings view for managing application configuration.
 * Includes tabs for Truststore, Keystore, and Encryption settings.
 */
@Route(value = "settings", layout = MainLayout.class)
@PageTitle("Settings | LDAP Browser")
public class SettingsView extends VerticalLayout {

  private final TruststoreService truststoreService;
  private Grid<String> certificateGrid;

  /**
   * Creates the Settings view.
   *
   * @param truststoreService truststore service
   */
  public SettingsView(TruststoreService truststoreService) {
    this.truststoreService = truststoreService;

    setSpacing(true);
    setPadding(true);
    setSizeFull();

    H2 title = new H2("Settings");
    add(title);

    TabSheet tabSheet = new TabSheet();
    tabSheet.setSizeFull();

    // Add tabs
    tabSheet.add("Truststore", createTruststoreTab());
    tabSheet.add("Keystore", createKeystoreTab());
    tabSheet.add("Encryption", createEncryptionTab());

    add(tabSheet);
    expand(tabSheet);
  }

  /**
   * Creates the Truststore management tab.
   *
   * @return truststore tab content
   */
  private VerticalLayout createTruststoreTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSpacing(true);
    layout.setPadding(false);
    layout.setSizeFull();

    // Info section
    Span infoText = new Span(
        "Manage trusted certificates for secure TLS connections to LDAP servers. "
        + "Certificates are stored in ~/.ldapbrowser/truststore.pfx");
    infoText.getStyle().set("color", "var(--lumo-secondary-text-color)");

    // Stats display
    Span statsLabel = new Span();
    statsLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");
    updateStatsLabel(statsLabel);

    // Action buttons
    Button addButton = new Button("Add Certificate", event -> openAddCertificateDialog(statsLabel));
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button viewButton = new Button("View Details", event -> viewSelectedCertificate());
    viewButton.setEnabled(false);

    Button deleteButton = new Button("Delete", event -> deleteSelectedCertificate(statsLabel));
    deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
    deleteButton.setEnabled(false);

    Button refreshButton = new Button("Refresh", event -> {
      refreshCertificateGrid();
      updateStatsLabel(statsLabel);
    });

    HorizontalLayout buttonLayout = new HorizontalLayout(
        addButton, viewButton, deleteButton, refreshButton
    );
    buttonLayout.setSpacing(true);
    buttonLayout.setAlignItems(FlexComponent.Alignment.CENTER);

    // Certificate grid
    certificateGrid = new Grid<>();
    certificateGrid.addColumn(alias -> alias).setHeader("Alias").setAutoWidth(true);
    certificateGrid.addColumn(alias -> getCertificateSubject(alias))
        .setHeader("Subject").setAutoWidth(true).setFlexGrow(1);
    certificateGrid.addColumn(alias -> getCertificateIssuer(alias))
        .setHeader("Issuer").setAutoWidth(true).setFlexGrow(1);

    certificateGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
    certificateGrid.addSelectionListener(event -> {
      boolean hasSelection = event.getFirstSelectedItem().isPresent();
      viewButton.setEnabled(hasSelection);
      deleteButton.setEnabled(hasSelection);
    });

    refreshCertificateGrid();

    layout.add(infoText, statsLabel, buttonLayout, certificateGrid);
    layout.expand(certificateGrid);

    return layout;
  }

  /**
   * Updates the statistics label with current truststore info.
   *
   * @param statsLabel label to update
   */
  private void updateStatsLabel(Span statsLabel) {
    try {
      String stats = truststoreService.getTruststoreStats();
      statsLabel.setText(stats);
    } catch (Exception e) {
      statsLabel.setText("Error loading truststore statistics");
    }
  }

  /**
   * Refreshes the certificate grid.
   */
  private void refreshCertificateGrid() {
    try {
      List<String> aliases = truststoreService.listCertificates();
      certificateGrid.setItems(aliases);
    } catch (Exception e) {
      Notification.show("Error loading certificates: " + e.getMessage(),
          3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  /**
   * Gets certificate subject for display.
   *
   * @param alias certificate alias
   * @return subject string or error message
   */
  private String getCertificateSubject(String alias) {
    try {
      Certificate cert = truststoreService.getCertificate(alias);
      if (cert instanceof java.security.cert.X509Certificate x509) {
        return x509.getSubjectX500Principal().getName();
      }
      return "N/A";
    } catch (Exception e) {
      return "Error";
    }
  }

  /**
   * Gets certificate issuer for display.
   *
   * @param alias certificate alias
   * @return issuer string or error message
   */
  private String getCertificateIssuer(String alias) {
    try {
      Certificate cert = truststoreService.getCertificate(alias);
      if (cert instanceof java.security.cert.X509Certificate x509) {
        return x509.getIssuerX500Principal().getName();
      }
      return "N/A";
    } catch (Exception e) {
      return "Error";
    }
  }

  /**
   * Opens dialog to add a new certificate.
   *
   * @param statsLabel stats label to update after adding
   */
  private void openAddCertificateDialog(Span statsLabel) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Add Certificate");
    dialog.setWidth("600px");

    VerticalLayout dialogLayout = new VerticalLayout();
    dialogLayout.setSpacing(true);
    dialogLayout.setPadding(false);

    // Alias field
    TextField aliasField = new TextField("Certificate Alias");
    aliasField.setWidthFull();
    aliasField.setPlaceholder("e.g., myserver-cert");
    aliasField.setRequired(true);

    // Upload component
    MemoryBuffer buffer = new MemoryBuffer();
    Upload upload = new Upload(buffer);
    upload.setAcceptedFileTypes(".cer", ".crt", ".pem", ".der");
    upload.setMaxFiles(1);
    upload.setDropLabel(new Span("Drop certificate file here or click to browse"));

    Span uploadInfo = new Span("Supported formats: CER, CRT, PEM, DER");
    uploadInfo.getStyle().set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");

    // Or paste PEM
    TextArea pemArea = new TextArea("Or Paste Certificate (PEM format)");
    pemArea.setWidthFull();
    pemArea.setHeight("200px");
    pemArea.setPlaceholder("-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----");

    dialogLayout.add(aliasField, upload, uploadInfo, pemArea);

    // Dialog buttons
    Button saveButton = new Button("Add Certificate", event -> {
      String alias = aliasField.getValue();
      if (alias == null || alias.trim().isEmpty()) {
        Notification.show("Please enter a certificate alias", 3000, Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_ERROR);
        return;
      }

      try {
        Certificate cert = null;

        // Try to load from upload first
        if (buffer.getFileData() != null) {
          InputStream inputStream = buffer.getInputStream();
          CertificateFactory cf = CertificateFactory.getInstance("X.509");
          cert = cf.generateCertificate(inputStream);
        } else if (pemArea.getValue() != null && !pemArea.getValue().trim().isEmpty()) {
          // Try to parse PEM text
          String pemText = pemArea.getValue().trim();
          byte[] certBytes = pemText.getBytes();
          ByteArrayInputStream bais = new ByteArrayInputStream(certBytes);
          CertificateFactory cf = CertificateFactory.getInstance("X.509");
          cert = cf.generateCertificate(bais);
        }

        if (cert == null) {
          Notification.show("Please upload a certificate file or paste PEM text",
              3000, Notification.Position.MIDDLE)
              .addThemeVariants(NotificationVariant.LUMO_ERROR);
          return;
        }

        truststoreService.addCertificate(alias, cert);
        refreshCertificateGrid();
        updateStatsLabel(statsLabel);
        dialog.close();
        Notification.show("Certificate added successfully", 3000, Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      } catch (Exception e) {
        Notification.show("Error adding certificate: " + e.getMessage(),
            5000, Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_ERROR);
      }
    });
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", event -> dialog.close());

    dialog.getFooter().add(cancelButton, saveButton);
    dialog.add(dialogLayout);
    dialog.open();
  }

  /**
   * Views details of the selected certificate.
   */
  private void viewSelectedCertificate() {
    certificateGrid.getSelectedItems().stream().findFirst().ifPresent(alias -> {
      try {
        String details = truststoreService.getCertificateDetails(alias);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Certificate Details: " + alias);
        dialog.setWidth("700px");

        TextArea detailsArea = new TextArea();
        detailsArea.setValue(details);
        detailsArea.setWidthFull();
        detailsArea.setHeight("400px");
        detailsArea.setReadOnly(true);

        Button closeButton = new Button("Close", event -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(detailsArea);
        dialog.getFooter().add(closeButton);
        dialog.open();
      } catch (Exception e) {
        Notification.show("Error loading certificate details: " + e.getMessage(),
            3000, Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_ERROR);
      }
    });
  }

  /**
   * Deletes the selected certificate.
   *
   * @param statsLabel stats label to update after deletion
   */
  private void deleteSelectedCertificate(Span statsLabel) {
    certificateGrid.getSelectedItems().stream().findFirst().ifPresent(alias -> {
      try {
        truststoreService.removeCertificate(alias);
        refreshCertificateGrid();
        updateStatsLabel(statsLabel);
        Notification.show("Certificate deleted successfully", 3000, Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      } catch (Exception e) {
        Notification.show("Error deleting certificate: " + e.getMessage(),
            3000, Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_ERROR);
      }
    });
  }

  /**
   * Creates the Keystore management tab (under construction).
   *
   * @return keystore tab content
   */
  private VerticalLayout createKeystoreTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSpacing(true);
    layout.setPadding(false);
    layout.setAlignItems(FlexComponent.Alignment.CENTER);
    layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

    H3 title = new H3("Keystore Management");
    Span message = new Span("Under Construction");
    message.getStyle()
        .set("font-size", "var(--lumo-font-size-xl)")
        .set("color", "var(--lumo-secondary-text-color)");

    layout.add(title, message);
    return layout;
  }

  /**
   * Creates the Encryption settings tab (under construction).
   *
   * @return encryption tab content
   */
  private VerticalLayout createEncryptionTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setSpacing(true);
    layout.setPadding(false);
    layout.setAlignItems(FlexComponent.Alignment.CENTER);
    layout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

    H3 title = new H3("Encryption Settings");
    Span message = new Span("Under Construction");
    message.getStyle()
        .set("font-size", "var(--lumo-font-size-xl)")
        .set("color", "var(--lumo-secondary-text-color)");

    layout.add(title, message);
    return layout;
  }
}
