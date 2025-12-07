package com.ldapbrowser.ui.dialogs;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.TruststoreService;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.function.Consumer;
import javax.security.auth.x500.X500Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dialog for displaying TLS/SSL certificate details.
 * Can show certificates as trusted or untrusted, and provides option to import
 * untrusted certificates.
 */
public class TlsCertificateDialog extends Dialog {

  private static final Logger logger = LoggerFactory.getLogger(TlsCertificateDialog.class);

  private final TruststoreService truststoreService;
  private final X509Certificate certificate;
  private final LdapServerConfig serverConfig;
  private final boolean isTrusted;
  private final Consumer<Boolean> onComplete;

  /**
   * Creates a TLS certificate dialog.
   *
   * @param certificate the server certificate to display
   * @param serverConfig the LDAP server configuration
   * @param truststoreService service for managing truststore
   * @param onComplete callback when dialog is closed (true if certificate was imported)
   * @deprecated Use the constructor with isTrusted parameter
   */
  @Deprecated
  public TlsCertificateDialog(X509Certificate certificate, LdapServerConfig serverConfig,
      TruststoreService truststoreService, Consumer<Boolean> onComplete) {
    this(certificate, serverConfig, truststoreService, false, onComplete);
  }

  /**
   * Creates a TLS certificate dialog.
   *
   * @param certificate the server certificate to display
   * @param serverConfig the LDAP server configuration
   * @param truststoreService service for managing truststore
   * @param isTrusted whether the certificate is already trusted
   * @param onComplete callback when dialog is closed (true if certificate was imported)
   */
  public TlsCertificateDialog(X509Certificate certificate, LdapServerConfig serverConfig,
      TruststoreService truststoreService, boolean isTrusted, Consumer<Boolean> onComplete) {
    this.certificate = certificate;
    this.serverConfig = serverConfig;
    this.truststoreService = truststoreService;
    this.isTrusted = isTrusted;
    this.onComplete = onComplete;

    initDialog();
  }

  /**
   * Initializes the dialog UI.
   */
  private void initDialog() {
    setHeaderTitle("Server Certificate");
    setWidth("700px");
    setCloseOnOutsideClick(false);
    setCloseOnEsc(false);

    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(false);
    layout.setSpacing(true);

    // Trust status header
    if (isTrusted) {
      H3 statusTitle = new H3("✓ Trusted Certificate");
      statusTitle.getStyle().set("color", "var(--lumo-success-color)");
      layout.add(statusTitle);

      TextArea statusText = new TextArea();
      statusText.setWidthFull();
      statusText.setReadOnly(true);
      statusText.setValue(
          "The server '" + serverConfig.getName() + "' presented a certificate that is trusted.\n\n"
          + "This certificate or its issuing certificate authority is already trusted by your system."
      );
      statusText.getStyle()
          .set("background", "var(--lumo-success-color-10pct)")
          .set("border", "1px solid var(--lumo-success-color-50pct)");
      layout.add(statusText);
    } else {
      H3 warningTitle = new H3("⚠️ Untrusted Certificate");
      warningTitle.getStyle().set("color", "var(--lumo-error-color)");
      layout.add(warningTitle);

      TextArea warningText = new TextArea();
      warningText.setWidthFull();
      warningText.setReadOnly(true);
      warningText.setValue(
          "The server '" + serverConfig.getName() + "' presented a certificate that is not trusted.\n\n"
          + "This may be expected for self-signed certificates or internal CAs. Review the "
          + "certificate details below and import it if you trust this server."
      );
      warningText.getStyle()
          .set("background", "var(--lumo-error-color-10pct)")
          .set("border", "1px solid var(--lumo-error-color-50pct)");
      layout.add(warningText);
    }

    layout.add(new Hr());

    // Certificate details
    H3 detailsTitle = new H3("Certificate Details");
    layout.add(detailsTitle);

    TextArea certDetails = new TextArea();
    certDetails.setWidthFull();
    certDetails.setHeight("300px");
    certDetails.setReadOnly(true);
    certDetails.setValue(formatCertificateDetails());
    layout.add(certDetails);

    // Buttons
    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setWidthFull();
    buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    buttonLayout.setSpacing(true);

    Button closeButton = new Button("Close", e -> {
      onComplete.accept(false);
      close();
    });

    if (isTrusted) {
      // For trusted certificates, only show close button
      closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      buttonLayout.add(closeButton);
    } else {
      // For untrusted certificates, show cancel and import buttons
      Button importButton = new Button("Import and Trust", e -> importCertificate());
      importButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      buttonLayout.add(closeButton, importButton);
    }

    layout.add(buttonLayout);
    add(layout);
  }

  /**
   * Formats certificate details for display.
   *
   * @return formatted certificate information
   */
  private String formatCertificateDetails() {
    StringBuilder details = new StringBuilder();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    // Subject
    details.append("Subject:\n");
    details.append("  ").append(formatDn(certificate.getSubjectX500Principal())).append("\n\n");

    // Issuer
    details.append("Issuer:\n");
    details.append("  ").append(formatDn(certificate.getIssuerX500Principal())).append("\n\n");

    // Validity
    details.append("Validity:\n");
    details.append("  Valid From: ").append(dateFormat.format(certificate.getNotBefore()))
        .append("\n");
    details.append("  Valid Until: ").append(dateFormat.format(certificate.getNotAfter()))
        .append("\n\n");

    // Serial Number
    details.append("Serial Number:\n");
    details.append("  ").append(certificate.getSerialNumber().toString(16).toUpperCase())
        .append("\n\n");

    // Extended Key Usage
    try {
      java.util.List<String> extendedKeyUsage = certificate.getExtendedKeyUsage();
      if (extendedKeyUsage != null && !extendedKeyUsage.isEmpty()) {
        details.append("Extended Key Usage:\n");
        for (String oid : extendedKeyUsage) {
          details.append("  ").append(formatKeyUsageOid(oid)).append("\n");
        }
        details.append("\n");
      }
    } catch (java.security.cert.CertificateParsingException e) {
      details.append("Extended Key Usage: Error parsing\n\n");
    }

    // Subject Alternative Names
    try {
      java.util.Collection<java.util.List<?>> subjectAltNames = 
          certificate.getSubjectAlternativeNames();
      if (subjectAltNames != null && !subjectAltNames.isEmpty()) {
        details.append("Subject Alternative Names:\n");
        for (java.util.List<?> san : subjectAltNames) {
          if (san.size() >= 2) {
            Integer type = (Integer) san.get(0);
            Object value = san.get(1);
            details.append("  ").append(formatSanType(type)).append(": ")
                .append(value).append("\n");
          }
        }
        details.append("\n");
      }
    } catch (java.security.cert.CertificateParsingException e) {
      details.append("Subject Alternative Names: Error parsing\n\n");
    }

    // Fingerprints
    try {
      details.append("SHA-256 Fingerprint:\n");
      details.append("  ").append(getFingerprint(certificate, "SHA-256")).append("\n\n");

      details.append("SHA-1 Fingerprint:\n");
      details.append("  ").append(getFingerprint(certificate, "SHA-1")).append("\n");
    } catch (NoSuchAlgorithmException | CertificateException e) {
      details.append("Error calculating fingerprints: ").append(e.getMessage()).append("\n");
    }

    return details.toString();
  }

  /**
   * Formats a distinguished name for readable display.
   *
   * @param principal the X500Principal to format
   * @return formatted DN string
   */
  private String formatDn(X500Principal principal) {
    String dn = principal.getName();
    // Split by comma and format each component on a new line
    return dn.replace(", ", "\n  ");
  }

  /**
   * Calculates certificate fingerprint.
   *
   * @param cert the certificate
   * @param algorithm hash algorithm (SHA-1, SHA-256)
   * @return formatted fingerprint string
   * @throws NoSuchAlgorithmException if algorithm not available
   * @throws CertificateException if certificate encoding fails
   */
  private String getFingerprint(X509Certificate cert, String algorithm)
      throws NoSuchAlgorithmException, CertificateException {
    MessageDigest md = MessageDigest.getInstance(algorithm);
    byte[] encoded = cert.getEncoded();
    byte[] digest = md.digest(encoded);

    StringBuilder fingerprint = new StringBuilder();
    for (int i = 0; i < digest.length; i++) {
      if (i > 0) {
        fingerprint.append(":");
      }
      fingerprint.append(String.format("%02X", digest[i] & 0xFF));
    }
    return fingerprint.toString();
  }

  /**
   * Imports the certificate into the truststore.
   */
  private void importCertificate() {
    try {
      // Generate alias from server name and certificate common name
      String alias = generateCertificateAlias();

      // Add certificate to truststore
      truststoreService.addCertificate(alias, certificate);

      logger.info("Imported certificate for {} with alias: {}", serverConfig.getName(), alias);

      // Show success message
      NotificationHelper.showSuccess("Certificate imported successfully as: " + alias);

      onComplete.accept(true);
      close();
    } catch (Exception e) {
      logger.error("Failed to import certificate", e);
      NotificationHelper.showError("Failed to import certificate: " + e.getMessage());
    }
  }

  /**
   * Generates a unique alias for the certificate based on server name.
   *
   * @return certificate alias
   */
  private String generateCertificateAlias() throws Exception {
    String baseName = serverConfig.getName().replaceAll("[^a-zA-Z0-9-]", "_");
    String alias = baseName;
    int counter = 1;

    // Ensure alias is unique
    while (truststoreService.getCertificate(alias) != null) {
      alias = baseName + "_" + counter;
      counter++;
    }

    return alias;
  }

  /**
   * Formats a Key Usage OID to a readable name.
   *
   * @param oid the OID string
   * @return formatted key usage name
   */
  private String formatKeyUsageOid(String oid) {
    return switch (oid) {
      case "1.3.6.1.5.5.7.3.1" -> "TLS Web Server Authentication (" + oid + ")";
      case "1.3.6.1.5.5.7.3.2" -> "TLS Web Client Authentication (" + oid + ")";
      case "1.3.6.1.5.5.7.3.3" -> "Code Signing (" + oid + ")";
      case "1.3.6.1.5.5.7.3.4" -> "Email Protection (" + oid + ")";
      case "1.3.6.1.5.5.7.3.8" -> "Time Stamping (" + oid + ")";
      case "1.3.6.1.5.5.7.3.9" -> "OCSP Signing (" + oid + ")";
      default -> oid;
    };
  }

  /**
   * Formats a Subject Alternative Name type to a readable name.
   *
   * @param type the SAN type integer
   * @return formatted SAN type name
   */
  private String formatSanType(Integer type) {
    return switch (type) {
      case 0 -> "Other Name";
      case 1 -> "RFC822 Name";
      case 2 -> "DNS Name";
      case 3 -> "X400 Address";
      case 4 -> "Directory Name";
      case 5 -> "EDI Party Name";
      case 6 -> "URI";
      case 7 -> "IP Address";
      case 8 -> "Registered ID";
      default -> "Unknown (" + type + ")";
    };
  }

  /**
   * Creates a TLS certificate dialog from PEM-encoded certificate string.
   *
   * @param pemCert PEM-encoded certificate
   * @param serverConfig server configuration
   * @param truststoreService truststore service
   * @param onComplete callback when dialog closes
   * @return TlsCertificateDialog instance
   * @throws CertificateException if certificate parsing fails
   */
  public static TlsCertificateDialog fromPemString(String pemCert, LdapServerConfig serverConfig,
      TruststoreService truststoreService, Consumer<Boolean> onComplete)
      throws CertificateException {
    
    // Remove PEM headers/footers and decode
    String certContent = pemCert
        .replace("-----BEGIN CERTIFICATE-----", "")
        .replace("-----END CERTIFICATE-----", "")
        .replaceAll("\\s", "");

    byte[] decoded = Base64.getDecoder().decode(certContent);

    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    X509Certificate certificate = (X509Certificate) cf.generateCertificate(
        new ByteArrayInputStream(decoded)
    );

    return new TlsCertificateDialog(certificate, serverConfig, truststoreService, onComplete);
  }
}
