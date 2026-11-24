#!/usr/bin/env bash
# Print the CHANGELOG.md section for a version (without the heading). Usage: changelog-section.sh 2.5.1
set -euo pipefail
v="${1#v}"
awk -v v="$v" '
  /^## \[/ { if (on) exit; on = index($0, "## [" v "]") == 1; next }
  on { print }
' "${2:-CHANGELOG.md}" | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}'
