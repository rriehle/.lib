# Version Compatibility Matrix

This document tracks version compatibility between `.lib` and dependent toolkits.

## Current Version: v1.0.0

### Dependent Toolkits

| Toolkit | Requires .lib | Status | Library Dependencies |
|---------|---------------|--------|---------------------|
| `.adr` | ✅ Required | Active | `metadata-parser.bb` |
| `.runnote` | ✅ Required | Active | `config-core.bb`, `metadata-parser.bb` |
| `.req` | ❌ No dependency | Active | Uses own libraries (`req-core.bb`, `req-metadata-extractor.bb`) |

## Library Exports

### `metadata-parser.bb`

**Purpose:** EDN metadata parsing from markdown files

**Exports:**

- `:extract-edn-metadata` - Extracts EDN metadata blocks from markdown

**Used by:**

- `.adr/bin/adr-search`
- `.adr/bin/adr-validate`
- `.runnote/bin/runnote-search`

**Stability:** Stable (v1.0.0)

### `config-core.bb`

**Purpose:** Configuration management and project root discovery

**Exports:**

- `:load-config` - Load configuration from EDN files
- `:resolve-*-dir` - Path resolution functions
- `:find-git-root` - Git repository root discovery
- `:discover-project-root` - Project root discovery

**Used by:**

- `.runnote/bin/runnote-init`
- `.runnote/bin/runnote-launch`
- `.runnote/bin/runnote-search`

**Stability:** Stable (v1.0.0)

## Version Compatibility Rules

### Semantic Versioning

`.lib` follows [semantic versioning](https://semver.org/):

- **MAJOR (X.0.0):** Breaking changes to library exports or behavior
  - Incompatible with previous versions
  - Requires toolkit updates
  - Coordinated release across all dependent toolkits

- **MINOR (1.X.0):** New features, backwards compatible
  - New exports or optional parameters
  - Safe to upgrade without toolkit changes
  - Toolkits automatically benefit from enhancements

- **PATCH (1.0.X):** Bug fixes, no interface changes
  - Internal fixes only
  - Safe to upgrade without any changes
  - Recommended for all users

### Toolkit Version Requirements

Each toolkit specifies its `.lib` compatibility:

```bash
# Example from toolkit install.sh
LIB_MIN_VERSION="1.0.0"
LIB_MAX_VERSION="2.0.0"  # Exclusive
```

### Breaking Change Policy

When `.lib` introduces breaking changes (MAJOR version bump):

1. **Announcement:** Document breaking changes in release notes
2. **Migration Guide:** Provide upgrade instructions
3. **Toolkit Updates:** Update all dependent toolkits
4. **Coordinated Release:** Release all toolkits together
5. **Deprecation Period:** Maintain previous MAJOR version for 30 days

## Version History

### v1.0.0 (2025-10-19)

**Initial Release**

- `metadata-parser.bb` - EDN metadata extraction
- `config-core.bb` - Configuration and path management
- GitHub Release infrastructure
- Installation script support

**Compatible Toolkits:**

- `.adr` v1.0.0+
- `.runnote` v1.0.0+

## Testing Compatibility

### Manual Testing

```bash
# Test .adr with current .lib
~/.adr/bin/adr-search list
~/.adr/bin/adr-validate

# Test .runnote with current .lib
~/.runnote/bin/runnote-search summary
~/.runnote/bin/runnote-init
```

### Version Verification

```bash
# Check installed .lib version
cat ~/.lib/VERSION

# Check toolkit compatibility
~/.adr/install.sh  # Should detect .lib and report version
~/.runnote/install.sh  # Should detect .lib and report version
```

## Upgrade Procedures

### Upgrading .lib Only

```bash
# Install new .lib version
curl -sL https://github.com/rriehle/.lib/releases/download/v1.1.0/install.sh | bash

# Verify toolkits still work (MINOR/PATCH versions only)
adr-validate --help
runnote-launch --help
```

### Upgrading After .lib Breaking Change

```bash
# Step 1: Upgrade .lib to new MAJOR version
curl -sL https://github.com/rriehle/.lib/releases/download/v2.0.0/install.sh | bash

# Step 2: Upgrade all dependent toolkits
curl -sL https://github.com/rriehle/.adr/releases/download/v2.0.0/install.sh | bash
curl -sL https://github.com/rriehle/.runnote/releases/download/v2.0.0/install.sh | bash

# Step 3: Verify functionality
adr-search list
runnote-search summary
```

## Rollback Procedures

### Rollback .lib

```bash
# Downgrade to previous version
rm -rf ~/.lib
curl -sL https://github.com/rriehle/.lib/releases/download/v1.0.0/install.sh | bash

# Verify toolkits work
adr-validate --help
```

### Rollback Toolkit Only

```bash
# Downgrade single toolkit
rm -rf ~/.adr
curl -sL https://github.com/rriehle/.adr/releases/download/v1.0.0/install.sh | bash
```

## Future Considerations

### Potential New Libraries

- `validation-core.bb` - Shared validation utilities
- `git-helpers.bb` - Git operations abstraction
- `template-engine.bb` - Template rendering

### Migration Path

When adding new shared libraries:

1. Add to `.lib` as MINOR version (backwards compatible)
2. Update dependent toolkits to use new library (optional)
3. Mark old toolkit-specific code as deprecated
4. Remove duplicated code in next MAJOR toolkit version

## Support Matrix

| .lib Version | .adr Support | .runnote Support | .req Support | Status |
|--------------|--------------|------------------|--------------|--------|
| v1.0.0 | v1.0.0+ | v1.0.0+ | N/A | Current |
| v2.0.0 | v2.0.0+ | v2.0.0+ | N/A | Future |

## Contact

For version compatibility issues:

- Open issue in relevant repository (`.lib`, `.adr`, `.runnote`)
- Check release notes for breaking changes
- Consult ADR-00068 for architectural decisions
