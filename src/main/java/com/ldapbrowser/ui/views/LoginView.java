package com.ldapbrowser.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.beans.factory.annotation.Value;

/**
 * Login view displayed when {@code ldapbrowser.auth.mode} is
 * {@code local} or {@code oauth}.
 *
 * <p>For local authentication the Vaadin {@link LoginForm} posts
 * credentials to Spring Security's form-login endpoint.
 * For OAuth the user is redirected to the OIDC provider before
 * this view renders, so it acts as a fallback only.
 */
@Route("login")
@PageTitle("Login | LDAP Browser")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

  private final LoginForm loginForm = new LoginForm();
  private final String authMode;

  /**
   * Constructs the login view.
   *
   * @param authMode the active authentication mode
   */
  public LoginView(@Value("${ldapbrowser.auth.mode:none}") String authMode) {
    this.authMode = authMode;

    setSizeFull();
    setAlignItems(FlexComponent.Alignment.CENTER);
    setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

    H1 title = new H1("LDAP Browser");
    title.getStyle().set("color", "var(--lumo-primary-text-color)");

    if ("oauth".equals(authMode)) {
      // OAuth mode — show a simple message; Spring Security will
      // redirect to the OIDC provider automatically.
      Paragraph info = new Paragraph(
          "Redirecting to identity provider...");
      add(title, info);
    } else {
      // Local mode — show the standard login form
      loginForm.setAction("login");
      loginForm.setForgotPasswordButtonVisible(false);
      add(title, loginForm);
    }
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    // Show error message on failed login attempt
    if (event.getLocation().getQueryParameters()
        .getParameters().containsKey("error")) {
      loginForm.setError(true);
    }
  }
}
