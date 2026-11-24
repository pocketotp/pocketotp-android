#!/usr/bin/env bash
# Upload release-signing secrets to GitHub without echoing them.
# Usage: scripts/set-release-secrets.sh /path/to/keystore.properties [owner/repo]
set -euo pipefail
props="${1:?keystore.properties path}"
repo="${2:-pocketotp/pocketotp-android}"
get() { sed -n "s/^$1=//p" "$props" | head -1; }
store="$(get storeFile)"
test -f "$store" || { echo "keystore not found" >&2; exit 1; }
base64 -w0 "$store" | gh secret set POCKETOTP_KEYSTORE_BASE64 --repo "$repo"
get storePassword | tr -d '\n' | gh secret set POCKETOTP_KEYSTORE_PASSWORD --repo "$repo"
get keyPassword | tr -d '\n' | gh secret set POCKETOTP_KEY_PASSWORD --repo "$repo"
echo "secrets set on $repo"
