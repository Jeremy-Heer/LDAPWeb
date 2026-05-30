# Keycloak Setup for LDAP Browser

This guide shows one practical way to wire Keycloak to LDAP Browser using
the app's OAuth/OIDC support and claim-to-role mapping.

## Overview

LDAP Browser expects a standard OIDC client and reads role values from a token
claim that you configure with `ldapbrowser.oauth.role-claim`. For Keycloak,
the most common choice is `realm_access.roles`.

The app then matches those claim values against role names created in the UI
and stored in `roles.json`.

## 1. Create a Realm

1. Sign in to the Keycloak admin console.
2. Create a new realm, or choose an existing realm for testing.
3. Copy the realm issuer URL. It usually looks like:

```text
http://localhost:8080/realms/<realm-name>
```

Use that value for `spring.security.oauth2.client.provider.oidc.issuer-uri`.

## 2. Create the OIDC Client

Create a client with these settings:

- **Client type**: OpenID Connect
- **Access type**: Confidential client
- **Standard flow**: Enabled
- **Direct access grants**: Disabled unless you need password grant testing
- **Valid redirect URIs**:
  - `http://localhost:8081/login/oauth2/code/oidc`
  - `https://<your-host>/login/oauth2/code/oidc` for production
- **Web origins**: `+` for development, or the exact origin of your app
- **Client secret**: Copy this into `spring.security.oauth2.client.registration.oidc.client-secret`

The redirect URI path must stay `/login/oauth2/code/oidc` because Spring Security
uses the registration id `oidc`.

## 3. Configure Claims

LDAP Browser can use any claim path that contains role values. For Keycloak, the
usual mapping is:

```properties
ldapbrowser.oauth.role-claim=realm_access.roles
ldapbrowser.oauth.default-role=DENY
```

If your realm emits roles in a different claim, update `ldapbrowser.oauth.role-claim`
to match that path.

## 4. Create Keycloak Roles

Create the Keycloak roles that you want the application to honor. The claim values
must match role names in `roles.json`.

Recommended starting roles:

- `Admin`
- `Viewer`
- `Auditor`

Then create matching roles in LDAP Browser's Roles UI and assign views and servers
there. The app resolves access from those UI-defined roles, not from properties.

## 5. Assign Roles to Test Users

Create a few users and assign realm roles so you can test different paths.

Suggested test matrix:

- User `alice` with role `Admin`
- User `bob` with role `Viewer`
- User `carol` with role `Auditor`
- User `dave` with no matching role

Expected results:

- `alice` gets the union of permissions from the `Admin` role in `roles.json`
- `bob` gets the union of permissions from the `Viewer` role in `roles.json`
- `carol` gets the union of permissions from the `Auditor` role in `roles.json`
- `dave` is authenticated but denied navigation when `ldapbrowser.oauth.default-role=DENY`

## 6. Application Configuration

Example properties:

```properties
spring.security.oauth2.client.registration.oidc.client-id=ldap-browser
spring.security.oauth2.client.registration.oidc.client-secret=YOUR_CLIENT_SECRET
spring.security.oauth2.client.provider.oidc.issuer-uri=http://localhost:8080/realms/ldapbrowser
ldapbrowser.oauth.role-claim=realm_access.roles
ldapbrowser.oauth.default-role=DENY
```

## 7. Optional mTLS

If your Keycloak client is configured for certificate-based client authentication,
enable mTLS in the app and set the client authentication method accordingly:

```properties
ldapbrowser.oauth.mtls.enabled=true
ldapbrowser.oauth.mtls.ssl-bundle=oidc-mtls
spring.security.oauth2.client.registration.oidc.client-authentication-method=tls_client_auth
```

Use `self_signed_tls_client_auth` when your Keycloak setup requires it.

## 8. Troubleshooting

If login succeeds but access is denied, check the following:

- The issuer URL matches the realm issuer exactly.
- The redirect URI registered in Keycloak matches the app URL.
- The configured claim path matches the token payload.
- The claim values match role names in `roles.json` exactly, ignoring case.
- The app logs from `OidcRoleMappingConfig` show which claim values were extracted
  and which roles were resolved.
