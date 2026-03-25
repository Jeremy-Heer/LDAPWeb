package com.ldapbrowser.service;

import com.ldapbrowser.model.EntryTemplate;
import com.ldapbrowser.model.LdapServerConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Service for managing entry templates.
 * Handles loading and saving templates to templates.json file.
 */
@Service
public class TemplateService {

  private static final Logger logger =
      LoggerFactory.getLogger(TemplateService.class);
  private static final String TEMPLATE_FILE = "templates.json";
  private final ObjectMapper objectMapper;
  private final Path templatePath;

  /**
   * Constructor initializes the template service.
   *
   * @param settingsDirValue resolved path of the application settings
   *     directory (injected from {@code ldapbrowser.settings.dir})
   */
  @Autowired
  public TemplateService(
      @Value("${ldapbrowser.settings.dir}") String settingsDirValue) {
    this.objectMapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();
    Path settingsDir = Path.of(settingsDirValue);
    this.templatePath = settingsDir.resolve(TEMPLATE_FILE);
    ensureDirectoryExists();
  }

  /**
   * Constructor for testing with explicit path.
   *
   * @param templatePath path to templates file
   * @param objectMapper Jackson object mapper
   */
  TemplateService(Path templatePath, ObjectMapper objectMapper) {
    this.templatePath = templatePath;
    this.objectMapper = objectMapper;
    ensureDirectoryExists();
  }

  private void ensureDirectoryExists() {
    try {
      Path dir = templatePath.getParent();
      if (dir != null && !Files.exists(dir)) {
        Files.createDirectories(dir);
        logger.info("Created template directory: {}", dir);
      }
    } catch (IOException e) {
      logger.error("Failed to create template directory", e);
    }
  }

  /**
   * Loads all entry templates from file.
   *
   * @return list of entry templates
   */
  public List<EntryTemplate> loadTemplates() {
    try {
      File file = templatePath.toFile();
      if (!file.exists()) {
        logger.info("Template file not found, returning empty list");
        return new ArrayList<>();
      }

      EntryTemplate[] templates =
          objectMapper.readValue(file, EntryTemplate[].class);
      logger.info("Loaded {} entry templates", templates.length);
      return new ArrayList<>(Arrays.asList(templates));
    } catch (JacksonException e) {
      logger.error("Failed to load templates", e);
      return new ArrayList<>();
    }
  }

  /**
   * Saves all entry templates to file.
   *
   * @param templates list of templates to save
   * @throws IOException if save fails
   */
  public void saveTemplates(List<EntryTemplate> templates)
      throws IOException {
    try {
      objectMapper.writeValue(templatePath.toFile(), templates);
      setRestrictivePermissions(templatePath);
      logger.info("Saved {} entry templates to {}",
          templates.size(), templatePath);
    } catch (Exception e) {
      logger.error("Failed to save templates", e);
      throw new IOException(e);
    }
  }

  /**
   * Saves a single entry template. Updates existing or adds new.
   *
   * @param template template to save
   * @throws IOException if save fails
   */
  public void saveTemplate(EntryTemplate template) throws IOException {
    List<EntryTemplate> templates = loadTemplates();

    Optional<EntryTemplate> existing = templates.stream()
        .filter(t -> t.getName().equals(template.getName()))
        .findFirst();

    if (existing.isPresent()) {
      int index = templates.indexOf(existing.get());
      templates.set(index, template);
      logger.info("Updated template: {}", template.getName());
    } else {
      templates.add(template);
      logger.info("Added new template: {}", template.getName());
    }

    saveTemplates(templates);
  }

  /**
   * Deletes a template by name.
   *
   * @param name name of template to delete
   * @throws IOException if save fails
   */
  public void deleteTemplate(String name) throws IOException {
    List<EntryTemplate> templates = loadTemplates();
    templates.removeIf(t -> t.getName().equals(name));
    saveTemplates(templates);
    logger.info("Deleted template: {}", name);
  }

  /**
   * Gets a template by name.
   *
   * @param name template name
   * @return optional template
   */
  public Optional<EntryTemplate> getTemplate(String name) {
    return loadTemplates().stream()
        .filter(t -> t.getName().equals(name))
        .findFirst();
  }

  /**
   * Checks if a template with the given name exists.
   *
   * @param name template name
   * @return true if exists
   */
  public boolean templateExists(String name) {
    return getTemplate(name).isPresent();
  }

  /**
   * Returns templates allowed for the given server configuration.
   *
   * <p>If the server's {@code allowedTemplates} list is empty or
   * null, all templates are returned (no filtering).
   *
   * @param config server configuration
   * @return filtered list of templates
   */
  public List<EntryTemplate> getTemplatesForServer(
      LdapServerConfig config) {
    List<EntryTemplate> all = loadTemplates();
    if (config == null) {
      return all;
    }
    List<String> allowed = config.getAllowedTemplates();
    if (allowed == null || allowed.isEmpty()) {
      return all;
    }
    return all.stream()
        .filter(t -> allowed.contains(t.getName()))
        .toList();
  }

  /**
   * Gets the template file path.
   *
   * @return template file path
   */
  public Path getTemplatePath() {
    return templatePath;
  }

  /**
   * Sets owner-only read/write permissions (Unix-like systems).
   *
   * @param path file path
   */
  private void setRestrictivePermissions(Path path) {
    try {
      Set<PosixFilePermission> perms = new HashSet<>();
      perms.add(PosixFilePermission.OWNER_READ);
      perms.add(PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(path, perms);
      logger.debug("Set restrictive permissions on: {}", path);
    } catch (UnsupportedOperationException e) {
      logger.debug(
          "POSIX permissions not supported on this system");
    } catch (IOException e) {
      logger.warn(
          "Failed to set restrictive permissions on: {}", path, e);
    }
  }
}
