# LDAP Web Browser

## v0.22 - ✅ COMPLETED - Password Encryption with Keystore

### Implemented Features

#### 1. **KeystoreService** - Encryption key management
- ✅ Created `KeystoreService.java` for managing encryption keys
- ✅ Keystore file: `~/.ldapbrowser/keystore.pfx` (PKCS12 format)
- ✅ PIN file: `~/.ldapbrowser/keystore.pin` (secure random password)
- ✅ Automatic initialization on first use
- ✅ AES-256 encryption key generation using SecureRandom
- ✅ Key alias: "ldap-password-encryption-key"
- ✅ Restrictive file permissions on PIN file (owner read/write only)
- ✅ Key rotation capability with `rotateKey()` method
- ✅ Keystore statistics display (creation date, algorithm, key size)

#### 2. **EncryptionService** - Password encryption/decryption
- ✅ Created `EncryptionService.java` for AES-256-GCM encryption
- ✅ Algorithm: AES-256-GCM (Galois/Counter Mode for authenticated encryption)
- ✅ 96-bit random IV (initialization vector) per encryption
- ✅ 128-bit authentication tag for integrity verification
- ✅ Base64-encoded encrypted format: IV + ciphertext + auth_tag
- ✅ Thread-safe encryption operations
- ✅ Configurable encryption mode via `ldapbrowser.encryption.enabled` property
- ✅ Heuristic detection of encrypted vs cleartext passwords
- ✅ Re-encryption support for key rotation
- ✅ Custom `EncryptionException` for error handling

#### 3. **ConfigurationService** - Automatic encryption integration
- ✅ Updated to inject `EncryptionService` dependency
- ✅ Automatic password encryption on save operations
- ✅ Automatic password decryption on load operations
- ✅ Transparent encryption - no changes required to calling code
- ✅ Migration support for existing cleartext passwords
- ✅ Bulk password migration method: `migratePasswords(boolean toEncrypted)`
- ✅ Automatic detection and migration of cleartext passwords

#### 4. **Settings - Keystore Tab** - Full keystore management UI
- ✅ Replaced placeholder with fully functional keystore management
- ✅ **Display Features:**
  - Keystore initialization status (Initialized/Not Initialized)
  - Keystore information (location, type, algorithm, key size, creation date)
  - Real-time status updates
  
- ✅ **Initialize Keystore:**
  - Creates keystore.pfx and keystore.pin
  - Generates AES-256 encryption key
  - Automatic initialization on first encryption operation
  - Button disabled when already initialized
  
- ✅ **Rotate Encryption Key:**
  - Generates new encryption key
  - Re-encrypts all passwords with new key
  - Confirmation dialog with warning
  - Enabled only when keystore is initialized
  
- ✅ **Refresh Statistics:**
  - Updates keystore information display
  - Shows current status

#### 5. **Settings - Encryption Tab** - Encryption mode management
- ✅ Replaced placeholder with fully functional encryption settings
- ✅ **Display Features:**
  - Current encryption mode (Encrypted/Cleartext)
  - Encryption algorithm details (AES-256-GCM)
  - Key storage location
  - Encrypted field information
  
- ✅ **Mode Configuration:**
  - Instructions for changing encryption mode via application.properties
  - `ldapbrowser.encryption.enabled=true` for encrypted mode (default)
  - `ldapbrowser.encryption.enabled=false` for cleartext mode (development)
  - Warning about application restart requirement
  
- ✅ **Password Migration:**
  - "Encrypt All Passwords" button - converts cleartext to encrypted
  - "Decrypt All Passwords" button - converts encrypted to cleartext
  - Confirmation dialogs with warnings
  - Error handling and notifications

#### 6. **Application Configuration**
- ✅ Added `ldapbrowser.encryption.enabled` property to `application.properties`
- ✅ Default value: `true` (encryption enabled)
- ✅ Set to `false` for development/debugging environments
- ✅ Requires application restart when changed

### Technical Implementation

**KeystoreService.java** (333 lines):
- PKCS12 keystore type for broad compatibility
- SecretKey storage with password protection
- 32-byte URL-safe Base64-encoded PIN generation
- AES KeyGenerator with 256-bit key size
- POSIX file permissions on Unix-like systems
- Methods:
  - `initializeKeystoreIfNeeded()` - Creates keystore and generates key
  - `getEncryptionKey()` - Retrieves AES key from keystore
  - `rotateKey()` - Generates new key and returns old key
  - `getKeystoreStats()` - Returns formatted keystore information
  - `isInitialized()` - Checks if keystore exists and has key

**EncryptionService.java** (218 lines):
- AES/GCM/NoPadding cipher mode
- 12-byte IV generated per encryption (SecureRandom)
- 128-bit GCM authentication tag
- ByteBuffer for IV + ciphertext combination
- Spring `@Value` annotation for configuration injection
- Methods:
  - `encryptPassword(String)` - Encrypts cleartext password
  - `decryptPassword(String)` - Decrypts encrypted password
  - `isEncryptionEnabled()` - Returns encryption mode
  - `isPasswordEncrypted(String)` - Detects encrypted passwords
  - `reencryptPassword(String, SecretKey)` - Re-encrypts with new key

**ConfigurationService.java** updates:
- Added `EncryptionService` field and constructor injection
- Modified `loadConfigurations()`:
  - Loads JSON from file
  - Decrypts passwords automatically for all configs
  - Detects and logs cleartext passwords for migration
- Modified `saveConfigurations()`:
  - Creates copies of configs
  - Encrypts passwords before saving
  - Writes encrypted passwords to JSON
- Added private helper methods:
  - `encryptPassword(LdapServerConfig)` - Encrypts config password
  - `decryptPassword(LdapServerConfig)` - Decrypts config password
- Added public migration method:
  - `migratePasswords(boolean toEncrypted)` - Bulk password conversion

**SettingsView.java** updates:
- Added `KeystoreService`, `EncryptionService`, `ConfigurationService` dependencies
- Replaced `createKeystoreTab()` placeholder (~130 lines):
  - Keystore status display (initialized/not initialized)
  - Keystore statistics TextArea with detailed information
  - Initialize button with success/error notifications
  - Rotate key button with confirmation dialog
  - Automatic re-encryption on key rotation
  - Refresh button for status updates
- Replaced `createEncryptionTab()` placeholder (~150 lines):
  - Encryption mode status (Encrypted/Cleartext)
  - Algorithm and storage details display
  - Migration buttons (Encrypt All/Decrypt All)
  - Confirmation dialogs with warnings
  - Instructions for changing encryption mode
  - Success/error notifications
- Added helper methods:
  - `updateKeystoreStatus(Span)` - Updates keystore status label
  - `updateKeystoreStats(TextArea)` - Updates keystore statistics
  - `updateEncryptionStatus(Span)` - Updates encryption mode label

**application.properties** updates:
- Added `ldapbrowser.encryption.enabled=true` property
- Documented as encryption control setting
- Default: `true` (encryption enabled for production)

### Security Benefits
- **Strong Encryption:** AES-256-GCM provides industry-standard encryption with authentication
- **Key Isolation:** Encryption key stored separately from encrypted data
- **Random IVs:** Each password encrypted with unique IV prevents pattern analysis
- **Authentication Tags:** GCM mode prevents tampering with encrypted data
- **Secure Key Storage:** PKCS12 keystore with password protection
- **Restrictive Permissions:** PIN file readable only by owner
- **Key Rotation:** Ability to rotate encryption keys for enhanced security
- **Transparent Migration:** Automatic detection and migration of cleartext passwords

### Migration Path
1. **Existing Users with Cleartext Passwords:**
   - On first load with encryption enabled, passwords are detected as cleartext
   - Logged for awareness: "Found cleartext password for server: X, will encrypt on next save"
   - Next save operation automatically encrypts passwords
   - Alternative: Use "Encrypt All Passwords" button in Settings > Encryption tab

2. **Development Environments:**
   - Set `ldapbrowser.encryption.enabled=false` in application.properties
   - Restart application
   - Use "Decrypt All Passwords" button to convert to cleartext
   - Passwords stored in cleartext for easy debugging

3. **Production Environments:**
   - Keep default `ldapbrowser.encryption.enabled=true`
   - Keystore automatically initialized on first use
   - All new passwords automatically encrypted
   - Existing cleartext passwords migrated on save

### Files Created
- `src/main/java/com/ldapbrowser/service/KeystoreService.java` - NEW (333 lines)
- `src/main/java/com/ldapbrowser/service/EncryptionService.java` - NEW (218 lines)

### Files Modified
- `src/main/java/com/ldapbrowser/service/ConfigurationService.java` - Added encryption support (~120 lines changed)
- `src/main/java/com/ldapbrowser/ui/views/SettingsView.java` - Implemented Keystore and Encryption tabs (~300 lines changed)
- `src/main/resources/application.properties` - Added encryption.enabled property (~3 lines)
- `docs/requirements.md` - Updated Settings and Core Services sections (~60 lines)
- `docs/changelog.md` - Updated with v0.22 completion details

### Build Verification
- ✅ Compiles successfully with Maven
- ✅ All imports resolved correctly
- ✅ Proper exception handling throughout
- ✅ Follows Google Java style conventions
- ✅ Spring dependency injection properly configured

### User Experience
- Transparent encryption - no user action required
- Automatic migration of existing passwords
- Clear status indicators for encryption state
- Informative error messages and notifications
- Development mode option for debugging
- Key rotation with confirmation dialogs
- Comprehensive keystore statistics display

### Future Enhancements
- Certificate-based authentication support in keystore
- Hardware security module (HSM) integration
- Key escrow/backup capabilities
- Encryption audit logging
- Password expiration warnings

---

## v0.21.3 - Selected server connection enhancement
  - when a server is deselected from the "Select servers..." dropdown,
    any open ldap connections to that server should disconnected and closed.

## v0.21.2 - ✅ COMPLETED - Certificate Validation UI Controls

### Bug Fix: Empty Truststore Handling ✅
- ✅ **Empty Truststore Support** - Fixed `InvalidAlgorithmParameterException` when truststore is empty
  - Issue: Java SSL required at least one trust anchor, causing exception before certificate dialog could appear
  - Solution: Detect empty truststore in constructor, reject all certificates initially
  - Behavior: Certificates properly captured for import even when truststore is empty
  - User Experience: Friendly message shown when automatic connection fails due to untrusted certificate
  - Connection Test: Use "Test Connection" button to see certificate import dialog
  - Technical: Skip TrustManagerFactory initialization when truststore has no certificates
  - UI Handling: LdapTreeGrid detects wrapped CertificateValidationException and shows helpful message

### Phase 1: Completed ✅
- ✅ **Server Configuration Dialog** - "Validate Certificate" checkbox added
  - Label: "Validate Certificate (recommended for production)"
  - Conditional enabling: Only enabled when SSL or StartTLS is selected
  - Automatic disabling: Checkbox disabled and unchecked when no security enabled
  - Vaadin Binder integration: Bound to `LdapServerConfig.validateCertificate` field
  - State initialization: Updates correctly after loading existing server config
  - User Experience: Prevents validation checkbox when connection has no encryption

### Phase 2: Completed ✅
- ✅ **TLS Certificate Dialog** - Modal dialog displays certificate details on validation failure
  - User-friendly certificate information: Subject DN, Issuer DN, validity dates, serial number
  - SHA-256 and SHA-1 fingerprints in colon-separated hex format
  - Warning banner explains untrusted certificate situation
  - Two action buttons: "Cancel" dismisses dialog, "Import and Trust" adds cert to truststore
  
- ✅ **Certificate Import Workflow** - Seamless import from connection test failure
  - Automatic certificate retrieval from failed SSL handshake
  - Custom `CertificateValidationException` carries certificate chain through exception stack
  - Enhanced `TrustStoreTrustManager` captures failed certificate for later retrieval
  - Server-specific tracking: `LdapService` maps trust managers by server name
  - Unique alias generation: "serverName", "serverName_2", etc. to avoid conflicts
  
- ✅ **Connection Test Integration** - Certificate dialog appears on validation failure
  - Catches `CertificateValidationException` separately from other connection errors
  - Displays certificate details in dialog for user review
  - Success notification after import with suggestion to retry connection
  - Automatic cleanup of failure info after successful import

### Technical Implementation (Phase 1)

**ServerView.java Updates:**
```java
// Added checkbox with descriptive label
Checkbox validateCertificateCheckbox = new Checkbox("Validate Certificate (recommended for production)");

// Conditional enabling logic
Runnable updateValidateCertState = () -> {
    boolean hasSecurity = useSslCheckbox.getValue() || useStartTlsCheckbox.getValue();
    validateCertificateCheckbox.setEnabled(hasSecurity);
    if (!hasSecurity) {
        validateCertificateCheckbox.setValue(false);
    }
};

// Value change listeners on SSL and StartTLS checkboxes
useSslCheckbox.addValueChangeListener(e -> updateValidateCertState.run());
useStartTlsCheckbox.addValueChangeListener(e -> updateValidateCertState.run());

// Binder binding
binder.bind(validateCertificateCheckbox,
    LdapServerConfig::isValidateCertificate, LdapServerConfig::setValidateCertificate);

// State initialization after loading config
binder.readBean(editConfig);
updateValidateCertState.run();
```

**User Interface Behavior:**
- **Default State:** When SSL or StartTLS is checked, validate checkbox is enabled and defaults to checked
- **Security Disabled:** When neither SSL nor StartTLS is selected:
  - Validate checkbox becomes disabled (grayed out)
  - Validate checkbox is automatically unchecked
  - User cannot enable validation without SSL/StartTLS
- **Edit Mode:** When editing existing server:
  - Checkbox state matches saved `validateCertificate` value
  - Enabling logic still applies (disabled if no security)

**Why Conditional Enabling:**
Certificate validation only makes sense when the connection uses SSL/TLS encryption. Without SSL or StartTLS:
- No certificate exchange occurs
- No validation possible
- Checkbox should be disabled to avoid user confusion

**Files Modified:**
- `ServerView.java` - Added validateCertificateCheckbox and conditional enabling logic

### Technical Implementation (Phase 2)

**New Components:**

**TlsCertificateDialog.java** (268 lines):
- Modal dialog for displaying untrusted certificate details
- Certificate details display:
  - Subject DN (multi-line formatted)
  - Issuer DN (multi-line formatted)
  - Validity period with timezone (Not Before / Not After)
  - Serial number in hexadecimal format
  - SHA-256 fingerprint (colon-separated hex)
  - SHA-1 fingerprint (colon-separated hex)
- Action buttons:
  - "Cancel" - Closes dialog without importing
  - "Import and Trust" (primary theme) - Imports certificate to truststore
- Success callback mechanism to notify parent component after import
- Static factory method `fromPemString()` for PEM-encoded certificates
- Helper methods:
  - `formatCertificateDetails()` - Formats all certificate info for display
  - `getFingerprint()` - Calculates SHA-1 or SHA-256 fingerprint
  - `generateCertificateAlias()` - Creates unique alias like "serverName" or "serverName_2"
  - `importCertificate()` - Adds certificate to truststore and shows notification

**CertificateValidationException.java** (44 lines):
- Custom exception carrying failed certificate chain through exception stack
- Extends `Exception` with certificate chain storage
- Methods:
  - `getCertificateChain()` - Returns full certificate chain
  - `getServerCertificate()` - Returns server's certificate (first in chain)
- Used to propagate certificate details from SSL layer to UI layer

**Enhanced Components:**

**TrustStoreTrustManager.java** updates:
- Added fields for failure tracking:
  - `lastFailedChain` - Stores certificate chain when validation fails
  - `lastException` - Stores the validation exception
- Enhanced `checkServerTrusted()` method:
  - Captures failed certificate chain before throwing exception
  - Stores exception details for later retrieval
- New accessor methods:
  - `getLastFailedChain()` - Returns failed certificate chain
  - `getLastException()` - Returns validation exception
  - `clearFailureInfo()` - Clears stored failure data

**LdapService.java** updates:
- New field: `Map<String, TrustStoreTrustManager> trustManagers`
  - Tracks trust manager instances per server connection
  - Enables server-specific certificate retrieval
  - Thread-safe using `ConcurrentHashMap`
- Enhanced `createSslUtil()` method:
  - Stores trust manager with server name as key: `trustManagers.put(config.getName(), trustManager)`
  - Maintains trust manager reference for later certificate retrieval
- Modified `testConnection()` method:
  - Now throws `CertificateValidationException` in addition to `Exception`
  - Inspects `LDAPException` cause chain for SSL failures
  - Detects `SSLHandshakeException` or `CertificateException`
  - Retrieves failed certificate via `getLastFailedCertificate()`
  - Wraps certificate in `CertificateValidationException` with original message
- New helper methods:
  - `getLastFailedCertificate(String serverName)` - Retrieves server certificate from trust manager
  - `clearCertificateFailure(String serverName)` - Clears failure info after successful import

**ServerView.java** updates:
- Added dependency: `TruststoreService` (constructor-injected)
- Enhanced `testSelectedServer()` method:
  - Added separate catch block for `CertificateValidationException`
  - Calls `handleCertificateValidationFailure()` on certificate failure
  - Distinguishes certificate errors from other connection errors
- New method `handleCertificateValidationFailure()`:
  - Extracts server certificate from exception: `exception.getServerCertificate()`
  - Creates `TlsCertificateDialog` with certificate, config, and truststore service
  - Provides callback for post-import actions:
    - Clears certificate failure info: `ldapService.clearCertificateFailure(config.getName())`
    - Shows success notification suggesting connection retry
  - Opens dialog for user interaction

**Certificate Import Workflow:**
1. User clicks "Test Connection" on server with validate certificate enabled
2. SSL handshake fails due to untrusted certificate
3. `TrustStoreTrustManager.checkServerTrusted()` captures certificate chain
4. `LdapService.testConnection()` catches `LDAPException`, inspects cause for SSL failure
5. `LdapService` retrieves failed certificate from trust manager by server name
6. Throws `CertificateValidationException` with certificate included
7. `ServerView.testSelectedServer()` catches `CertificateValidationException`
8. `ServerView.handleCertificateValidationFailure()` creates `TlsCertificateDialog`
9. Dialog displays certificate details to user
10. User clicks "Import and Trust" button
11. `TlsCertificateDialog.importCertificate()` calls `truststoreService.addCertificate()`
12. Success notification shown, callback triggered
13. Callback clears failure info in `LdapService`
14. User can retry connection with newly trusted certificate

**Unique Alias Generation:**
- Base alias: Server configuration name (e.g., "Production LDAP")
- Conflict resolution: Appends "_2", "_3", etc. if alias exists
- Example sequence: "Production LDAP", "Production LDAP_2", "Production LDAP_3"
- Prevents alias collision when importing multiple certificates for same server

**Files Created:**
- `TlsCertificateDialog.java` - Certificate display and import dialog
- `CertificateValidationException.java` - Custom exception for certificate failures

**Files Modified:**
- `TrustStoreTrustManager.java` - Enhanced with failure tracking
- `LdapService.java` - Enhanced with certificate retrieval and exception detection
- `ServerView.java` - Integrated certificate dialog with connection testing

## v0.21.1 - ✅ COMPLETED - Certificate Validation Using Truststore

### Implemented Features

#### 1. **Server Configuration - Certificate Validation Option**
- ✅ Added `validateCertificate` boolean field to `LdapServerConfig` (default: `true`)
- ✅ Getter and setter methods for certificate validation control
- ✅ Updated constructor with default validation enabled
- ✅ Updated `copy()` method to preserve validation setting
- ✅ JSON serialization support for persisting validation preference

#### 2. **TrustStoreTrustManager** - Custom SSL/TLS certificate validation
- ✅ Created `TrustStoreTrustManager.java` implementing `X509TrustManager`
- ✅ Validates server certificates against trusted certs in `truststore.pfx`
- ✅ Loads truststore using password from `truststore.pin`
- ✅ Uses Java standard `TrustManagerFactory` for validation
- ✅ Comprehensive error logging for certificate validation failures
- ✅ Automatic empty truststore initialization if file doesn't exist
- ✅ Thread-safe certificate validation for concurrent LDAP connections

#### 3. **LdapService** - Integration with truststore validation
- ✅ Injected `TruststoreService` via constructor dependency injection
- ✅ New `createSslUtil()` method for conditional trust manager selection:
  - When `validateCertificate = true`: Uses `TrustStoreTrustManager` with truststore
  - When `validateCertificate = false`: Uses `TrustAllTrustManager` (no validation)
- ✅ Applied to both SSL (`isUseSsl()`) and StartTLS (`isUseStartTls()`) connections
- ✅ Fallback to `TrustAllTrustManager` if truststore loading fails
- ✅ Debug logging for validation mode and certificate status

#### 4. **TruststoreService** - Enhanced with password accessor
- ✅ Added `getTruststorePassword()` method for SSL/TLS connection use
- ✅ Returns password as `char[]` (security best practice)
- ✅ Throws `IOException` if PIN file cannot be read
- ✅ Used by `LdapService` to initialize `TrustStoreTrustManager`

### Technical Details

**Certificate Validation Flow:**
1. Server connection initiated with `LdapServerConfig`
2. `LdapService.createConnection()` calls `createSslUtil(config)`
3. If `config.isValidateCertificate()` is `true`:
   - Load truststore path and password from `TruststoreService`
   - Create `TrustStoreTrustManager` with truststore credentials
   - Certificate validation performed against trusted certs
4. If validation fails, `CertificateException` thrown with details
5. If `validateCertificate` is `false`, `TrustAllTrustManager` used (no validation)

**Security Benefits:**
- Protects against man-in-the-middle attacks on LDAP connections
- Validates server identity using trusted certificate store
- Provides user control over validation (can disable for testing/dev)
- Follows security best practices (password as char[], proper exception handling)
- Supports enterprise certificate management workflows

**Files Modified:**
- `LdapServerConfig.java` - Added validateCertificate field and accessors
- `TruststoreService.java` - Added getTruststorePassword() method
- `LdapService.java` - Added TruststoreService injection and certificate validation
- `TrustStoreTrustManager.java` - New class for certificate validation

**Future Enhancements (v0.21.2):**
- Add "Validate Certificate" checkbox to Server Add/Edit dialog UI
- Create TLS Dialog for certificate import on validation failure
- Display server certificate details when validation fails
- Auto-import and trust button for failed certificates
- Certificate alias naming based on server configuration name

### Migration Notes
- Existing LDAP server configurations automatically get `validateCertificate = true` on first load
- No user action required for existing configurations
- Certificate validation only occurs when SSL or StartTLS is enabled
- Empty truststore allows all connections to fail validation (by design)
- Populate truststore via Settings > Truststore tab before enabling validation

---

## v0.21 - ✅ COMPLETED - Settings Tab with Truststore Management

### Implemented Features

#### 1. **Centralized Settings Directory** - `~/.ldapbrowser/`
- ✅ Unified application settings directory at `~/.ldapbrowser/`
- ✅ All application data stored in single location:
  - `~/.ldapbrowser/connections.json` - LDAP server configurations
  - `~/.ldapbrowser/truststore.pfx` - Trusted certificates
  - `~/.ldapbrowser/truststore.pin` - Truststore password
- ✅ Added `ldapbrowser.settings.dir` property to `application.properties`
- ✅ Automatic directory creation on first use
- ✅ Both `ConfigurationService` and `TruststoreService` use same base directory
- ✅ Clean, organized application data structure

#### 2. **TruststoreService** - Core truststore management service
- ✅ Created `TruststoreService.java` for secure certificate management
- ✅ Automatic truststore initialization at `~/.ldapbrowser/truststore.pfx`
- ✅ Secure PIN file management at `~/.ldapbrowser/truststore.pin`
- ✅ PKCS12 keystore format for broad compatibility
- ✅ Automatic PIN generation using SecureRandom (32-byte URL-safe Base64)
- ✅ Restrictive file permissions on PIN file (Unix-like systems)
- ✅ Settings directory auto-creation with `getSettingsDir()` accessor

#### 2. **TruststoreService Operations**
- ✅ `initializeTruststoreIfNeeded()` - Creates truststore and PIN file if missing
- ✅ `listCertificates()` - Returns sorted list of certificate aliases
- ✅ `getCertificate(alias)` - Retrieves certificate by alias
- ✅ `addCertificate(alias, cert)` - Adds new trusted certificate
- ✅ `removeCertificate(alias)` - Removes certificate from truststore
- ✅ `getCertificateDetails(alias)` - Formatted X.509 certificate details
- ✅ `getTruststoreStats()` - Statistics (count, size, location)
- ✅ Comprehensive exception handling with descriptive error messages

#### 3. **SettingsView** - Settings management interface
- ✅ Created `SettingsView.java` with three-tab layout
- ✅ Proper route configuration: `/settings` with MainLayout
- ✅ TabSheet component for organized settings sections
- ✅ Responsive layout with proper sizing and spacing
- ✅ Consistent styling with rest of application

#### 4. **Truststore Tab** - Full CRUD certificate management
- ✅ **Display Features:**
  - Grid showing certificate alias, subject, and issuer
  - Informational text explaining truststore purpose
  - Live statistics display (certificate count, file size, location)
  - Auto-refresh statistics after operations
  
- ✅ **Add Certificate Dialog:**
  - Alias text field for certificate identification
  - File upload component (accepts .cer, .crt, .pem, .der)
  - Drag-and-drop support for certificate files
  - Alternative PEM paste text area (for copy/paste)
  - X.509 certificate parsing from both file and text
  - Validation and error handling
  - Success/error notifications
  
- ✅ **View Certificate Details:**
  - Modal dialog with full certificate information
  - Displays Subject, Issuer, Serial Number
  - Validity dates (Valid From/To)
  - Signature algorithm information
  - Read-only text area for detailed view
  
- ✅ **Delete Certificate:**
  - Confirmation through button click
  - Grid selection-based deletion
  - Auto-refresh after deletion
  - Success/error notifications
  
- ✅ **Grid Features:**
  - Single selection mode
  - Auto-width columns with flexible layout
  - Subject and Issuer columns with flex grow
  - Selection-dependent button states
  - Refresh button to reload from disk

#### 5. **Keystore Tab** - Placeholder for future implementation
- ✅ "Under Construction" message
- ✅ Consistent layout and styling
- ✅ Ready for future keystore management features

#### 6. **Encryption Tab** - Placeholder for future implementation
- ✅ "Under Construction" message
- ✅ Consistent layout and styling
- ✅ Ready for future encryption settings

#### 7. **Navigation Integration**
- ✅ Added Settings link to drawer navigation in MainLayout
- ✅ VaadinIcon.COG icon for Settings
- ✅ Proper import and route configuration
- ✅ Settings positioned after Export in navigation

### Technical Details

**Settings Directory Structure:**
```
~/.ldapbrowser/
├── connections.json      (LDAP server configurations)
├── truststore.pfx        (Trusted certificates keystore)
└── truststore.pin        (Truststore password)
```

**TruststoreService.java:**
- Spring `@Service` annotation for dependency injection
- PKCS12 keystore type (modern standard, replacing JKS)
- Centralized paths: `~/.ldapbrowser/truststore.pfx` and `~/.ldapbrowser/truststore.pin`
- Thread-safe operations with proper exception handling
- Certificate validation through Java security APIs
- Support for X.509 certificates (standard for SSL/TLS)
- Settings directory constant: `SETTINGS_DIR = ".ldapbrowser"`
- Auto-creates `~/.ldapbrowser/` directory if not present

**ConfigurationService.java:**
- Updated to use centralized settings directory
- Configuration path: `~/.ldapbrowser/connections.json`
- Settings directory constant: `SETTINGS_DIR = ".ldapbrowser"`
- `getSettingsDir()` method for accessing base directory
- Consistent path handling with TruststoreService

**SettingsView.java:**
- Route: `/settings` with MainLayout integration
- Uses Vaadin TabSheet for organized sections
- Dependency injection of TruststoreService
- Responsive design with proper expand/flex settings
- Memory buffer for file uploads
- Certificate factory for X.509 parsing

**Certificate Import Support:**
- Multiple formats: CER, CRT, PEM, DER
- File upload via drag-and-drop or browse
- Direct PEM text paste (-----BEGIN CERTIFICATE-----)
- CertificateFactory with X.509 type
- Handles both binary and text-encoded certificates

**Security Considerations:**
- Random PIN generation prevents guessing
- PIN file with restrictive permissions (owner read/write only)
- PKCS12 format with strong encryption
- No plaintext password storage
- Automatic initialization prevents unauthorized access

### Files Created
- `src/main/java/com/ldapbrowser/service/TruststoreService.java` - NEW (285 lines)
- `src/main/java/com/ldapbrowser/ui/views/SettingsView.java` - NEW (417 lines)

### Files Modified
- `src/main/java/com/ldapbrowser/ui/MainLayout.java` - Added Settings navigation (~5 lines)
- `src/main/java/com/ldapbrowser/service/ConfigurationService.java` - Centralized settings directory (~15 lines)
- `src/main/resources/application.properties` - Added ldapbrowser.settings.dir property
- `docs/changelog.md` - Updated with v0.21 completion details

### Build Verification
- ✅ Compiles successfully with Maven
- ✅ All imports resolved correctly
- ✅ Proper exception handling throughout
- ✅ Follows Google Java style conventions

### User Experience
- Intuitive certificate management interface
- Clear feedback through notifications
- Helpful information text and tooltips
- Drag-and-drop file upload convenience
- Alternative PEM paste for quick imports
- Statistics display for transparency
- Consistent UI patterns with rest of application

### Migration Notes
- **Settings Directory:** All application data now stored in `~/.ldapbrowser/`
- **Automatic Migration:** Directory created automatically on first use
- **No Action Required:** Services handle directory creation transparently
- **Existing Users:** If you had `connections.json` in project root or elsewhere, it will be recreated in `~/.ldapbrowser/` when you add servers

### Future Enhancements
- Integration with LdapService for SSL/TLS connections
- Automatic trust of server certificates during connection
- Certificate chain validation
- Certificate expiration warnings
- Export certificates feature
- Keystore management implementation
- Encryption settings implementation

## v0.20.1 - Updated README to current application

## v0.20 - Connection retry and stale connection recovery

## v0.19 - Implement Create view

## v0.18.3 - bug fix operational attribute logic

## v0.18.2 - Enhance copy button on entry details

## v0.18.1 - Add filter results to search view

## v0.18 - Search enhancments
  - add Return attributes dropdown and adjust grid columns for these
  - Add search from here link on entry details

## v0.17 - Schema tab optomization
  - reload schema cache
  - raw schema edit on add / edit object class / attribute types

## v0.16 - 2025-11-11 ✅ COMPLETED - Schema Aware Entry Editor

### Implemented Features

#### 1. **Per-Server Schema Caching in LdapService**
- ✅ Added `schemaCache` map to store Schema objects per server
- ✅ Schema automatically fetched and cached when connection pool is created
- ✅ `getSchema()` method now checks cache before fetching from LDAP
- ✅ New `fetchSchemaFromServer()` private method for actual LDAP retrieval
- ✅ New `refreshSchema()` method to force re-fetch from LDAP server
- ✅ New `clearSchemaCache()` and `clearAllSchemaCaches()` methods
- ✅ Schema cache automatically cleared when connection pools are closed
- ✅ Pre-fetching of schema on first connection for better performance

#### 2. **Schema-Aware Attribute Color Coding in EntryEditor**
- ✅ Added schema-based attribute classification system
- ✅ New `AttributeClassification` enum (REQUIRED, OPTIONAL, OPERATIONAL, UNKNOWN)
- ✅ New `classifyAttribute()` method that:
  - Checks if attribute is operational (schema or heuristic)
  - Gets cached schema from LdapService
  - Analyzes entry's objectClasses to determine required/optional attributes
  - Returns appropriate classification

- ✅ Updated `createAttributeNameComponent()` to color-code attributes:
  - **Red (#d32f2f)** - Required attributes (MUST attributes from objectClass)
  - **Blue (#1976d2)** - Optional attributes (MAY attributes from objectClass)
  - **Orange (#f57c00)** - Operational/system attributes
  - **Default color** - Unknown attributes (not in schema or no schema available)
  
- ✅ Added hover tooltips indicating attribute type
- ✅ Uses cached schema for performance (no repeated LDAP calls)

#### 3. **Technical Benefits**
- **Performance**: Schema fetched once per server and cached in memory
- **Consistency**: All UI components can use same cached schema
- **Accuracy**: Color coding based on actual LDAP schema, not heuristics
- **User Experience**: Visual indication of attribute requirements
- **Extensibility**: Schema cache available for future schema-aware dialogs

### Technical Details

**LdapService.java modifications:**
- Added `schemaCache` field: `Map<String, Schema>`
- Modified `getConnectionPool()` to pre-fetch schema on new connections
- Modified `getSchema()` to check cache first
- Added `fetchSchemaFromServer()` for actual retrieval logic
- Added `refreshSchema()` for cache refresh
- Added cache management methods
- Modified `closeConnectionPool()` and `closeAllConnectionPools()` to clear caches

**EntryEditor.java modifications:**
- Added imports for Schema, AttributeTypeDefinition, ObjectClassDefinition
- Added `AttributeClassification` enum
- Added `classifyAttribute()` method (~60 lines)
- Modified `createAttributeNameComponent()` to use schema classification
- Color coding based on attribute role in schema

**Schema Classification Logic:**
1. Check if operational attribute (heuristic or schema)
2. Retrieve cached schema from LdapService
3. Get entry's objectClass values
4. Build sets of MUST and MAY attributes from all objectClasses
5. Classify attribute as REQUIRED, OPTIONAL, OPERATIONAL, or UNKNOWN

### Files Modified
- `src/main/java/com/ldapbrowser/service/LdapService.java` - Schema caching (~120 lines changed/added)
- `src/main/java/com/ldapbrowser/ui/components/EntryEditor.java` - Schema-aware coloring (~100 lines changed/added)
- `docs/changelog.md` - Updated with v0.16 completion details

### Build Verification
- ✅ Compiles successfully with Maven
- ✅ 0 new Checkstyle violations
- ✅ All imports resolved correctly
- ✅ Proper exception handling throughout
- ✅ Follows Google Java style conventions

### Future Enhancements
- Schema-aware attribute editor dialogs (validation, syntax checking)
- Schema-aware entry creation wizard
- Attribute auto-completion based on objectClass schema
- Syntax-specific value editors (DN picker, date picker, etc.)

## V0.15.1 - Edit Search bug

## v0.15 - Search / browse UI improvements

## v0.14 - 2025-11-11 ✅ COMPLETED - Multi Browser Tab Support

### Implemented Features

#### 1. **UI-Scoped State Management** - Phase 1 Implementation
- ✅ Converted from VaadinSession-scoped to UI-scoped state management
- ✅ Each browser tab maintains its own independent server selection
- ✅ State isolation prevents bleedover between tabs
- ✅ Backward-compatible implementation using UI-specific session keys

#### 2. **Technical Changes**

**MainLayout.java:**
- ✅ Added `getSelectedServersKey()` helper method
  - Generates UI-specific keys: `selectedServers_<UIId>`
  - Ensures each browser tab has isolated state
  
- ✅ Modified `updateSelection()` method
  - Uses UI-scoped key via `getSelectedServersKey()`
  - Stores selection per UI instance
  
- ✅ Modified `refreshServerList()` method
  - Restores selection using UI-scoped key
  - Preserves tab-specific state on refresh
  
- ✅ Modified static `getSelectedServers()` method
  - Retrieves selection using UI-scoped key
  - Returns empty set if no selection for current UI

- ✅ Added UI detach listener for cleanup
  - Automatically removes UI-scoped state when tab closes
  - Prevents memory leaks from abandoned sessions

#### 3. **Benefits**
- Each browser tab operates independently
- No more shared state between tabs
- Server selection in one tab doesn't affect others
- Clean session management with automatic cleanup
- Foundation for Phase 2 (URL parameter sync) and Phase 3 (browser history)

### Technical Details

**Key Pattern:**
```java
String uiKey = SELECTED_SERVERS_KEY + "_" + UI.getCurrent().getUIId();
```

**State Storage:**
- Still uses VaadinSession for storage
- Key differentiation provides isolation
- Each UI (tab) gets unique key

**Files Modified:**
- `src/main/java/com/ldapbrowser/ui/MainLayout.java` - UI-scoped state management (~40 lines changed)
- `docs/changelog.md` - Updated with v0.14 completion details

### Build Verification
- ✅ Compiles successfully with Maven
- ✅ 0 new Checkstyle violations
- ✅ All imports resolved correctly
- ✅ Proper exception handling throughout
- ✅ Follows Google Java style conventions

### Future Enhancements (Phase 2 & 3)
- URL parameter synchronization for bookmarkable server selections
- Browser back/forward button support
- Page refresh state preservation
- @PreserveOnRefresh annotation per view

## v0.13 - Private naming contexts added to tree grid

## v0.12 - Updated drawer to use SideNav Vaadin component

## v0.11 - Export tab

## v0.10.3 - 2025-11-08 ✅ COMPLETED - Bulk Tab - Group Memberships sub tab

### Implemented Features

#### 1. **Bulk Group Memberships Tab** - Add/remove users to/from LDAP groups
- ✅ Integrated `BulkGroupMembershipsTab.java` from LDAPBrowser project with full adaptation:
  - Package updated from `com.ldapweb.ldapbrowser` to `com.ldapbrowser`
  - Multi-server support - operations execute against all selected servers
  - Results aggregation: shows total successes/errors across all servers
  - Per-server error logging and reporting
  
- ✅ **Group Membership Configuration:**
  - Group Name (cn) field - specify the target group
  - User Base DN with LdapTreeBrowser dialog for easy DN selection
  - Group Base DN with LdapTreeBrowser dialog for easy DN selection
  - Operation selector: Add Members or Remove Members
  - User list text area - enter user IDs (UIDs) one per line
  - File upload - upload text file with user IDs
  - Continue on error option
  - Permissive modify request control option
  
- ✅ **Multi-Server Execution:**
  - Iterates through all selected servers from MainLayout
  - Aggregates results: "X successes and Y errors across N server(s)"
  - Per-server logging of operations via LoggingService
  - Error isolation - continues processing remaining servers on failure
  
- ✅ **Group Type Support:**
  - posixGroup - modifies memberUid attribute
  - groupOfNames - modifies member attribute (DN-based)
  - groupOfUniqueNames - modifies uniqueMember attribute (DN-based)
  - groupOfUrls - modifies attributes based on memberURL filter
  
- ✅ **User Validation:**
  - Validates all users exist before modification
  - Searches for users in specified base DN
  - Obtains exact UIDs and DNs
  - Reports users not found
  - Reports multiple users with same UID (error)
  
- ✅ **User Interface:**
  - Progress indicator during processing
  - Real-time status updates during validation and processing
  - Success/Error/Info notifications with detailed messages
  - Error report generation for failures
  
- ✅ **DN Selection with LdapTreeBrowser:**
  - Browse buttons next to User Base DN and Group Base DN fields
  - Modal dialog with full LDAP tree navigation
  - Selection automatically populates the DN text field
  - Uses first selected server for tree browsing

#### 2. **Integration with BulkView**
- ✅ Added `BulkGroupMembershipsTab` as fourth tab (after Import, Search, Generate)
- ✅ Wired to `updateTabServers()` for automatic server configuration updates
- ✅ Replaced placeholder tab with fully functional implementation
- ✅ Proper Spring dependency injection with field initialization

### Technical Details

**Components Modified:**
- `BulkView.java`
  - Added `BulkGroupMembershipsTab` field and instantiation
  - Added to TabSheet as "Group Memberships" tab
  - Wired to `updateTabServers()` method
  - Removed unused `createPlaceholder()` method
  - Removed unused `Paragraph` import

**Components Added:**
- `BulkGroupMembershipsTab.java` (817 lines)
  - Multi-server support with `List<LdapServerConfig>`
  - `setServerConfigs(List<LdapServerConfig>)` method for server configuration
  - `performBulkGroupOperation()` - main orchestration with multi-server iteration
  - `processBulkGroupMembership(serverConfig, ...)` - per-server processing returning int[3]
  - `processPosixGroup(serverConfig, ...)` - handles posixGroup modifications
  - `processGroupOfNames(serverConfig, ...)` - handles groupOfNames modifications
  - `processGroupOfUniqueNames(serverConfig, ...)` - handles groupOfUniqueNames modifications
  - `processGroupOfUrls(serverConfig, ...)` - handles groupOfUrls (dynamic groups)
  - `showDnBrowserDialog(TextField)` - DN selection with LdapTreeBrowser
  - Comprehensive error handling and logging

**LDAP Operations:**
- Uses `ldapService.search(config, baseDn, filter, scope, attributes)` for searches
- Uses `ldapService.modifyEntry(config, dn, modifications, controls)` for modifications
- Each operation executed per server in the selected server list
- Supports PermissiveModifyRequestControl (OID 1.2.840.113556.1.4.1413)

**Multi-Server Pattern:**
- All operations execute against every selected server
- Results show: "X successes and Y errors across N server(s)"
- Per-server error details logged when failures occur
- Each server logs operations independently via LoggingService
- Error isolation - continues processing remaining servers on failure

### Files Modified
- `src/main/java/com/ldapbrowser/ui/views/BulkView.java` - Added Group Memberships tab (~15 lines changed)
- `src/main/java/com/ldapbrowser/ui/components/BulkGroupMembershipsTab.java` - NEW - Full implementation (817 lines)
- `docs/changelog.md` - Updated with v0.10.3 completion details

### Build Verification
- ✅ Compiles successfully with Maven
- ✅ All imports resolved correctly
- ✅ Proper exception handling throughout
- ✅ Follows Google Java style conventions

## v0.10.2 - 2025-11-08 ✅ COMPLETED - Bulk Tab - Generate sub tab

### Implemented Features

#### 1. **Bulk Generate Tab** - Generate and import LDAP entries using templates
- ✅ Integrated `BulkGenerateTab.java` from LDAPBrowser project with full adaptation:
  - Package updated from `com.ldapweb.ldapbrowser` to `com.ldapbrowser`
  - Changed from single `LdapServerConfig` to `List<LdapServerConfig>` for multi-server
  - Updated all LDAP operations to use config object instead of ID
  - Fixed imports to match this project's structure
  - Results aggregation: shows total successes/errors across all servers
  
- ✅ **Entry Generation Configuration:**
  - Count Start field - starting number for {COUNT} placeholder (default: 1)
  - Count End field - ending number for {COUNT} placeholder (default: 100)
  - LDIF Template textarea - template with {COUNT} placeholder for generation
  - Real-time LDIF Preview - shows first 3 generated entries as preview
  
- ✅ **Template Processing:**
  - Uses {COUNT} placeholder in LDIF templates
  - Supports ADD, MODIFY, and DELETE LDIF change types
  - Processes each count value from start to end
  - Executes generated LDIF against all selected LDAP servers
  
- ✅ **Multi-Server Execution:**
  - Iterates through all selected servers from MainLayout
  - Performs bulk generation for each server independently
  - Aggregates results: "X successes and Y errors across N server(s)"
  - Per-server error reporting when failures occur
  - Continues processing on error (error isolation)
  
- ✅ **User Interface:**
  - Progress indicator during generation and import
  - Default template for creating user entries
  - Step buttons on count fields for easy adjustment
  - Load button to execute bulk generation
  - Success/Error/Info notifications with detailed messages

#### 2. **Integration with BulkView**
- ✅ Added `BulkGenerateTab` as third tab (after Import and Search)
- ✅ Wired to `updateTabServers()` for automatic server configuration updates
- ✅ Replaced placeholder "Generate" tab with fully functional implementation
- ✅ Proper Spring dependency injection with `@SpringComponent` and `@UIScope`

### Technical Details

**Components Modified:**
- `BulkView.java`
  - Added `BulkGenerateTab` field and instantiation
  - Added import for `BulkGenerateTab`
  - Updated `updateTabServers()` to include `generateTab.setServerConfigs()`
  - Replaced placeholder with functional tab

**Components Added:**
- `BulkGenerateTab.java` (379 lines)
  - Multi-server support with `List<LdapServerConfig>`
  - Real-time preview generation using `updatePreview()`
  - UnboundID LDAP SDK for LDIF parsing
  - Support for ADD/MODIFY/DELETE operations
  - Comprehensive error handling and logging

**LDAP Operations:**
- Uses `ldapService.addEntry(config, entry)` for ADD operations
- Uses `ldapService.modifyEntry(config, dn, modifications)` for MODIFY operations
- Uses `ldapService.deleteEntry(config, dn)` for DELETE operations
- Each operation executed per server in the selected server list

**Multi-Server Pattern:**
- All operations execute against every selected server
- Results show: "X successes and Y errors across N server(s)"
- Per-server error details displayed when failures occur
- Each server logs operations independently via LoggingService
- Error isolation - continues processing remaining entries on failure

### Files Modified
- `src/main/java/com/ldapbrowser/ui/views/BulkView.java` - Added Generate tab (~10 lines)
- `src/main/java/com/ldapbrowser/ui/components/BulkGenerateTab.java` - NEW - Full implementation (379 lines)
- `docs/changelog.md` - Updated with v0.10.2 completion details

### Build Verification
- ✅ Compiles successfully with Maven
- ✅ 0 Checkstyle violations
- ✅ All imports resolved correctly
- ✅ Proper exception handling throughout
- ✅ Follows Google Java style conventions

## v0.10.1 - 2025-11-08 ✅ COMPLETED - Bulk Tab - Search sub tab

### Implemented Features

#### 1. **Bulk Search Tab** - Perform bulk LDAP search operations
- ✅ Integrated `BulkSearchTab.java` from LDAPBrowser project with full adaptation:
  - Package updated from `com.ldapweb.ldapbrowser` to `com.ldapbrowser`
  - Changed from single `LdapServerConfig` to `List<LdapServerConfig>` for multi-server
  - Replaced `DnSelectorField` with `TextField` + browse button using `LdapTreeBrowser`
  - Updated all LDAP operations to iterate through selected servers
  - Results aggregation: shows total successes/errors across all servers
  
- ✅ **Search Configuration:**
  - Search base DN with tree browser dialog for easy DN selection
  - LDAP filter input
  - Search scope selector (BASE, ONE, SUB, CHILDREN)
  - Target attribute selection for operations
  
- ✅ **Two Operation Modes:**
  
  **Preview Mode** (default):
  - Generates LDIF representation of search results from all servers
  - Shows entries with server name headers for multi-server scenarios
  - LDIF includes DN and selected attributes for each entry
  - No modifications are made to LDAP
  
  **Execute Changes Mode** (checkbox enabled):
  - Performs actual LDAP operations on search results
  - Supports LDIF templates with variable substitution
  - Operation types:
    - Modify: Apply LDIF modifications to matching entries
    - Add: Create new entries using LDIF template
    - Delete: Remove matching entries (no template needed)
  - Template variables:
    - {DN} - Full DN of the search result entry
    - {ATTRIBUTE:attributeName} - Value of any attribute from search result
    - Example: Replace attribute with value from another attribute
  - Optional LDAP controls:
    - No Operation control (OID 1.3.6.1.4.1.4203.1.10.2) for validation
    - Permissive Modify control (OID 1.2.840.113556.1.4.1413) for fault tolerance
  - Continue on error option for processing all entries despite failures
  - Results show: "X successes and Y errors across N server(s)"
  
- ✅ **Integration with BulkView:**
  - Added to TabSheet as second tab alongside Import
  - Receives server configuration updates via `setServerConfigs()`
  - Proper instantiation with Spring dependency injection
  - `updateTabServers()` method now updates both Import and Search tabs

#### 2. **Technical Implementation Details**
- ✅ All LDAP method calls corrected:
  - `search(config, baseDn, filter, scope)` for LDAP searches
  - `modifyEntry(config, dn, modifications, controls)` for modifications with optional controls
  - `addEntry(config, entry)` for adding entries
  - `deleteEntry(config, dn)` for deletions
- ✅ LdapTreeBrowser integration:
  - Constructor: `new LdapTreeBrowser(ldapService)`
  - Server configuration: `browser.setServerConfig(config)`
  - Selection listener: `browser.addSelectionListener(event -> targetField.setValue(event.getSelectedDn()))`
- ✅ Results textarea displays combined LDIF output with server headers
- ✅ Error handling and logging for all operations
- ✅ Proper validation of required fields before execution

## v0.10 - 2025-11-07 ✅ COMPLETED - Bulk Tab

### Implemented Features

#### 1. **Bulk Operations View** - Tabbed interface for bulk LDAP operations
- ✅ Created `BulkView` with `TabSheet` containing 4 sub-tabs:
  - Import (fully functional)
  - Search (placeholder - under construction)
  - Generate (placeholder - under construction)
  - Group Memberships (placeholder - under construction)
- ✅ Multi-server support - operations execute against all selected servers from MainLayout
- ✅ Follows same pattern as `AccessView` for server selection integration
- ✅ Proper Spring dependency injection with `@SpringComponent` and `@UIScope`

#### 2. **Import Tab** - Import LDAP data from LDIF and CSV files
- ✅ Integrated `ImportTab.java` from LDAPBrowser project with full adaptation:
  - Package updated from `com.ldapweb.ldapbrowser` to `com.ldapbrowser`
  - Changed from single `LdapServerConfig` to `List<LdapServerConfig>` for multi-server
  - Added `setServerConfigs(List<LdapServerConfig>)` method for server configuration
  - Updated all LDAP operations to iterate through selected servers
  - Results aggregation: shows total successes/errors across all servers
  
- ✅ **LDIF Import Mode:**
  - File upload or text input for LDIF content
  - Supports ADD, MODIFY, DELETE change types
  - Optional LDAP controls:
    - No Operation control (OID 1.3.6.1.4.1.4203.1.10.2) for validation
    - Permissive Modify control (OID 1.2.840.113556.1.4.1413) for fault tolerance
  - Continue on error option for processing all entries despite failures
  - Per-server error reporting in results

- ✅ **CSV Import with LDIF Templates:**
  - Upload CSV file with data
  - Create LDIF template with {C1}, {C2}, {DN} variable substitution
  - Preview CSV data in grid (supports any number of columns as C1, C2, C3, etc.)
  - Two DN resolution methods:
    - CSV Column: Use first column value as DN directly
    - LDAP Search: Search for entry matching filter and use result DN
  - Template generates LDIF for each CSV row
  - Same control support as LDIF mode (No Operation, Permissive Modify)
  - Continue on error option
  - Per-server and per-row error reporting

#### 3. **Service Layer Enhancements**
- ✅ **LdapService.java** additions:
  - `addEntry(LdapServerConfig config, LdapEntry entry)` - Creates new LDAP entries
  - `modifyEntry(LdapServerConfig config, String dn, List<Modification> modifications, Control... controls)` - Modifies entries with optional LDAP controls
  - `isControlSupported(LdapServerConfig config, String controlOid)` - Overload accepting config object
  
- ✅ **LoggingService.java** additions:
  - `logImport(String serverName, String source, int entriesProcessed)` - Logs import operations
  - `logWarning(String category, String message)` - Logs warning-level messages

### Technical Details

**Components Added:**
- `BulkView.java`
  - TabSheet with 4 tabs (Import active, others placeholder)
  - Constructor injection: `ConfigurationService`, `ImportTab`
  - `updateImportTabServers()` method syncs selected servers from MainLayout
  - `getSelectedServers()` method filters configurations by MainLayout selection
  - `createPlaceholder()` helper for under-construction tabs

- `ImportTab.java` (997 lines)
  - `@SpringComponent` and `@UIScope` annotations for Vaadin DI
  - Three import modes: LDIF File, LDIF Text, CSV Template
  - File upload components using `MemoryBuffer`
  - CSV parsing with dynamic column detection
  - LDIF template variable substitution engine
  - UnboundID `LDIFReader` and `LDIFChangeRecord` processing
  - Multi-server iteration with aggregated results
  - Progress indicators during import operations
  - Comprehensive error handling and user feedback

**LDAP Features:**
- Uses UnboundID LDAP SDK for LDIF parsing and LDAP controls
- Support for ADD/MODIFY/DELETE change types from LDIF
- DN resolution via direct CSV column or LDAP search
- Control validation before use (checks server support)
- Batch processing with error isolation (continue on error)

**Multi-Server Pattern:**
- All import operations execute against every selected server
- Results show: "X successes and Y errors across N server(s)"
- Per-server error details displayed when failures occur
- Each server logs operations independently via LoggingService

## v0.9.3 - 2025-11-06 ✅ COMPLETED - Access / Effective Rights Sub-Tab

### Implemented Features

#### 1. **Effective Rights Tab** - Check access rights using GetEffectiveRightsRequestControl
- ✅ Integrated `EffectiveRightsTab.java` from LDAPBrowser project
- ✅ Updated to use `LdapTreeBrowser` for DN selection (Browse buttons)
- ✅ Multi-server support - searches all selected servers
- ✅ Added Server column as first column in results grid
- ✅ Form fields:
  - Search Base with Browse button for tree navigation
  - Search Scope (Base, One Level, Subtree)
  - Search Filter with default `(objectClass=*)`
  - Attributes (all or comma-separated list)
  - Effective Rights For DN with Browse button
  - Search Size Limit (default 100)
- ✅ Results display:
  - Server name (for multi-server identification)
  - Entry DN
  - Attribute Rights (read, write, selfwrite_add, selfwrite_delete, compare, search)
  - Entry Rights (add, delete, read, write, proxy)
- ✅ Proper error handling for:
  - Unsupported GetEffectiveRightsRequestControl
  - Connection failures
  - Size limit exceeded
  - Invalid DNs

### Technical Details

**Components Added:**
- `EffectiveRightsTab.java`
  - Package updated from `com.ldapweb.ldapbrowser` to `com.ldapbrowser`
  - Replaced `DnSelectorField` with `TextField` + Browse `Button` pattern
  - Added `showDnBrowserDialog(TextField)` method using `LdapTreeBrowser`
  - Updated from single `LdapServerConfig` to `Set<LdapServerConfig>`
  - Added `setSelectedServers(Set<LdapServerConfig>)` method
  - `performSearch()` iterates through all selected servers
  - `searchEffectiveRights()` accepts `LdapServerConfig` parameter
  - Added `serverName` field to `EffectiveRightsResult` inner class
  - Grid columns: Server (sortable), Entry DN, Attribute Rights, Entry Rights
  - Fixed method names: `isUseSsl()`, `isUseStartTls()`, `getBindPassword()`

**Components Modified:**
- `AccessView.java`
  - Added `EffectiveRightsTab` field and instantiation
  - Enabled "Effective Rights" tab (previously disabled placeholder)
  - Added tab selection case for `effectiveRightsTabItem`
  - Added `updateEffectiveRightsTabServers()` method
  - Properly wired to selected servers from `MainLayout`

**LDAP Features:**
- Uses UnboundID LDAP SDK `GetEffectiveRightsRequestControl` (OID 1.3.6.1.4.1.42.2.27.9.5.2)
- Processes `EffectiveRightsEntry` for rights information
- Handles `AttributeRight` and `EntryRight` enumerations
- Supports both SSL and StartTLS connections
- Direct LDAP connection management for control-based searches

**Multi-Server Pattern:**
- Follows same pattern as `GlobalAccessControlTab` and `EntryAccessControlTab`
- Aggregates results from all selected servers
- Server name displayed in first column for identification
- Proper error logging per server without stopping entire search

## v0.9.2 - 2025-11-05 ✅ COMPLETED - Access / Entry Access Control Enhancement

### Implemented Features

#### 1. **Fixed ACI Deletion Bug** - Delete only the selected ACI attribute value
- ✅ Modified `deleteAci` method in `EntryAccessControlTab`
- ✅ Reads current entry with operational attributes to get all ACI values
- ✅ Removes only the specific ACI value from the list
- ✅ Updates entry with remaining ACI values using `modifyAttribute`
- ✅ Deletes entire aci attribute if no values remain
- ✅ Proper error handling for entry not found, no ACI attributes, value not found

#### 2. **Comprehensive ACI Builder Dialog** - Visual ACI construction tool
- ✅ Integrated `AciBuilderDialog.java` from LDAPBrowser project
- ✅ "Build ACI" button on "Add Access Control Instructions" dialog
- ✅ "Build ACI" button on "Edit Access Control Instruction" dialog  
- ✅ Support for all ACI components:
  - Target types: target DN, targetattr, targetfilter, targettrfilters, extop, targetcontrol, requestcriteria, targetscope
  - Permissions: read, write, add, delete, search, compare, selfwrite, proxy, all
  - Bind rules: userdn, groupdn, roledn, authmethod, ip, dns with negation support
- ✅ Real-time ACI preview as components are configured
- ✅ Populate from existing ACI for editing
- ✅ Schema-aware attribute selection from LDAP server
- ✅ RootDSE integration for supported controls and extended operations

#### 3. **DN Selection with Tree Browser** - Enhanced DN field navigation
- ✅ Added `LdapTreeBrowser` integration for DN field selection
- ✅ Browse button next to Target DN fields in ACI Builder
- ✅ Browse button next to Bind Rule DN fields (userdn, groupdn, roledn)
- ✅ Modal dialog with full LDAP tree navigation
- ✅ Selection automatically populates the DN text field

#### 4. **LDAP Service Enhancement** - Added getRootDSE method
- ✅ New `getRootDSE(LdapServerConfig)` method in `LdapService`
- ✅ Returns RootDSE with server capabilities information
- ✅ Used by ACI Builder for controls and extended operations
- ✅ Proper exception handling for LDAP and SSL/TLS errors

### Technical Details

**Components Modified:**
- `EntryAccessControlTab.java`
  - Fixed deleteAci to use `readEntry(config, dn, true)` for operational attributes
  - Enabled "Build ACI" button and wired to `AciBuilderDialog`
  - Added `openAciBuilder()` method to launch dialog with pre-population support

**Components Added:**
- `AciBuilderDialog.java` (1,300+ lines)
  - Full visual ACI builder with all PingDirectory ACI syntax support
  - Multiple target components with dynamic controls based on type
  - Multiple bind rule components with type-specific fields
  - Real-time preview with syntax highlighting
  - DN browser integration for DN-type fields
  - Schema integration for attribute selection
  - RootDSE integration for controls and extended operations

**Service Enhanced:**
- `LdapService.java`
  - Added `getRootDSE(LdapServerConfig)` method
  - Returns `RootDSE` object with server information
  - Enables ACI Builder to query server capabilities

### Files Modified
- `src/main/java/com/ldapbrowser/ui/components/EntryAccessControlTab.java` - ACI deletion fix and builder integration (~50 lines)
- `src/main/java/com/ldapbrowser/ui/components/AciBuilderDialog.java` - NEW - Full ACI builder (1,300+ lines)
- `src/main/java/com/ldapbrowser/service/LdapService.java` - Added getRootDSE method (~15 lines)
- `docs/changelog.md` - Updated with v0.9.2 completion details

### Build Verification
- ✅ Compiles successfully with Maven
- ✅ 0 Checkstyle violations
- ✅ All imports resolved correctly
- ✅ Proper exception handling throughout
- ✅ Follows Google Java style conventions

## v0.9.1 - Access / Entry Access Control sub tab
  - implement Entry Access Control sub tab features
    - main Entry Access Control tab
    - refresh button
    - Add New ACI - under construction
    - search
    - display grid
    - details pane
    - details edit - under construction
    - details delete - under construction

## v0.9 - 2025-11-02 ✅ COMPLETED - Access Tab - Global Access Control

### Implemented Features
- ✅ **Global Access Control Tab** - Read-only viewer for LDAP global ACIs
  - Displays global Access Control Instructions from `cn=Access Control Handler,cn=config`
  - Reads `ds-cfg-global-aci` attribute from each selected LDAP server
  - Split-pane layout with ACI list on left (60%) and details panel on right (40%)
  - Grid display with sortable columns:
    - Name - ACI description/name
    - Server - Origin server name
    - Resources - Target resources (attributes, entries, scope, filters)
    - Rights - Allow/Deny with permissions
    - Clients - Bind rules (users, groups, IP, auth methods)
  - Real-time search/filter across all ACIs by name or content
  - Detailed ACI view panel showing:
    - Parsed components (Resources, Rights, Clients)
    - Raw ACI definition in read-only text area
  - Refresh button to reload ACIs from all selected servers
  - Multi-server support - aggregates ACIs from all selected servers
  
- ✅ **Access View Structure**
  - Tabbed interface with three tabs:
    - Global Access Control (active and functional)
    - Effective Rights (placeholder, disabled)
    - ACI Editor (placeholder, disabled)
  - Follows same pattern as SchemaView for consistency
  - Automatically refreshes when servers are selected/deselected
  
- ✅ **Integration with Existing Components**
  - Uses existing `AciParser` utility for parsing ACI syntax
  - Integrates with `LdapService` for LDAP operations
  - Works with `ConfigurationService` for server configuration
  - Respects multi-server selection from navbar

### Technical Details
- **Component Created**: `GlobalAccessControlTab.java`
  - Extends `VerticalLayout`
  - Inner class `GlobalAciInfo` wraps parsed ACI data with server name
  - Uses `SplitLayout` for responsive two-pane design
- **Component Modified**: `AccessView.java`
  - Converted from placeholder to functional tabbed view
  - Implements server selection tracking
  - Prepares structure for future Effective Rights and ACI Editor tabs
- **LDAP Operations**: Uses base scope search on `cn=Access Control Handler,cn=config`
- **Code Style**: Follows Google Java style, passes Checkstyle validation

## v0.8.2 - Use OidLookupTable to retreive OID for all extended operations

## v0.8.1 - 2025-10-30 ✅ COMPLETED - Server Management Enhancement

### Implemented Features
- ✅ **Redesigned Server View** - Complete overhaul of server management interface
  - Replaced form-based layout with grid-based display
  - Action buttons prominently displayed at top (Add Server, Edit, Copy, Delete, Test)
  - Servers displayed in sortable, resizable grid with columns:
    - Name - Server configuration name
    - Host:Port - Combined host and port display
    - Bind DN - Distinguished name for binding
    - Security - Shows SSL/TLS, StartTLS, or None
  
- ✅ **Dialog-Based Server Management**
  - Add Server button opens dialog for creating new configurations
  - Edit button opens dialog with selected server details
  - Copy button duplicates selected server for quick configuration cloning
  - Delete button with confirmation dialog prevents accidental deletion
  - Test button validates connection for selected server
  
- ✅ **Server Configuration Dialog Features**
  - Modal dialog with professional layout
  - All fields from original form (Name, Host, Port, Base DN, Bind DN, Password)
  - SSL and StartTLS checkboxes with mutual exclusivity
  - Automatic port adjustment (389 for standard, 636 for SSL)
  - Form validation with required field checking
  - Save and Cancel buttons
  
- ✅ **Enhanced User Experience**
  - Grid selection enables/disables action buttons appropriately
  - Single click to select server, double click support maintained
  - Visual feedback for security settings in grid
  - Automatic navbar refresh when servers are added/edited/deleted
  - Professional, clean layout following Vaadin Lumo theme
  - Consistent with other views (Search, Browse, Schema)
  
- ✅ **Preserved Functionality**
  - All original features retained (connection testing, SSL/TLS support)
  - ConfigurationService integration unchanged
  - LdapService integration unchanged
  - JSON persistence to `~/.ldapbrowser/connections.json`
  - Session-based server selection in navbar

### Technical Details
- **Component Updates**: Complete rewrite of `ServerView.java`
- **Grid Implementation**: Vaadin Grid component with custom columns
- **Dialog Pattern**: Modal dialogs for all server CRUD operations
- **Data Binding**: Binder pattern for form validation maintained
- **Code Reduction**: Eliminated redundant field variables, simplified logic
- **Responsive Design**: Grid and dialogs adapt to viewport size

### Files Modified
- `src/main/java/com/ldapbrowser/ui/views/ServerView.java` - Complete rewrite (~387 lines)

### Build Verification
- ✅ `mvn clean compile` - Successful (26 source files)
- ✅ `mvn checkstyle:check` - 0 violations
- ✅ All components compile without errors

### User Experience Improvements
- **Better Overview**: Grid view shows all servers at once
- **Faster Workflow**: Action buttons always visible, no scrolling needed
- **Safer Operations**: Confirmation dialog for destructive actions
- **Clearer UI**: Dialog-based editing reduces visual clutter
- **Professional Appearance**: Matches modern enterprise application standards

---

## v0.8 - 2025-10-30 ✅ COMPLETED - Schema Compare Tab Implementation

### Implemented Features
- ✅ **Schema Compare Tab Component**
  - Complete implementation of schema comparison across multiple LDAP servers
  - Based on the proven GroupSchemaTab model from the LDAPBrowser GitHub repository
  - Enables side-by-side comparison of schema elements between selected servers

- ✅ **Multi-Server Schema Comparison**
  - **Dynamic server columns** - grid adapts to show one column per selected server
  - Loads and compares schemas from all selected servers simultaneously
  - Checksum-based comparison using SHA-256 for detecting differences
  - Status column showing "Equal" or "Unequal" across servers
  
  - **Comparison Features:**
    - 8-character hash display for compact comparison
    - MISSING indicator for elements not present on a server
    - ERROR indicator for servers that failed to load schema
    - Visual indication of schema consistency across infrastructure

- ✅ **Five Sub-Tabs for Schema Elements**
  - **Object Classes** - Compare class definitions, superior classes, required/optional attributes
  - **Attribute Types** - Compare attribute definitions, syntax, matching rules
  - **Matching Rules** - Compare matching rule definitions and syntax
  - **Matching Rule Use** - Compare matching rule usage and applicable attributes
  - **Syntaxes** - Compare attribute syntax definitions

- ✅ **Smart Schema Retrieval**
  - Automatic detection of Extended Schema Info control support (1.3.6.1.4.1.30221.2.5.12)
  - All-or-none decision: if any server lacks support, standard retrieval used for consistency
  - Prevents comparison mismatches due to different schema metadata
  - Logs control usage for troubleshooting

- ✅ **Canonicalization for Accurate Comparison**
  - Schema elements normalized before comparison using `SchemaCompareUtil`
  - Sorts multi-valued attributes (names, superior classes, etc.)
  - Optional extension filtering via "Ignore Extensions" checkbox
  - Removes vendor-specific variations to focus on semantic differences

- ✅ **Search and Filter**
  - Real-time search across all schema elements
  - Filters applied to all sub-tabs simultaneously
  - Search by element name

- ✅ **Detailed Comparison View**
  - Split layout with details panel below main grid
  - Select any schema element to view property-by-property comparison
  - Shows values from each server side-by-side
  - Comprehensive property display:
    - Object Classes: OID, Names, Description, Type, Obsolete, Superior Classes, Required/Optional Attributes
    - Attribute Types: OID, Names, Description, Syntax, Single Value, Usage, Matching Rules
    - Matching Rules: OID, Names, Description, Syntax
    - Matching Rule Use: OID, Names, Description, Applicable Attributes
    - Syntaxes: OID, Description

- ✅ **Supporting Infrastructure**
  - **SchemaCompareUtil** - Canonicalization utility for schema elements
  - **LoggingService** - Detailed logging for schema comparison operations
  - **LdapService enhancements**:
    - `connect(config)` - Ensures connection pool exists
    - `isControlSupported(serverId, controlOid)` - Checks LDAP control support
    - `getSchema(serverId, useExtendedControl)` - Retrieves schema with control options

- ✅ **Integration with SchemaView**
  - SchemaView updated to pass selected servers to Compare tab
  - Automatic refresh when Compare tab is selected
  - Seamless integration with existing Manage tab

### Technical Details
- **New Components**:
  - `src/main/java/com/ldapbrowser/ui/components/SchemaCompareTab.java` (~830 lines)
  - `src/main/java/com/ldapbrowser/util/SchemaCompareUtil.java` - Canonicalization utility
  - `src/main/java/com/ldapbrowser/service/LoggingService.java` - Structured logging service

- **Enhanced Components**:
  - `src/main/java/com/ldapbrowser/service/LdapService.java` - Added control checking and schema retrieval methods
  - `src/main/java/com/ldapbrowser/ui/views/SchemaView.java` - Updated to integrate Compare tab

- **Model Pattern**: Follows GroupSchemaTab.java from LDAPBrowser GitHub repository
- **Comparison Algorithm**: SHA-256 checksums of canonicalized schema definitions
- **Grid Architecture**: Dynamic column generation based on selected servers

### Build Verification
- ✅ `mvn clean compile` - Successful (26 source files)
- ✅ `mvn checkstyle:check` - 0 violations
- ✅ All components compile without errors
- ✅ Clean integration with existing Schema view structure

### User Experience Features
- **Visual Feedback**: Status label shows loading progress and completion status
- **Error Handling**: Graceful handling of server connection failures
- **Performance**: Efficient parallel schema loading from multiple servers
- **Flexibility**: Toggle extension comparison on/off
- **Detailed Logging**: DEBUG and TRACE level logging for troubleshooting
- **Professional Layout**: Consistent with Browse/Search/Schema Manage views

### Use Cases Enabled
1. **Schema Drift Detection** - Quickly identify differences between development and production LDAP servers
2. **Multi-Directory Consistency** - Ensure schema consistency across replicated or clustered LDAP environments
3. **Migration Validation** - Verify schema compatibility before migration
4. **Vendor Comparison** - Compare schema implementations across different LDAP vendors
5. **Change Auditing** - Track schema changes over time by comparing snapshots

---

## v0.7 - 2025-10-25 ✅ COMPLETED - Schema Tab Implementation

### Implemented Features
- ✅ **Schema View with Top-Level Tabs**
  - Created two-level tab structure: top-level (Manage/Compare) and sub-level tabs
  - Implemented `SchemaView` as main container with dependency injection
  - Automatic schema loading on view initialization

- ✅ **Schema Manage Tab Component**
  - Created `SchemaManageTab` component with 5 sub-tabs:
    - Object Classes
    - Attribute Types
    - Matching Rules
    - Matching Rule Use
    - Syntaxes
  
  - **Multi-Server Support:**
    - **Server column added to all grids** - displays source server for each schema element
    - Loads and aggregates schema from all selected servers simultaneously
    - Each schema element tracked with its source server via `SchemaElement` wrapper class
    - Search filtering works across server names and schema element properties
  
  - **Grid Features:**
    - Sortable and resizable columns
    - Search functionality across all schema elements
    - Refresh button to reload schemas
    - Details panel showing complete information for selected element
  
  - **Schema Element Display:**
    - Object Classes: Name, OID, Description, Type, Obsolete status, Server
    - Attribute Types: Name, OID, Description, Syntax OID, Obsolete status, Server
    - Matching Rules: Name, OID, Description, Syntax OID, Obsolete status, Server
    - Matching Rule Use: OID, Description, Obsolete status, Server
    - Syntaxes: OID, Description, Server
  
  - **Details Panel:**
    - Comprehensive details for selected schema elements
    - Shows superior classes, required/optional attributes (for object classes)
    - Displays superior type, matching rules (for attribute types)
    - Clean, labeled layout with server information prominently displayed

- ✅ **Schema Compare Tab Placeholder**
  - Created `SchemaCompareTab` component with "Under Construction" message
  - Reserved for future schema comparison functionality

- ✅ **LDAP Service Enhancement**
  - Added `getSchema(LdapServerConfig)` method to retrieve schema from servers
  - Added `isConnected(String)` method to check connection status
  - Schema retrieval uses UnboundID LDAP SDK's built-in schema support

- ✅ **Schema Element Wrapper**
  - Created `SchemaElement<T>` generic wrapper class
  - Associates any schema element type with its source server name
  - Enables multi-server schema aggregation and display

### Technical Details
- **Component Location**: 
  - `src/main/java/com/ldapbrowser/ui/views/SchemaView.java`
  - `src/main/java/com/ldapbrowser/ui/components/SchemaManageTab.java`
  - `src/main/java/com/ldapbrowser/ui/components/SchemaCompareTab.java`
  - `src/main/java/com/ldapbrowser/model/SchemaElement.java`
- **Service Enhancement**: `src/main/java/com/ldapbrowser/service/LdapService.java`
- **Model Used**: Based on SchemaBrowser.java from LDAPBrowser GitHub repository
- **Multi-Server Pattern**: Iterates through selected servers, aggregates results with server tracking

### Files Modified
- `src/main/java/com/ldapbrowser/service/LdapService.java` - Added schema retrieval methods
- `src/main/java/com/ldapbrowser/ui/views/SchemaView.java` - Complete rewrite with tabs
- **New Files Created:**
  - `src/main/java/com/ldapbrowser/ui/components/SchemaManageTab.java`
  - `src/main/java/com/ldapbrowser/ui/components/SchemaCompareTab.java`
  - `src/main/java/com/ldapbrowser/model/SchemaElement.java`

### Build Verification
- ✅ `mvn clean compile` - Successful (23 source files)
- ✅ `mvn checkstyle:check` - 0 violations
- ✅ All components compile without errors
- ✅ Multi-server schema loading implemented and tested

### User Experience Features
- **Multi-Server Awareness**: Server column in every grid shows schema element origin
- **Comprehensive Search**: Filter by server name or any schema element property
- **Two-Level Navigation**: Top tabs (Manage/Compare) and sub-tabs (schema element types)
- **Detailed Information**: Side panel with complete schema element details
- **Professional Layout**: Clean, consistent with existing Browse/Search views
- **Future Ready**: Compare tab placeholder for future schema comparison features

---

## v0.6 - 2025-10-25 ✅ COMPLETED - Shared Entry Details Component

### Implemented Features
- ✅ **AttributeEditor Component** - Comprehensive LDAP entry editing component
  - Created `AttributeEditor` as a unified, shared component for entry management
  - Based on proven model from LDAPBrowser GitHub repository
  - Complete CRUD operations for LDAP entries and attributes
  
  - **Entry Management:**
    - Full DN display with copy-to-clipboard functionality
    - Add, edit, and delete attributes with dialog-based interfaces
    - Save changes with modification tracking (pending changes indicator)
    - Delete entry with confirmation dialog
    - Refresh entry to reload from LDAP server
    - Test login functionality to verify credentials
    
  - **Attribute Display:**
    - Sortable grid with attribute name, values, and action columns
    - Multi-value attribute support (values displayed one per line)
    - Color-coded attribute names (objectClass highlighted in red)
    - Operational attributes toggle (show/hide system attributes)
    - Operational attributes marked in orange when visible
    
  - **Edit Features:**
    - Add new attributes with multi-line value input
    - Edit existing attributes with multi-line value editor
    - Delete attributes with confirmation
    - Inline action buttons (edit, delete) for each attribute
    - Automatic sorting: objectClass first, then alphabetical
    
  - **Advanced Operations:**
    - Test bind dialog to verify authentication credentials
    - Copy DN to clipboard with single click
    - Pending changes indicator on Save button (highlighted with asterisk)
    - Modification tracking to detect actual changes before saving
    - Operational attributes filtering (create*, modify*, entryUUID, etc.)

- ✅ **SearchView Integration**
  - Replaced custom entry details implementation with AttributeEditor
  - Simplified codebase by removing duplicate dialogs and methods
  - All entry editing now handled by shared component
  - Automatic server config detection from selected entry
  
- ✅ **BrowseView Integration**
  - Replaced simple EntryDetailsPanel with full-featured AttributeEditor
  - Upgraded from read-only view to full editing capabilities
  - Browse and edit entries in same interface
  - Consistent user experience across Search and Browse views

### Technical Details
- **Component Location**: `src/main/java/com/ldapbrowser/ui/components/AttributeEditor.java`
- **Code Reduction**: Eliminated ~300 lines of duplicate code from SearchView
- **API Usage**: Leverages existing LdapService methods (readEntry, addAttribute, modifyAttribute, deleteAttribute, deleteEntry, testBind)
- **Data Model**: Works with LdapEntry.getAttributes() Map API
- **Modification Detection**: Compares original vs. modified entries to generate minimal change sets
- **Error Handling**: Comprehensive try-catch blocks with user-friendly notifications

### Files Modified
- `src/main/java/com/ldapbrowser/ui/components/AttributeEditor.java` - Created new shared component
- `src/main/java/com/ldapbrowser/ui/views/SearchView.java` - Integrated AttributeEditor
- `src/main/java/com/ldapbrowser/ui/views/BrowseView.java` - Integrated AttributeEditor
- `pom.xml` - Version bumped to 0.6.0-SNAPSHOT

### Build Verification
- ✅ `mvn clean compile` - Successful (20 source files)
- ✅ `mvn checkstyle:check` - 0 violations (only style warnings)
- ✅ All components compile without errors

### User Experience Improvements
- **Consistency**: Same editing interface in both Search and Browse views
- **Full Functionality**: Both views now support complete entry management
- **Better UX**: Professional dialogs for add/edit operations with multi-line text areas
- **Visual Feedback**: Pending changes indicator, color-coded attributes, clear notifications
- **Safety**: Confirmation dialogs for destructive operations (delete attribute, delete entry)

---

## v0.5 - 2025-10-24 ✅ COMPLETED - Tree Browser Component

### Implemented Features
- ✅ **Tree Browser Component** - Hierarchical LDAP navigation
  - Created `LdapTreeGrid` component for tree-based LDAP browsing
    - Icon-based visualization for different entry types (server, domain, organization, organizational unit, person, group, default)
    - **Multi-server support:** Server names appear as top-level nodes
    - Hierarchy: Server → Root DSE → Naming Contexts → LDAP entries
    - Lazy loading with automatic child expansion
    - 100 entry per-level limit for performance
    - Determinate expander display based on entry type
  
  - Created `LdapTreeBrowser` wrapper component
    - Header with LDAP Browser title
    - Refresh button for tree reload
    - Selection event propagation
    - Support for both single-server and multi-server modes
    - Clean API for server config management
  
  - Created `EntryDetailsPanel` component
    - Displays LDAP entry attributes in sortable grid
    - DN with copy-to-clipboard functionality
    - Refresh button to reload entry from LDAP
    - Error handling for missing/invalid entries

- ✅ **Browse Page Implementation**
  - Complete `BrowseView` with split layout (25% tree, 75% details)
  - **Multi-server integration:** All selected servers automatically populate the tree
  - Server selection integration via MainLayout
  - Tree browser on left showing hierarchical LDAP data for multiple servers
  - Entry details panel on right for selected entries
  - Automatic server detection for entry details
  - Full entry load on selection with error handling

- ✅ **Search DN Selector Integration**
  - Browse button on Search Base field opens DN selector dialog
  - Dialog with tree browser for DN navigation
  - Select button to choose DN from tree
  - Selected DN automatically populates Search Base field
  - Server selection validation with user-friendly errors

- ✅ **Model Enhancements**
  - Extended `LdapEntry` with `hasChildren` and `rdn` fields
  - Added `getRdn()`, `getDisplayName()` methods for tree display
  - Updated setters for tree navigation state

- ✅ **Service Enhancements**
  - Added `browseEntries()` for one-level searches with size limit
  - Added `getNamingContexts()` for Root DSE context retrieval
  - Added `getEntryMinimal()` for lightweight entry fetches
  - Added `shouldShowExpanderForEntry()` for UI hints
  - Added `clearPagingState()` for paging cleanup

### Build Status
- ✅ Compilation successful with `mvn clean compile`
- ✅ 0 Checkstyle violations (only warnings for style suggestions)
- ✅ All components error-free

### Multi-Server Behavior
- When multiple servers are selected from the dropdown, each server appears as a top-level node in the tree
- Expanding a server node shows its Root DSE entry
- Expanding Root DSE shows Naming Contexts
- Full LDAP hierarchy browsing available under each naming context
- Entry details automatically use the correct server configuration
- **Browse page auto-refreshes** when server selection changes (1-second polling)
- **DN selector dialog** shows all selected servers with proper multi-server support

### Recent Fixes (2025-10-24)
- ✅ Fixed Browse page to auto-refresh when servers are selected/deselected (UI polling every 1 second)
- ✅ Fixed DN selector dialog to show server names as top-level nodes
- ✅ Fixed DN selector dialog closing issue when expanding Root DSE
- ✅ Added validation to prevent selecting server nodes or Root DSE as search base DN
- ✅ Dialog now modal with close-on-outside-click disabled for better UX

## v0.4 - IN PROGRESS

### Implemented Features
- ✅ **Advanced Search Filter Builder** - Professional filter construction interface
  - Created `AdvancedSearchBuilder` component based on model from LDAPBrowser project
  - Hierarchical filter structure with Filter Groups and Filter Units
  - Support for complex logical combinations (AND, OR, NOT)
  - Multiple filter groups with configurable root operator
  - Each group contains multiple filter units
  - Filter operators:
    - EQUALS, NOT_EQUALS
    - GREATER_EQUAL, LESS_EQUAL
    - STARTS_WITH, ENDS_WITH, CONTAINS
    - EXISTS, NOT_EXISTS
  - Visual filter construction with:
    - Attribute selector with common LDAP attributes
    - Custom attribute input support
    - Operator selection per filter unit
    - Value field with visibility based on operator
  - Real-time LDAP filter generation
  - Editable generated filter text area
  - Search base field with helper text
  - Add/remove filter groups and units
  - Styled with Vaadin Lumo theme
  - Integrated into SearchView as collapsible panel
  - Syncs with main search form filter field

### Future Enhancements
- Implement DN Selector Field with tree-based navigation
- Implement Browse page with Tree Navigator

## v0.3 - 2025-10-18 ✅ COMPLETED

### Implemented Features
- ✅ **Search Functionality** - Complete LDAP search interface
  - Search base field with browse button placeholder
  - LDAP filter field with manual entry
  - Search scope selector (Base, One Level, Subtree, Subordinate)
  - Search button to execute searches across selected servers
  
- ✅ **Filter Builder** - Graphical filter construction tool
  - Collapsible accordion panel
  - Operator selection (Equals, Approximately Equals, Greater/Less Than or Equal, Present, Substring)
  - Attribute and value input fields
  - Automatic LDAP filter string generation
  - Results populated into filter field
  
- ✅ **Search Results Grid** - Comprehensive results display
  - Shows server name, DN, common name, and object classes
  - Selection support to view entry details
  - Multi-server search support
  - Result count notification
  
- ✅ **Entry Details Panel** - Full entry viewing and editing
  - Split layout with results grid on left, details on right
  - Server and DN header with copy-to-clipboard button
  - Action buttons:
    - Add Attribute - Add new attributes to entry
    - Test Login - Test bind with password dialog
    - Refresh - Reload entry from LDAP
    - Delete Entry - Remove entry from directory
  - Attributes grid with:
    - Attribute name and values columns
    - Edit button for modifying attribute values
    - Delete button for removing attributes
    - Actions column for inline operations
    
- ✅ **LDAP Service Enhancements** - Extended LDAP operations
  - `search()` - Perform LDAP searches with filters and scope
  - `readEntry()` - Read entry with optional operational attributes
  - `modifyAttribute()` - Update attribute values
  - `addAttribute()` - Add new attributes
  - `deleteAttribute()` - Remove attributes
  - `deleteEntry()` - Delete LDAP entries
  - `testBind()` - Test authentication credentials
  
- ✅ **Data Models**
  - `LdapEntry` - Represents LDAP entry with attributes and operational attributes
  - `SearchFilter` - Filter builder model with operators and LDAP string generation
  
- ✅ All code follows Google Java Style conventions
- ✅ Zero Checkstyle violations
- ✅ Project compiles and builds successfully

### Files Created
- `src/main/java/com/ldapbrowser/model/LdapEntry.java` - LDAP entry model
- `src/main/java/com/ldapbrowser/model/SearchFilter.java` - Search filter builder model

### Files Modified
- `src/main/java/com/ldapbrowser/ui/views/SearchView.java` - Complete search interface implementation
- `src/main/java/com/ldapbrowser/service/LdapService.java` - Added search and entry CRUD operations
- `pom.xml` - Version bumped to 0.3.0-SNAPSHOT

### Technical Details
- **Search Operations**: Full LDAP search with configurable scope and filters
- **Multi-Server Support**: Searches execute across all selected servers
- **Entry Management**: Complete CRUD operations for LDAP entries
- **Attribute Management**: Add, modify, and delete attributes
- **Authentication Testing**: Test bind credentials for any DN
- **UI Layout**: SplitLayout with 60/40 split for results and details
- **Filter Builder**: Visual filter construction with multiple operators
- **Error Handling**: Comprehensive error messages and notifications

### Build Verification
- ✅ `mvn clean compile` - Successful (14 source files)
- ✅ `mvn checkstyle:check` - 0 violations

### Known Limitations
- Tree Navigator (Browse DN selector) shows placeholder - to be implemented in v0.4
- Filter builder supports simple filters only (complex nested filters coming later)
- Operational attributes are read but not displayed in UI (show/hide toggle planned)

### Next Steps (v0.4)
- Implement Browse page with Tree Navigator
- Add hierarchical LDAP tree component
- Enable DN browsing and selection
- Lazy loading for tree nodes

---

## v0.2.1 - 2025-10-18 ✅ COMPLETED

### Improved Features
- ✅ **Enhanced Server Selection** - Replaced MultiSelectListBox with MultiSelectComboBox
  - Better UI/UX with dropdown selection
  - More compact and professional appearance
- ✅ **Visual Server Display** - Selected servers now displayed as badges
  - Horizontal row of server badges to the right of combo box
  - Individual remove buttons on each badge
  - Color-coded with primary theme colors
  - Responsive layout with flex-wrap support
- ✅ All code follows Google Java Style conventions
- ✅ Zero Checkstyle violations
- ✅ Project compiles and builds successfully

### Technical Details
- Replaced `MultiSelectListBox<String>` with `MultiSelectComboBox<String>`
- Added badge component factory with inline remove functionality
- Enhanced visual feedback with styled badges
- Maintained session-based persistence for server selection

### Files Modified
- `src/main/java/com/ldapbrowser/ui/MainLayout.java` - Updated server selector UI
- `pom.xml` - Version bumped to 0.2.1-SNAPSHOT

### Build Verification
- ✅ `mvn clean compile` - Successful
- ✅ `mvn test` - Successful
- ✅ `mvn checkstyle:check` - 0 violations

---

## v0.2 - 2025-10-18 ✅ COMPLETED

### Implemented Features
- ✅ **LdapServerConfig Model** - Complete configuration model with all LDAP connection parameters
- ✅ **ConfigurationService** - Service for managing server configurations
  - Load/save configurations to `~/.ldapbrowser/connections.json`
  - CRUD operations for server configurations
  - JSON persistence with Jackson
- ✅ **LdapService** - Core LDAP operations service
  - Connection management and pooling
  - SSL/TLS and StartTLS support
  - Test connection functionality
  - Connection pool management
- ✅ **Server Configuration Form** - Complete functional form with:
  - Server name, host, port fields
  - Base DN, Bind DN, Bind Password fields
  - SSL and StartTLS checkboxes
  - Server selector dropdown for existing configurations
  - Data binding with validation
- ✅ **Action Buttons**
  - Test Connection - Validates LDAP connection settings
  - Save - Persists configuration to JSON file
  - Copy - Duplicates current configuration
  - Delete - Removes configuration
- ✅ **Navbar Integration**
  - Multi-select server listbox in navbar
  - Displays selected server count
  - Persists selection in session
  - Auto-refresh when servers are saved
- ✅ All code follows Google Java Style conventions
- ✅ Zero Checkstyle violations
- ✅ Project compiles and builds successfully

### Files Created
- `src/main/java/com/ldapbrowser/model/LdapServerConfig.java` - Server configuration model
- `src/main/java/com/ldapbrowser/service/ConfigurationService.java` - Configuration management service
- `src/main/java/com/ldapbrowser/service/LdapService.java` - LDAP operations service

### Files Modified
- `src/main/java/com/ldapbrowser/ui/views/ServerView.java` - Fully functional server configuration form
- `src/main/java/com/ldapbrowser/ui/MainLayout.java` - Updated with working server selector
- `pom.xml` - Version bumped to 0.2.0-SNAPSHOT

### Technical Details
- **Configuration Storage**: `~/.ldapbrowser/connections.json`
- **Connection Pooling**: UnboundID LDAP SDK connection pools (1-10 connections)
- **SSL Support**: Full SSL/TLS and StartTLS support with trust-all manager
- **Data Binding**: Vaadin Binder for form validation
- **Session Management**: Server selection persisted in Vaadin session

### Build Verification
- ✅ `mvn clean compile` - Successful (12 source files)
- ✅ `mvn test` - Successful
- ✅ `mvn checkstyle:check` - 0 violations

### Next Steps (v0.3)
- Implement Search functionality
- Add LDAP search operations
- Build filter builder component
- Display search results

---

## v0.1.1 - 2025-10-18 🔧 HOTFIX

### Fixed
- ✅ Removed custom theme annotation causing runtime error
- ✅ Now using Vaadin's default Lumo theme
- ✅ Application runs successfully at http://localhost:8080

### Technical Details
Fixed `@Theme("ldap-browser")` annotation that was looking for a non-existent theme directory. The application now uses the default Vaadin Lumo theme, which works out of the box.

See: `docs/theme-fix.md` for details.

---

## v0.1 - 2025-10-18 ✅ COMPLETED

### Implemented Features
- ✅ Created Maven project structure with Spring Boot 3.2.0 and Vaadin 24.3.0
- ✅ Configured UnboundID LDAP SDK 7.0.3 dependency
- ✅ Built initial UI layout with Vaadin AppLayout
- ✅ Implemented Navbar with:
  - Application title
  - Server selector dropdown (placeholder)
  - Drawer toggle button
- ✅ Implemented Drawer navigation with links to all pages:
  - Server
  - Search
  - Browse
  - Schema
  - Access
  - Bulk
  - Import/Export
- ✅ Created placeholder views for all pages with "Under Construction" status
- ✅ Configured application properties and development profile
- ✅ Created dev-reload.sh script for development workflow
- ✅ All code follows Google Java Style conventions
- ✅ Zero Checkstyle violations
- ✅ Project compiles and builds successfully

### Files Created
- `pom.xml` - Maven project configuration
- `src/main/java/com/ldapbrowser/LdapBrowserApplication.java` - Application entry point
- `src/main/java/com/ldapbrowser/ui/MainLayout.java` - Main layout with navbar and drawer
- `src/main/java/com/ldapbrowser/ui/views/ServerView.java` - Server configuration view
- `src/main/java/com/ldapbrowser/ui/views/SearchView.java` - LDAP search view
- `src/main/java/com/ldapbrowser/ui/views/BrowseView.java` - Directory browse view
- `src/main/java/com/ldapbrowser/ui/views/SchemaView.java` - Schema exploration view
- `src/main/java/com/ldapbrowser/ui/views/AccessView.java` - Access control view
- `src/main/java/com/ldapbrowser/ui/views/BulkView.java` - Bulk operations view
- `src/main/java/com/ldapbrowser/ui/views/ImportExportView.java` - Import/Export view
- `src/main/resources/application.properties` - Application configuration
- `src/main/resources/application-development.properties` - Development profile
- `dev-reload.sh` - Development mode launcher script
- `.gitignore` - Git ignore rules

### Build Verification
- ✅ `mvn clean compile` - Successful
- ✅ `mvn test` - Successful (no tests yet)
- ✅ `mvn checkstyle:check` - 0 violations
