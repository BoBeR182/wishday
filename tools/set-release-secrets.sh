#!/usr/bin/env bash
# Copies the release signing key and its passwords from the local signing
# properties into the GitHub secrets that .github/workflows/release.yml uses.
# Nothing secret is printed.
set -euo pipefail

props="${1:-$HOME/.android-keys/wishday-release.properties}"
repo=cybersecurityhandle/wishday

prop() { grep "^$1=" "$props" | cut -d= -f2- | tr -d '\r\n'; }

base64 -w0 "$(prop storeFile)" | gh secret set KEYSTORE_BASE64 -R "$repo"
prop storePassword | gh secret set KEYSTORE_PASSWORD -R "$repo"
prop keyAlias | gh secret set KEY_ALIAS -R "$repo"
prop keyPassword | gh secret set KEY_PASSWORD -R "$repo"
