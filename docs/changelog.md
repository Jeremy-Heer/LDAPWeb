# LDAP Web Browser

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
