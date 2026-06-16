package com.ldapbrowser.ui.dialogs;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.ldapbrowser.util.LdapExportFormatter;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.streams.DownloadHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Dialog for exporting Search view results.
 */
public class SearchExportDialog extends Dialog {

  @FunctionalInterface
  public interface SearchRunner {

    List<LdapEntry> run(List<String> returnAttributes) throws Exception;
  }

  private final SearchRunner searchRunner;
  private final TextField returnAttributesField;
  private final Checkbox includeHeaderCheckbox;
  private final Checkbox includeDnCheckbox;
  private final Checkbox surroundValuesCheckbox;
  private final ComboBox<String> outputFormatCombo;
  private final Button exportButton;
  private final Anchor downloadLink;

  /**
   * Creates a new search export dialog.
   *
   * @param initialReturnAttributes initial return attributes
   * @param searchRunner search callback that reruns the current search
   */
  public SearchExportDialog(String initialReturnAttributes,
      SearchRunner searchRunner) {
    this.searchRunner = searchRunner;

    setHeaderTitle("Export Search Results");
    setResizable(true);
    setDraggable(true);
    setWidth("700px");

    returnAttributesField = new TextField("Return Attributes");
    returnAttributesField.setWidthFull();
    returnAttributesField.setPlaceholder(
        "cn,mail,telephoneNumber (leave empty for all)");
    if (initialReturnAttributes != null && !initialReturnAttributes.isBlank()) {
      returnAttributesField.setValue(initialReturnAttributes);
    }

    includeHeaderCheckbox = new Checkbox("Include Header", true);
    includeDnCheckbox = new Checkbox("Include DN", true);
    surroundValuesCheckbox = new Checkbox(
        "Surround Values in Quotes", true);

    outputFormatCombo = new ComboBox<>("Output Format");
    outputFormatCombo.setItems("CSV", "JSON", "LDIF", "DN LIST");
    outputFormatCombo.setValue("CSV");
    outputFormatCombo.setWidth("220px");
    outputFormatCombo.addValueChangeListener(e -> updateCsvCheckboxVisibility());

    exportButton = new Button("Export", new Icon(VaadinIcon.DOWNLOAD));
    exportButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    exportButton.addClickListener(event -> performExport());

    downloadLink = new Anchor();
    downloadLink.setVisible(false);

    Button closeButton = new Button("Close", event -> close());
    closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    HorizontalLayout optionsLayout = new HorizontalLayout(
        includeHeaderCheckbox, includeDnCheckbox, surroundValuesCheckbox);
    optionsLayout.setSpacing(true);

    HorizontalLayout actionsLayout = new HorizontalLayout(
        outputFormatCombo, exportButton, closeButton);
    actionsLayout.setDefaultVerticalComponentAlignment(
        HorizontalLayout.Alignment.END);

    VerticalLayout content = new VerticalLayout(
        returnAttributesField,
        optionsLayout,
        actionsLayout,
        downloadLink);
    content.setPadding(false);
    content.setSpacing(true);
    content.setWidthFull();

    add(content);
  }

  private void updateCsvCheckboxVisibility() {
    boolean isCsv = "CSV".equals(outputFormatCombo.getValue());
    includeHeaderCheckbox.setVisible(isCsv);
    includeDnCheckbox.setVisible(isCsv);
    surroundValuesCheckbox.setVisible(isCsv);
  }

  private void performExport() {
    exportButton.setEnabled(false);

    try {
      List<String> requestedAttributes = LdapExportFormatter.parseReturnAttributes(
          returnAttributesField.getValue());
      List<LdapEntry> entries = searchRunner.run(requestedAttributes);
      String format = outputFormatCombo.getValue();
      String exportData = LdapExportFormatter.generateExportData(entries,
          format,
          requestedAttributes,
          includeHeaderCheckbox.getValue(),
          includeDnCheckbox.getValue(),
          surroundValuesCheckbox.getValue());
      String fileName = LdapExportFormatter.generateFileName(format);

      createDownloadLink(exportData, fileName, format);
      NotificationHelper.showSuccess(
          "Export ready. " + entries.size() + " entries included.");
    } catch (Exception e) {
      NotificationHelper.showError("Export failed: " + e.getMessage());
    } finally {
      exportButton.setEnabled(true);
    }
  }

  private void createDownloadLink(String data, String fileName,
      String format) {
    DownloadHandler handler = event -> {
      event.setFileName(fileName);
      event.setContentType(LdapExportFormatter.getMimeType(format));
      try (java.io.OutputStream out = event.getOutputStream()) {
        out.write(data.getBytes(StandardCharsets.UTF_8));
      }
    };

    downloadLink.setHref(handler);
    downloadLink.setText("Download " + fileName);
    downloadLink.setVisible(true);
  }
}