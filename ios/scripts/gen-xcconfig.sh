#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT_DIR="$(cd "$IOS_DIR/.." && pwd)"

ENV_FILE="$ROOT_DIR/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  ENV_FILE="$ROOT_DIR/.env.example"
fi

OUT_FILE="$IOS_DIR/Config/Local.xcconfig"
mkdir -p "$(dirname "$OUT_FILE")"

{
  echo "// Auto-generated from $(basename "$ENV_FILE") by ios/scripts/gen-xcconfig.sh — DO NOT COMMIT"
  echo '#include "Default.xcconfig"'
  echo ""

  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    line="${raw_line%%$'\r'}"
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" != *"="* ]] && continue

    key="${line%%=*}"
    val="${line#*=}"
    key="$(echo "$key" | xargs)"
    val="$(echo "$val" | sed -E 's/^["'\'']|["'\'']$//g')"
    [[ -z "$val" ]] && continue

    case "$key" in
      API_BASE_URL_DEV|API_BASE_URL_PROD|PUBLIC_SITE_URL|PRODUCT_PRO_MONTHLY|PRODUCT_PRO_QUARTERLY|PRODUCT_PRO_YEARLY|ADMOB_APP_ID_IOS|ADMOB_BANNER_UNIT_ID_IOS|GOOGLE_OAUTH_CLIENT_ID_IOS|GOOGLE_OAUTH_REVERSED_CLIENT_ID_IOS)
        escaped_val="${val//:\/\//:/\$()/}"
        echo "$key = $escaped_val"
        ;;
    esac
  done < "$ENV_FILE"
} > "$OUT_FILE"

echo "Generated $OUT_FILE"
