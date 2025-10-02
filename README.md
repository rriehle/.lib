# Shared Libraries

A collection of shared Babashka libraries for common functionality across development toolkits.

## Purpose

This repository provides reusable Babashka scripts that implement core functionality shared across multiple development tools. Rather than duplicating code, tools like ADR, Requirements Management, and RunNotes load these shared libraries at runtime.

## Dependent Projects

This library is a dependency of:
- **[~/.adr](https://github.com/rriehle/.adr)** - Architecture Decision Records toolkit
- **[~/.req](https://github.com/rriehle/.req)** - Requirements Management toolkit
- **[~/.runnote](https://github.com/rriehle/.runnote)** - Development Knowledge Capture system

## Installation

1. Clone this repository to `~/.lib`:
   ```bash
   git clone <repo-url> ~/.lib
   ```

2. No PATH modification needed - dependent tools load these libraries directly using `load-file`.

3. Verify installation:
   ```bash
   ls ~/.lib/*.bb
   ```

## Library Modules

### Configuration Management

**`config-core.bb`** - Shared configuration loading and merging

- **Purpose**: Unified config hierarchy (global → project overrides)
- **Used by**: ADR tools, Requirements tools
- **Features**:
  - EDN config file parsing
  - Multi-level config merging
  - Path resolution and expansion
  - Project root discovery

**Example usage:**
```clojure
(load-file (str (fs/expand-home "~/.lib/config-core.bb")))

;; Load merged config
(def config (load-merged-config
              "~/.adr/config.edn"    ; Global defaults
              ".adr.edn"))           ; Project overrides
```

### Metadata Parsing

**`metadata-parser.bb`** - EDN metadata extraction from markdown

- **Purpose**: Parse embedded EDN metadata blocks from markdown files
- **Used by**: All tools (ADR, Requirements, RunNotes)
- **Features**:
  - Extracts ` ```edn :metadata` blocks
  - Validates EDN syntax
  - Returns parsed Clojure data structures
  - Handles malformed metadata gracefully

**Example usage:**
```clojure
(load-file (str (fs/expand-home "~/.lib/metadata-parser.bb")))

;; Extract metadata from file
(def metadata (extract-metadata "path/to/file.md"))
;; => {:date "2025-10-01" :status :accepted :tag #{:architecture}}
```

### ADR Support

**`adr-core.bb`** - ADR-specific configuration and path resolution

- **Purpose**: ADR path discovery, config defaults, validation helpers
- **Used by**: ADR validation, search, and duplicate detection tools
- **Features**:
  - ADR directory resolution
  - Sequence number parsing
  - Duplicate detection
  - Template management

**`adr-metadata-extractor.bb`** - ADR metadata extraction and validation

- **Purpose**: Extract and validate ADR-specific metadata
- **Used by**: ADR search, validation tools
- **Features**:
  - Parse ADR title and number
  - Extract and validate metadata schema
  - Status and tag validation
  - Cross-reference resolution

**Example usage:**
```clojure
(load-file (str (fs/expand-home "~/.lib/adr-core.bb")))
(load-file (str (fs/expand-home "~/.lib/adr-metadata-extractor.bb")))

;; Get ADR directory for current project
(def adr-dir (resolve-adr-path config))

;; Extract ADR metadata
(def adr-data (extract-adr-metadata "00042-event-sourcing.md"))
```

### Requirements Support

**`req-core.bb`** - Requirements-specific configuration and utilities

- **Purpose**: Requirements path discovery, ID parsing, traceability helpers
- **Used by**: Requirements validation, search, and traceability tools
- **Features**:
  - Requirements directory resolution
  - REQ-ID format parsing
  - Category and priority validation
  - Traceability link resolution

**`req-metadata-extractor.bb`** - Requirements metadata extraction

- **Purpose**: Extract and validate requirements-specific metadata
- **Used by**: Requirements search, validation, traceability tools
- **Features**:
  - Parse requirement ID and category
  - Extract metadata with schema validation
  - ISO 25010 taxonomy support
  - Traceability link extraction

**Example usage:**
```clojure
(load-file (str (fs/expand-home "~/.lib/req-core.bb")))
(load-file (str (fs/expand-home "~/.lib/req-metadata-extractor.bb")))

;; Get requirements directory
(def req-dir (resolve-req-path config))

;; Extract requirement metadata
(def req-data (extract-req-metadata "REQ-AUTH-001-mfa.md"))
```

## Directory Structure

```
~/.lib/
├── config-core.bb                # Shared config management
├── metadata-parser.bb            # EDN metadata parsing
├── adr-core.bb                   # ADR-specific utilities
├── adr-metadata-extractor.bb     # ADR metadata extraction
├── req-core.bb                   # Requirements utilities
├── req-metadata-extractor.bb     # Requirements metadata extraction
└── README.md                     # This file
```

## Design Principles

### 1. Single Source of Truth

Common functionality lives here, not duplicated across dependent projects. Changes propagate automatically to all consumers.

### 2. Runtime Loading

Libraries are loaded at runtime using `load-file`, allowing:
- Zero build process
- Immediate updates across all dependent tools
- Easy debugging and development

### 3. Namespace Isolation

Each library is self-contained with clear dependencies. Libraries can load other libraries but avoid circular dependencies.

### 4. Babashka Native

All code is Babashka-compatible:
- Fast startup time (no JVM)
- Minimal dependencies
- Standard Clojure syntax

## Development

### Adding New Libraries

1. Create new `.bb` file in `~/.lib/`
2. Follow naming convention: `<domain>-<purpose>.bb`
3. Add clear docstrings and examples
4. Update this README

### Testing Changes

Since libraries are loaded at runtime, test changes immediately:

```bash
# Test with ADR tools
cd ~/path/to/project
adr-validate

# Test with Requirements tools
req-search list

# Test with RunNotes
runnote-launch research Test
```

### Linting

```bash
cd ~/.lib
clj-kondo --lint *.bb
# Expected: 0 errors, 0 warnings
```

## Common Patterns

### Loading Multiple Libraries

```clojure
;; Load in dependency order
(def lib-dir (str (fs/expand-home "~/.lib/")))

(load-file (str lib-dir "config-core.bb"))
(load-file (str lib-dir "metadata-parser.bb"))
(load-file (str lib-dir "adr-core.bb"))
```

### Config Hierarchy

```clojure
;; Global config with project overrides
(def global-config "~/.adr/config.edn")
(def project-config ".adr.edn")

(def config (load-merged-config global-config project-config))
(def adr-dir (get-in config [:adr :path] "doc/adr"))
```

### Metadata Extraction

```clojure
;; Extract and validate metadata
(def file-content (slurp "00042-decision.md"))
(def metadata (extract-metadata file-content))

;; Validate required fields
(when-not (and (:date metadata) (:status metadata))
  (throw (ex-info "Missing required metadata" {:file file})))
```

## Versioning

This repository follows semantic versioning:
- **Major**: Breaking API changes
- **Minor**: New features, backward compatible
- **Patch**: Bug fixes

Check dependent tools' documentation for minimum required version.

## Integration Examples

### From ADR Tools

```clojure
;; ~/.adr/bin/adr-validate
#!/usr/bin/env bb

(require '[babashka.fs :as fs])

;; Load shared libraries
(load-file (str (fs/expand-home "~/.lib/config-core.bb")))
(load-file (str (fs/expand-home "~/.lib/metadata-parser.bb")))
(load-file (str (fs/expand-home "~/.lib/adr-core.bb")))
(load-file (str (fs/expand-home "~/.lib/adr-metadata-extractor.bb")))

;; Use shared functionality
(def config (load-merged-config "~/.adr/config.edn" ".adr.edn"))
(def adr-dir (resolve-adr-path config))
```

### From Requirements Tools

```clojure
;; ~/.req/bin/req-search
#!/usr/bin/env bb

(require '[babashka.fs :as fs])

;; Load shared libraries
(load-file (str (fs/expand-home "~/.lib/config-core.bb")))
(load-file (str (fs/expand-home "~/.lib/metadata-parser.bb")))
(load-file (str (fs/expand-home "~/.lib/req-core.bb")))
(load-file (str (fs/expand-home "~/.lib/req-metadata-extractor.bb")))

;; Use shared functionality
(def config (load-merged-config "~/.req/config.edn" ".req.edn"))
(def req-dir (resolve-req-path config))
```

## Troubleshooting

### Library not found

**Problem:** `FileNotFoundException: ~/.lib/config-core.bb`

**Solution:**
```bash
# Verify installation
ls ~/.lib/*.bb

# Re-clone if missing
git clone <repo-url> ~/.lib
```

### Load order issues

**Problem:** `Unable to resolve symbol`

**Solution:** Load libraries in dependency order:
1. `config-core.bb` (no dependencies)
2. `metadata-parser.bb` (no dependencies)
3. Domain-specific cores (`adr-core.bb`, `req-core.bb`)
4. Domain-specific extractors (depend on core and parser)

### Changes not taking effect

**Problem:** Updated library but tools still use old code

**Solution:** Babashka scripts load libraries at runtime - no caching. Check:
```bash
# Verify file was actually saved
cat ~/.lib/config-core.bb | head -20

# Check git status
cd ~/.lib
git status
```

## Contributing

This is shared infrastructure - changes impact multiple projects:

1. **Test thoroughly** across all dependent tools
2. **Document changes** in this README
3. **Maintain backward compatibility** when possible
4. **Version appropriately** following semver

## See Also

- [Babashka](https://babashka.org/) - Fast native Clojure scripting
- [ADR Tools](https://github.com/rriehle/.adr) - Architecture Decision Records
- [Requirements Tools](https://github.com/rriehle/.req) - Requirements Management
- [RunNotes](https://github.com/rriehle/.runnote) - Development Knowledge Capture
- [ADR-00035](https://github.com/rriehle/.adr/doc/adr/00035-folder-and-script-naming-conventions.md) - Naming conventions

## License

MIT
