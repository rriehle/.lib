#!/bin/bash
#
# Shared Libraries (.lib) - Installation Script
# Install .lib from GitHub Releases
#
# Usage:
#   curl -sL https://github.com/rriehle/.lib/releases/latest/download/install.sh | bash
#
#   Or with specific version:
#   curl -sL https://github.com/rriehle/.lib/releases/download/v1.0.0/install.sh | bash
#
#   Or with custom install directory:
#   curl -sL https://github.com/rriehle/.lib/releases/latest/download/install.sh | bash -s -- v1.0.0 /custom/path
#

set -e

# Configuration
REPO="rriehle/.lib"
VERSION="${1:-latest}"
INSTALL_DIR="${2:-$HOME/.lib}"
TMP_DIR=$(mktemp -d)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Cleanup on exit
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

# Logging functions
info() {
  echo -e "${GREEN}==>${NC} $1"
}

warn() {
  echo -e "${YELLOW}Warning:${NC} $1"
}

error() {
  echo -e "${RED}Error:${NC} $1" >&2
  exit 1
}

# Check prerequisites
check_prerequisites() {
  if ! command -v curl >/dev/null 2>&1; then
    error "curl is required but not installed"
  fi

  if ! command -v tar >/dev/null 2>&1; then
    error "tar is required but not installed"
  fi
}

# Determine download URL
get_download_url() {
  if [ "$VERSION" = "latest" ]; then
    # Get latest release version
    info "Fetching latest release version..."
    LATEST_VERSION=$(curl -sL "https://api.github.com/repos/${REPO}/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')

    if [ -z "$LATEST_VERSION" ]; then
      error "Could not determine latest version"
    fi

    info "Latest version: ${LATEST_VERSION}"
    echo "https://github.com/${REPO}/releases/download/${LATEST_VERSION}/lib-${LATEST_VERSION}.tar.gz"
  else
    echo "https://github.com/${REPO}/releases/download/${VERSION}/lib-${VERSION}.tar.gz"
  fi
}

# Download and extract
install_lib() {
  local url=$1

  info "Downloading .lib from: ${url}"

  if ! curl -fsSL "$url" -o "${TMP_DIR}/lib.tar.gz"; then
    error "Failed to download .lib. Check that the version exists."
  fi

  info "Extracting to ${INSTALL_DIR}..."

  # Create parent directory if it doesn't exist
  mkdir -p "$(dirname "$INSTALL_DIR")"

  # Extract to temporary location
  tar xzf "${TMP_DIR}/lib.tar.gz" -C "$TMP_DIR"

  # Find the extracted directory (should be lib-vX.Y.Z)
  EXTRACTED_DIR=$(find "$TMP_DIR" -maxdepth 1 -type d -name "lib-*" | head -n 1)

  if [ -z "$EXTRACTED_DIR" ]; then
    error "Could not find extracted directory"
  fi

  # Remove existing installation if present
  if [ -d "$INSTALL_DIR" ]; then
    warn "Removing existing installation at ${INSTALL_DIR}"
    rm -rf "$INSTALL_DIR"
  fi

  # Move to final location
  mv "$EXTRACTED_DIR" "$INSTALL_DIR"

  info "Installation complete!"
}

# Display post-install instructions
post_install() {
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo ""
  info "Shared Libraries (.lib) installed to: ${INSTALL_DIR}"
  echo ""
  echo "This is a foundation dependency for:"
  echo "  - ADR tools (.adr)"
  echo "  - RunNotes (.runnote)"
  echo "  - Requirements tracking (.req)"
  echo ""
  echo "Next: Install toolkit(s):"
  echo "  curl -sL https://github.com/rriehle/.adr/releases/latest/download/install.sh | bash"
  echo "  curl -sL https://github.com/rriehle/.runnote/releases/latest/download/install.sh | bash"
  echo "  curl -sL https://github.com/rriehle/.req/releases/latest/download/install.sh | bash"
  echo ""
  echo "Verify installation:"
  echo "  ls ~/.lib/*.bb"
  echo "  cat ~/.lib/VERSION"
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo ""
}

# Main
main() {
  info "Installing Shared Libraries (.lib)"
  echo ""

  check_prerequisites

  local download_url
  download_url=$(get_download_url)

  install_lib "$download_url"

  post_install
}

main "$@"
