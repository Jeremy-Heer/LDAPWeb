package com.ldapbrowser.config;

import com.ldapbrowser.ui.views.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration supporting three authentication modes.
 *
 * <ul>
 *   <li>{@code none} &ndash; no authentication, all routes open (default)</li>
 *   <li>{@code local} &ndash; form login with a JSON-backed user store</li>
 *   <li>{@code oauth} &ndash; OpenID Connect via any compliant provider</li>
 * </ul>
 *
 * <p>Set {@code ldapbrowser.auth.mode} in application properties to choose.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  private static final Logger logger =
      LoggerFactory.getLogger(SecurityConfiguration.class);

  private final String authMode;

  /**
   * Creates the security configuration.
   *
   * @param authMode authentication mode from property
   */
  public SecurityConfiguration(
      @Value("${ldapbrowser.auth.mode:none}") String authMode) {
    this.authMode = authMode;
    logger.info("Security configuration initialized with auth mode: {}",
        authMode);
  }

  /**
   * Builds the {@link SecurityFilterChain} based on the active auth mode.
   *
   * @param http the {@link HttpSecurity} builder
   * @return configured security filter chain
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http)
      throws Exception {
    switch (authMode) {
      case "local" -> configureLocal(http);
      case "oauth" -> configureOauth(http);
      default -> configureNone(http);
    }
    return http.build();
  }

  /**
   * No authentication &ndash; permit all requests.
   *
   * <p>The {@link VaadinSecurityConfigurer} is intentionally not applied
   * here because it registers its own {@code authorizeHttpRequests}
   * rules that deny unauthenticated access, and those rules conflict
   * with a blanket {@code anyRequest().permitAll()}.  Skipping the
   * configurer means Vaadin role annotations are not enforced, which
   * is exactly the desired behaviour for "no auth" mode.
   */
  private void configureNone(HttpSecurity http) throws Exception {
    logger.info("Auth mode: none - all routes are open");
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    http.csrf(csrf -> csrf.disable());
  }

  /**
   * Local form login backed by
   * {@link com.ldapbrowser.service.UserService}.
   */
  private void configureLocal(HttpSecurity http) throws Exception {
    logger.info("Auth mode: local - form login enabled");
    http.with(VaadinSecurityConfigurer.vaadin(),
        vaadin -> vaadin.loginView(LoginView.class));
    http.logout(logout -> logout
        .logoutUrl("/logout")
        .logoutSuccessUrl("/login"));
  }

  /**
   * OAuth2 / OpenID Connect login.
   *
   * <p>The default success URL is {@code /browse} rather than the
   * root ({@code /}) because the root route ({@code ServerView})
   * requires {@code ADMIN}.  OIDC users whose token lacks the
   * configured admin-role claim receive only {@code VIEWER} and
   * would be denied access to the root, resulting in a Vaadin
   * "Could not navigate to ''" error.  {@code /browse} is
   * accessible to both {@code ADMIN} and {@code VIEWER}.
   */
  private void configureOauth(HttpSecurity http) throws Exception {
    logger.info("Auth mode: oauth - OIDC login enabled");
    http.with(VaadinSecurityConfigurer.vaadin(),
        vaadin -> vaadin
            .oauth2LoginPage("/login")
            .loginView(LoginView.class));
    http.oauth2Login(oauth -> oauth
        .defaultSuccessUrl("/browse", true));
    http.logout(logout -> logout
        .logoutUrl("/logout")
        .logoutSuccessUrl("/login"));
  }

  /**
   * Provides the BCrypt password encoder used by local auth.
   *
   * @return password encoder
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Gets the active authentication mode.
   *
   * @return auth mode string
   */
  public String getAuthMode() {
    return authMode;
  }
}
