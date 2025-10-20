# Shared Libraries

A collection of shared Babashka libraries for common functionality across development toolkits.

## Purpose

This repository provides reusable Babashka scripts that implement core functionality shared across multiple development tools. Rather than duplicating code, tools like ADR, Requirements Management, and RunNotes load these shared libraries at runtime.

## Dependent Projects

This library is a dependency of:

- **[~/.adr](https://github.com/rriehle/.adr)** - Architecture Decision Records toolkit ✅ **Required**
- **[~/.runnote](https://github.com/rriehle/.runnote)** - Development Knowledge Capture system ✅ **Required**
- **[~/.req](https://github.com/rriehle/.req)** - Requirements Management toolkit ❌ **No dependency**

See [DEPENDENCIES.md](DEPENDENCIES.md) for version compatibility matrix.

## Installation

### From GitHub Releases (Recommended)

```bash
# Install latest release
curl -sL https://github.com/rriehle/.lib/releases/latest/download/install.sh | bash

# Or install specific version
curl -sL https://github.com/rriehle/.lib/releases/download/v1.0.0/install.sh | bash
```

This installs to `~/.lib/` by default. Dependent toolkits (`.adr`, `.runnote`) will automatically detect and use it.

### From Source (Development)

```bash
# Clone this repository to ~/.lib
git clone https://github.com/rriehle/.lib.git ~/.lib
```

### Verify Installation

```bash
# Check files installed
ls ~/.lib/*.bb

# Check version
cat ~/.lib/VERSION
```

**Note:** `.lib` must be installed before installing dependent toolkits (`.adr`, `.runnote`).

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

### Test Framework

**`test/test-framework.bb`** - Comprehensive testing utilities for Babashka scripts

- **Purpose**: Reusable test framework for all CLI tools (ADR, Requirements, RunNotes)
- **Used by**: Test suites across all projects
- **Features**:
  - Assertion functions (`assert-true`, `assert-equal`, `assert-string-contains`, etc.)
  - Property-based testing utilities
  - Test fixtures (temporary directories, file creation)
  - Command execution helpers
  - Test reporting and summaries

**Assertion Functions:**

- `assert-true`, `assert-false` - Boolean assertions
- `assert-equal`, `assert-not-equal` - Value equality
- `assert-string-contains` - Substring matching
- `assert-contains` - Collection membership
- `assert-nil`, `assert-not-nil` - Null checks
- `assert-count` - Collection size
- `assert-throws` - Exception expectations
- `assert-file-exists`, `assert-file-not-exists` - File system checks

**Test Utilities:**

- `test-case` - Run single test with pass/fail reporting
- `run-tests` - Execute test suite and return results
- `with-temp-dir` - Temporary directory fixture with auto-cleanup
- `create-test-file` - Create test files in temp directories

**Property-Based Testing:**

- `gen-string`, `gen-int`, `gen-seq` - Random data generators
- `check-property` - Run property tests N times

**Example usage:**

```clojure
(load-file (str (fs/expand-home "~/.lib/test/test-framework.bb")))

;; Import what you need
(def test-case (:test-case test-framework/exports))
(def assert-equal (:assert-equal test-framework/exports))
(def assert-string-contains (:assert-string-contains test-framework/exports))
(def with-temp-dir (:with-temp-dir test-framework/exports))

;; Write tests
(defn test-example []
  (with-temp-dir
    (fn [temp-dir]
      (let [result (my-function temp-dir)]
        (assert-equal "expected" result "Should return expected value")))))

(defn test-error-messages []
  (let [error (get-error-message)]
    (assert-string-contains error "field-name"
                           "Error should mention the field")))

;; Run tests
(def tests
  [(test-case "Example test" test-example)
   (test-case "Error message test" test-error-messages)])

;; Execute and get results
(let [results ((:run-tests test-framework/exports) tests)]
  (println (str (:passed results) " passed, " (:failed results) " failed")))
```

## Directory Structure

```
~/.lib/
├── config-core.bb                # Shared config management (truly shared)
├── metadata-parser.bb            # EDN metadata parsing (truly shared)
├── test/
│   └── test-framework.bb         # Test utilities (truly shared)
└── README.md                     # This file

Note: Project-specific libraries have been moved to their respective projects:
- ADR libraries: ~/.adr/adr-core.bb, ~/.adr/adr-metadata-extractor.bb
- Requirements libraries: ~/.req/req-core.bb, ~/.req/req-metadata-extractor.bb
```

## Design Principles

### 1. Truly Shared Libraries Only

**What belongs in ~/.lib:**

- Code used by 2+ projects (config-core.bb, metadata-parser.bb)
- Test infrastructure (test-framework.bb)
- Universal utilities with no domain-specific logic

**What belongs in project directories:**

- Domain-specific logic (ADR utilities in ~/.adr, Requirements utilities in ~/.req)
- Project-specific validation and extraction
- Tools that only make sense in one domain context

This separation emerged from DRY refactoring work (October 2025) that identified and eliminated duplicate functions while clarifying architectural boundaries.

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
;; Load shared libraries from ~/.lib
(load-file (str (fs/expand-home "~/.lib/config-core.bb")))
(load-file (str (fs/expand-home "~/.lib/metadata-parser.bb")))

;; Load project-specific libraries from their home directories
;; (if building ADR tools)
(load-file (str (fs/expand-home "~/.adr/adr-core.bb")))
(load-file (str (fs/expand-home "~/.adr/adr-metadata-extractor.bb")))

;; (if building Requirements tools)
(load-file (str (fs/expand-home "~/.req/req-core.bb")))
(load-file (str (fs/expand-home "~/.req/req-metadata-extractor.bb")))
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

This repository follows [semantic versioning](https://semver.org/):

- **Major (X.0.0)**: Breaking API changes - requires toolkit updates
- **Minor (1.X.0)**: New features, backward compatible - safe to upgrade
- **Patch (1.0.X)**: Bug fixes - recommended for all

**Current Version:** v1.0.0

See [DEPENDENCIES.md](DEPENDENCIES.md) for:

- Version compatibility matrix
- Toolkit version requirements
- Upgrade and rollback procedures
- Breaking change policy

## Integration Examples

### From ADR Tools

```clojure
;; ~/.adr/bin/adr-validate
#!/usr/bin/env bb

(require '[babashka.fs :as fs])

;; Load shared libraries from ~/.lib
(load-file (str (fs/expand-home "~/.lib/config-core.bb")))
(load-file (str (fs/expand-home "~/.lib/metadata-parser.bb")))

;; Load ADR-specific libraries from ~/.adr
(load-file (str (fs/expand-home "~/.adr/adr-core.bb")))
(load-file (str (fs/expand-home "~/.adr/adr-metadata-extractor.bb")))

;; Use functionality
(def config ((:load-config adr-core/exports)))
(def adr-dir ((:resolve-adr-path adr-core/exports) config))
```

### From Requirements Tools

```clojure
;; ~/.req/bin/req-search
#!/usr/bin/env bb

(require '[babashka.fs :as fs])

;; Load shared libraries from ~/.lib
(load-file (str (fs/expand-home "~/.lib/config-core.bb")))
(load-file (str (fs/expand-home "~/.lib/metadata-parser.bb")))

;; Load Requirements-specific libraries from ~/.req
(load-file (str (fs/expand-home "~/.req/req-core.bb")))
(load-file (str (fs/expand-home "~/.req/req-metadata-extractor.bb")))

;; Use functionality
(def config ((:load-config req-core/exports)))
(def req-dir ((:resolve-req-path req-core/exports) config))
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

1. Shared libraries first: `config-core.bb`, `metadata-parser.bb` (from ~/.lib)
2. Domain-specific cores: `adr-core.bb` (from ~/.adr) or `req-core.bb` (from ~/.req)
3. Domain-specific extractors: `adr-metadata-extractor.bb` or `req-metadata-extractor.bb`

**Note:** Project-specific libraries are now in their respective project directories, not in ~/.lib.

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
