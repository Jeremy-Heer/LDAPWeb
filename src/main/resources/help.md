# LDAP Browser Help

Welcome to LDAP Browser! This application helps you manage, browse, and search LDAP directories. Select one or more servers from the navbar dropdown to get started.

## Server

The Server view manages LDAP server connection configurations.

### Features:
- Add new LDAP server connections with host, port, and credentials
- Edit existing server configurations
- Copy server configurations for quick setup
- Delete server configurations
- Test connections to verify server accessibility
- SSL/TLS support with certificate validation
- Connection pooling for improved performance

### How to Use:
- Click 'Add Server' to create a new server configuration
- Fill in the server details (name, host, port, bind DN, password)
- Enable SSL/TLS if your server requires secure connections
- Click 'Test Connection' to verify the configuration
- Save the configuration to make it available in the server selector

## Search

The Search view allows you to query LDAP directories using flexible search criteria.

### Features:
- Quick search with base DN and filter
- Advanced search builder for complex queries
- Search scope selection (base, one level, subtree)
- Multi-server search across selected servers
- View and edit search results
- Export search results
- Delete entries directly from results

### How to Use:
- Select one or more servers from the navbar
- Enter a base DN (starting point for the search)
- Enter an LDAP filter (e.g., '(objectClass=person)' or '(uid=jdoe)')
- Select the search scope
- Click 'Search' to execute the query
- Click on a result entry to view or edit its attributes

## Browse

The Browse view provides a tree-based navigation interface for exploring LDAP directories.

### Features:
- Tree view of LDAP directory structure
- Expand and collapse nodes to navigate
- View entry attributes in detail panel
- Edit entry attributes
- Delete entries with confirmation
- Multi-server browsing with server identification
- Restore previously browsed locations via URL parameters

### How to Use:
- Select one or more servers from the navbar
- Click on nodes in the tree to expand them
- Select an entry to view its attributes on the right panel
- Click 'Edit' to modify attributes
- Click 'Delete' to remove an entry (requires confirmation)

## Schema

The Schema view displays LDAP schema information including object classes, attribute types, matching rules, and syntaxes.

### Features:
- View object classes and their attributes
- Explore attribute types and their properties
- Browse matching rules for attribute comparison
- View supported LDAP syntaxes
- Filter schema elements by name or properties
- Multi-server schema inspection with server column
- Detailed information panel for selected elements

### How to Use:
- Select one or more servers from the navbar
- The schema is automatically loaded when you access the view
- Switch between tabs to view different schema element types
- Use the filter field to search for specific schema elements
- Click on a schema element to view its detailed properties

## Create

The Create view enables you to create new LDAP entries with custom attributes.

### Features:
- Create new LDAP entries interactively
- Specify parent DN and RDN for new entries
- Browse directory to select parent DN
- Add multiple attributes with values
- Support for multi-valued attributes
- Validation of DN structure
- Clear form for quick reset

### How to Use:
- Select a server from the navbar
- Enter the RDN (e.g., 'cn=John Doe')
- Enter or browse to select the parent DN
- Click 'Add Row' to add attribute-value pairs
- Fill in attribute names and values
- Click 'Create Entry' to save the new entry
- Use 'Clear' to reset the form

## Access

The Access view manages LDAP access control lists (ACLs) and permissions.

### Features:
- View global access control policies
- Manage entry-specific access controls
- Check effective rights for users
- Understand permission inheritance
- Multi-tabbed interface for different access control aspects

### How to Use:
- Select one or more servers from the navbar
- Switch between tabs to view different access control types
- 'Global Access Control': View directory-wide ACL policies
- 'Entry Access Control': Manage ACLs for specific entries
- 'Effective Rights': Check what permissions a user has on entries

## Bulk

The Bulk view allows you to perform operations on multiple LDAP entries at once.

### Features:
- Bulk modifications across multiple entries
- Batch attribute updates
- Mass deletion with safeguards
- Apply changes to search result sets
- Progress tracking for bulk operations
- Error reporting for failed operations

### How to Use:
- Select a server from the navbar
- Define the scope of entries to modify (via search or DN list)
- Specify the operation type (add, modify, delete attributes)
- Define the attribute changes to apply
- Review and confirm the bulk operation
- Monitor progress and review results

## Export

The Export view enables you to export LDAP data in various formats.

### Features:
- Export entries to LDIF format
- Export to CSV for spreadsheet applications
- Export to JSON for data processing
- Select specific attributes to export
- Filter entries with search criteria
- Download exported files directly
- Preview export before downloading

### How to Use:
- Select a server from the navbar
- Specify the base DN for export
- Choose the export format (LDIF, CSV, JSON)
- Optionally select specific attributes to include
- Apply search filters if needed
- Click 'Export' to generate the file
- Download the exported file

## Settings

The Settings view provides application configuration, security management, and template
customization. It is organized into tabs — which tabs appear depends on your authentication mode.

### Users Tab

Only shown when local-auth mode is active. Manage local user accounts:

- Add, edit, and delete user accounts
- Set and reset passwords
- Assign roles to control which views each user can access

### Truststore Tab

Manage the trusted certificate store used for TLS connections to LDAP servers:

- View all currently trusted certificates with subject, issuer, and expiry date
- Upload `.crt` or `.pem` certificate files to add a new trusted certificate
- Paste PEM text directly instead of uploading a file
- Delete certificates that are no longer needed

If a server uses a self-signed or private CA certificate, import that certificate here before
connecting so the TLS handshake succeeds.

### Keystore Tab

Manage the application's internal encryption keystore. The keystore holds the symmetric key
used to encrypt saved LDAP passwords at rest. You can rotate the key here, which re-encrypts
all stored passwords automatically.

### Encryption Tab

Configure whether passwords stored in server configurations are encrypted. When enabled,
passwords are encrypted using the key in the keystore before being written to disk.

### Templates Tab

**Templates** are the most powerful customization feature in LDAP Browser. A template defines
how a particular type of LDAP entry behaves in the Create, View/Edit, and Search views. Each
template can have up to three sections — enable only the sections you need.

#### Managing Templates

- **Add** — create a new template from scratch
- **Edit** / double-click a row — open the template editor
- **Duplicate** — copy an existing template as a starting point
- **Delete** — permanently remove a template
- **Refresh** — reload templates from disk

The template grid shows at a glance which sections (Create, View/Edit, Search) are configured
for each template.

#### Template Editor — Create Section

When enabled, this section customizes the **Create Entry** view for this type of entry.

- **Parent Filter** — an LDAP filter used to populate the parent DN drop-down (e.g.
  `(&(objectClass=organizationalUnit)(ou=people))`). Only matching entries are offered as
  parent DN choices.
- **Base DN** — limits the parent-DN search to a specific subtree. Use `Default` to search
  from the server's default base, or enter a named base defined in the server config.
- **Attribute rows** — define the fields shown in the Create view. Each row has:
  - *Display Name* — a friendly label shown in the UI
  - *LDAP Attribute* — the actual LDAP attribute name written to the directory
  - *Req* — marks the attribute as required; the form will not submit without a value
  - *Type* — field type: Text, Password, Multi-value, or TextArea
  - *Hidden* — pre-populated attributes that are sent to the directory but not shown to the user
  - **Naming** — when checked, this attribute's value is used to build the RDN of the new
    entry (e.g. checking the `uid` row means the DN is formed as `uid=<value>,<parent DN>`).
    You can check multiple attributes to create a multi-valued RDN joined with `+`.

#### Template Editor — View/Edit Section

When enabled, this section customizes the attribute panel when you open an existing entry in
Browse or Search results.

- **Matching Filter** — an LDAP filter (e.g. `(objectClass=inetOrgPerson)`) that determines
  when this template is automatically applied to an entry. The first matching template is used.
- **Attribute rows** — same columns as Create (Display Name, LDAP Attribute, Req, Type, Hidden)
  but without the Naming column.

#### Template Editor — Search Section

When enabled, this section adds the template as a quick-search option in the Search view.

- **Search Filter** — the base LDAP filter; use `{SEARCH}` as the placeholder for the user's
  search text (e.g. `(&(objectClass=inetOrgPerson)(uid={SEARCH}))`).
- **Base Filter** — an LDAP filter to find the base DN candidates for the search.
- **Base DN** — starting point for the search; `Default` or a named base from server config.
- **Scope** — `base`, `one`, or `sub` (subtree).
- **Return Attributes** — comma-separated list of attributes to fetch (e.g. `cn,uid,mail`).

### Roles Tab

Define roles to control which views and servers each user can access. Roles are assigned to
users in the Users tab. Each role specifies:

- Which navigation views are accessible (Browse, Search, Create, Schema, etc.)
- Which LDAP server configurations the role is allowed to connect to

## OAuth / OIDC Authentication

When running with `ldapbrowser.auth.mode=oauth`, users authenticate with an OpenID Connect
identity provider.

### Create The OAuth Client

When creating the client in your identity provider, use these baseline settings:

- **Application type** - Web application / confidential client
- **OAuth flow** - Authorization Code
- **OIDC support** - Required
- **Client authentication** - `client_secret_basic` by default; use `tls_client_auth`
  or `self_signed_tls_client_auth` only when enabling OAuth mTLS
- **Redirect URI** - `http://localhost:8081/login/oauth2/code/oidc`
- **Redirect URI pattern** - `http(s)://<host>:<port>/login/oauth2/code/oidc`
- **Requested scopes** - `openid,profile,email`
- **Issuer URL** - Use the provider issuer / authority URL exposed by OIDC discovery

Notes:

- The redirect URI path is fixed by the current Spring Security registration id: `oidc`.
- If you change host, port, reverse proxy, or TLS termination, register the externally
  visible callback URI with the provider.
- If your provider requires exact URI matching, register a separate redirect URI for each
  environment.
- Dynamic role resolution expects the configured role claim to contain values that match role
  names in `roles.json`.
- If OAuth mTLS is enabled, configure the provider client for certificate-based client
  authentication and set
  `spring.security.oauth2.client.registration.oidc.client-authentication-method`
  accordingly.
- For a provider-specific setup walkthrough, see [docs/keycloak-setup.md](../../docs/keycloak-setup.md).

### Claim-Based Role Mapping

OAuth access is controlled by matching token claim values to role names in
`roles.json` (case-insensitive exact match).

For a provider-specific setup walkthrough, see [docs/keycloak-setup.md](../../docs/keycloak-setup.md).

Relevant properties:

- `ldapbrowser.oauth.role-claim` - claim path containing role values (dot-path supported)
- `ldapbrowser.oauth.default-role` - fallback role name from `roles.json`, or `DENY`
- `ldapbrowser.oauth.admin-role` / `ldapbrowser.oauth.viewer-role` - legacy properties,
  not used for dynamic OAuth authorization

With `default-role=DENY`, users without a matching claim value are authenticated but denied
application access by default.

### OAuth mTLS (Token Endpoint)

Optional mTLS can be enabled for OAuth token endpoint calls:

- `ldapbrowser.oauth.mtls.enabled=true`
- `ldapbrowser.oauth.mtls.ssl-bundle=<bundle-name>`
- `spring.security.oauth2.client.registration.oidc.client-authentication-method=tls_client_auth`

Use `self_signed_tls_client_auth` when required by your identity provider.
