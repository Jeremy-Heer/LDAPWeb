package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LogEntry;
import com.ldapbrowser.model.LogEntry.LogLevel;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.service.LoggingService.LogUpdateListener;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Component that displays recent activity logs with filtering and color-coding.
 * Implements LogUpdateListener for real-time log updates.
 */
public class LogsDrawer extends VerticalLayout implements LogUpdateListener {
  
  private static final DateTimeFormatter TIME_FORMATTER = 
      DateTimeFormatter.ofPattern("MMM dd HH:mm:ss");
  
  private final LoggingService loggingService;
  private final Div logsContainer;
  private final TextField searchField;
  private final CheckboxGroup<LogLevel> levelFilter;
  private List<LogEntry> allLogs;
  
  /**
   * Constructor.
   *
   * @param loggingService the logging service
   */
  public LogsDrawer(LoggingService loggingService) {
    this.loggingService = loggingService;
    
    // Let the dialog handle sizing and scrolling
    setWidthFull();
    setHeightFull();
    setPadding(false);
    setSpacing(false);
    
    // Header with action buttons
    Button refreshButton = new Button(new Icon(VaadinIcon.REFRESH));
    refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    refreshButton.addClickListener(e -> refresh());
    refreshButton.setTooltipText("Refresh logs");
    
    Button clearButton = new Button(new Icon(VaadinIcon.TRASH));
    clearButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    clearButton.addClickListener(e -> clearLogs());
    clearButton.setTooltipText("Clear all logs");
    
    HorizontalLayout header = new HorizontalLayout(refreshButton, clearButton);
    header.setJustifyContentMode(JustifyContentMode.END);
    header.setAlignItems(Alignment.CENTER);
    header.setPadding(false);
    header.getStyle().set("margin-bottom", "var(--lumo-space-s)");
    
    // Search field
    searchField = new TextField();
    searchField.setPlaceholder("Search logs...");
    searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
    searchField.setWidthFull();
    searchField.setValueChangeMode(ValueChangeMode.EAGER);
    searchField.addValueChangeListener(e -> filterLogs());
    searchField.getStyle().set("margin-bottom", "var(--lumo-space-s)");
    
    // Level filter
    levelFilter = new CheckboxGroup<>();
    levelFilter.setLabel("Filter by Level");
    levelFilter.setItems(LogLevel.values());
    levelFilter.setValue(Set.of(LogLevel.values())); // All selected by default
    levelFilter.addValueChangeListener(e -> filterLogs());
    levelFilter.getStyle().set("margin-bottom", "var(--lumo-space-s)");
    
    // Logs container - let parent handle scrolling
    logsContainer = new Div();
    logsContainer.setWidthFull();
    logsContainer.getStyle().set("flex-grow", "1");
    logsContainer.getStyle().set("padding", "var(--lumo-space-xs)");
    
    // Footer with file info
    Span footerInfo = new Span();
    updateFooterInfo(footerInfo);
    footerInfo.getStyle().set("font-size", "var(--lumo-font-size-xs)");
    footerInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");
    footerInfo.getStyle().set("margin-top", "var(--lumo-space-s)");
    
    add(header, searchField, levelFilter, logsContainer, footerInfo);
    
    // Initial load
    refresh();
  }
  
  /**
   * Refreshes the logs display.
   */
  public void refresh() {
    allLogs = loggingService.getRecentLogs(0);
    filterLogs();
  }
  
  /**
   * Filters logs based on search text and level filter.
   */
  private void filterLogs() {
    String searchText = searchField.getValue().toLowerCase();
    Set<LogLevel> selectedLevels = levelFilter.getValue();
    
    List<LogEntry> filtered = allLogs.stream()
        .filter(entry -> selectedLevels.contains(entry.getLevel()))
        .filter(entry -> matchesSearch(entry, searchText))
        .collect(Collectors.toList());
    
    displayLogs(filtered);
  }
  
  /**
   * Checks if a log entry matches the search text.
   *
   * @param entry the log entry
   * @param searchText the search text (lowercase)
   * @return true if matches
   */
  private boolean matchesSearch(LogEntry entry, String searchText) {
    if (searchText.isEmpty()) {
      return true;
    }
    
    return entry.getMessage().toLowerCase().contains(searchText)
        || entry.getCategory().toLowerCase().contains(searchText)
        || (entry.getDetails() != null && entry.getDetails().toLowerCase().contains(searchText))
        || (entry.getServerName() != null && entry.getServerName().toLowerCase().contains(searchText));
  }
  
  /**
   * Displays filtered logs in the container.
   *
   * @param logs the logs to display
   */
  private void displayLogs(List<LogEntry> logs) {
    logsContainer.removeAll();
    
    if (logs.isEmpty()) {
      Span emptyMessage = new Span("No log entries");
      emptyMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");
      emptyMessage.getStyle().set("font-style", "italic");
      logsContainer.add(emptyMessage);
      return;
    }
    
    // Display in reverse order (most recent first)
    for (int i = logs.size() - 1; i >= 0; i--) {
      LogEntry entry = logs.get(i);
      logsContainer.add(createLogEntryComponent(entry));
    }
  }
  
  /**
   * Creates a UI component for a log entry.
   *
   * @param entry the log entry
   * @return the component
   */
  private Component createLogEntryComponent(LogEntry entry) {
    Div entryDiv = new Div();
    entryDiv.setWidthFull();
    entryDiv.getStyle().set("padding", "var(--lumo-space-xs)");
    entryDiv.getStyle().set("margin-bottom", "var(--lumo-space-xs)");
    entryDiv.getStyle().set("border-left", "3px solid " + getLevelColor(entry.getLevel()));
    entryDiv.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
    entryDiv.getStyle().set("border-radius", "var(--lumo-border-radius-s)");
    
    // Timestamp and level
    Span timestamp = new Span(entry.getTimestamp().format(TIME_FORMATTER));
    timestamp.getStyle().set("font-size", "var(--lumo-font-size-xs)");
    timestamp.getStyle().set("color", "var(--lumo-secondary-text-color)");
    
    Span levelBadge = new Span(entry.getLevel().toString());
    levelBadge.getStyle().set("font-size", "var(--lumo-font-size-xs)");
    levelBadge.getStyle().set("font-weight", "bold");
    levelBadge.getStyle().set("color", getLevelColor(entry.getLevel()));
    levelBadge.getStyle().set("margin-left", "var(--lumo-space-xs)");
    
    Span categoryBadge = new Span(entry.getCategory());
    categoryBadge.getStyle().set("font-size", "var(--lumo-font-size-xs)");
    categoryBadge.getStyle().set("background-color", "var(--lumo-contrast-10pct)");
    categoryBadge.getStyle().set("padding", "2px 6px");
    categoryBadge.getStyle().set("border-radius", "var(--lumo-border-radius-s)");
    categoryBadge.getStyle().set("margin-left", "var(--lumo-space-xs)");
    
    HorizontalLayout headerLine = new HorizontalLayout(timestamp, levelBadge, categoryBadge);
    headerLine.setSpacing(false);
    headerLine.setAlignItems(Alignment.CENTER);
    headerLine.getStyle().set("margin-bottom", "var(--lumo-space-xs)");
    
    // Message
    Span message = new Span(entry.getMessage());
    message.getStyle().set("font-size", "var(--lumo-font-size-s)");
    message.getStyle().set("display", "block");
    
    entryDiv.add(headerLine, message);
    
    // Server name if present
    if (entry.getServerName() != null) {
      Span serverInfo = new Span("Server: " + entry.getServerName());
      serverInfo.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      serverInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");
      serverInfo.getStyle().set("font-style", "italic");
      serverInfo.getStyle().set("display", "block");
      serverInfo.getStyle().set("margin-top", "var(--lumo-space-xs)");
      entryDiv.add(serverInfo);
    }
    
    // Details if present
    if (entry.getDetails() != null) {
      Span details = new Span(entry.getDetails());
      details.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      details.getStyle().set("color", "var(--lumo-secondary-text-color)");
      details.getStyle().set("display", "block");
      details.getStyle().set("margin-top", "var(--lumo-space-xs)");
      entryDiv.add(details);
    }
    
    // LDIF data if present
    if (entry.getLdifData() != null) {
      Div ldifContainer = new Div();
      ldifContainer.getStyle().set("font-family", "monospace");
      ldifContainer.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      ldifContainer.getStyle().set("background-color", "var(--lumo-contrast-10pct)");
      ldifContainer.getStyle().set("padding", "var(--lumo-space-xs)");
      ldifContainer.getStyle().set("border-radius", "var(--lumo-border-radius-s)");
      ldifContainer.getStyle().set("margin-top", "var(--lumo-space-xs)");
      ldifContainer.getStyle().set("white-space", "pre-wrap");
      ldifContainer.getStyle().set("word-break", "break-all");
      ldifContainer.getStyle().set("overflow-x", "auto");
      
      Span ldifLabel = new Span("LDIF:");
      ldifLabel.getStyle().set("font-weight", "bold");
      ldifLabel.getStyle().set("display", "block");
      ldifLabel.getStyle().set("margin-bottom", "2px");
      
      Span ldifContent = new Span(entry.getLdifData());
      ldifContent.getStyle().set("display", "block");
      
      ldifContainer.add(ldifLabel, ldifContent);
      entryDiv.add(ldifContainer);
    }
    
    return entryDiv;
  }
  
  /**
   * Gets the color for a log level.
   *
   * @param level the log level
   * @return the CSS color value
   */
  private String getLevelColor(LogLevel level) {
    switch (level) {
      case ERROR:
        return "var(--lumo-error-color)";
      case WARNING:
        return "var(--lumo-warning-color)";
      case INFO:
        return "var(--lumo-primary-color)";
      case DEBUG:
        return "var(--lumo-contrast-60pct)";
      default:
        return "var(--lumo-contrast-60pct)";
    }
  }
  
  /**
   * Clears all logs.
   */
  private void clearLogs() {
    loggingService.clearLogs();
    refresh();
  }
  
  /**
   * Updates the footer information.
   *
   * @param footerInfo the footer span to update
   */
  private void updateFooterInfo(Span footerInfo) {
    int count = loggingService.getLogCount();
    String filePath = loggingService.getLogFilePath().toString();
    long fileSize = 0;
    
    try {
      fileSize = Files.size(loggingService.getLogFilePath()) / 1024; // KB
    } catch (Exception e) {
      // File may not exist yet
    }
    
    footerInfo.setText(String.format("%d entries | %d KB | %s", count, fileSize, filePath));
  }

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    super.onAttach(attachEvent);
    // Register for log updates when component is attached
    loggingService.addListener(this);
  }

  @Override
  protected void onDetach(DetachEvent detachEvent) {
    super.onDetach(detachEvent);
    // Unregister when component is detached
    loggingService.removeListener(this);
  }

  @Override
  public void onLogAdded(LogEntry entry) {
    // Update UI on log addition (called from LoggingService)
    getUI().ifPresent(ui -> ui.access(() -> {
      refresh();
    }));
  }
}
