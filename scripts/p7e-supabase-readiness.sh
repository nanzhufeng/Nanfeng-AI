#!/usr/bin/env bash
set -euo pipefail

# P7-E secret-free deployment readiness probe. It never prints config values, account data,
# targets, ciphertext, URLs, keys, tokens, or shell errors. Default is local/offline only.
mode="dry-run"
check_auth=false
target=""
for arg in "$@"; do
  case "$arg" in
    --check-auth) check_auth=true ;;
    --target=*) target="${arg#--target=}" ;;
    --dry-run|--readonly) mode="readonly" ;;
    --apply) echo "p7e_deploy_allowed=false"; echo "reason=explicit_user_authorization_required"; exit 2 ;;
    *) echo "p7e_deploy_allowed=false"; echo "reason=unsupported_argument"; exit 2 ;;
  esac
done

root="$(cd "$(dirname "$0")/.." && pwd)"
cli=false; linked=false; private_config=false; auth=false
command -v supabase >/dev/null 2>&1 && cli=true
[[ -f "$root/supabase/config.toml" || -f "$root/.supabase/config.toml" ]] && linked=true
for candidate in "$root/local.properties" "$root/app/local.properties"; do
  if [[ -f "$candidate" ]] \
    && rg -q '^(SUPABASE_URL|SUPABASE_PUBLISHABLE_KEY|SUPABASE_ANON_KEY|GOOGLE_WEB_CLIENT_ID)=[^[:space:]]+' "$candidate"; then
    # Presence of a generic Android SDK local.properties file is not P7-C configuration.
    if rg -q '^(SUPABASE_URL)=[^[:space:]]+' "$candidate" \
      && rg -q '^(GOOGLE_WEB_CLIENT_ID)=[^[:space:]]+' "$candidate" \
      && (rg -q '^(SUPABASE_PUBLISHABLE_KEY)=[^[:space:]]+' "$candidate" || rg -q '^(SUPABASE_ANON_KEY)=[^[:space:]]+' "$candidate"); then private_config=true; fi
  fi
done

# Authentication is deliberately opt-in because it can contact Supabase. Suppress all output.
if [[ "$check_auth" == true && "$cli" == true ]]; then
  if supabase projects list --output json >/dev/null 2>&1; then auth=true; fi
fi

echo "p7e_mode=$mode"
echo "supabase_cli_present=$cli"
echo "project_link_present=$linked"
echo "private_client_config_present=$private_config"
echo "auth_session_verified=$auth"
echo "target_supplied=$([[ -n "$target" ]] && echo true || echo false)"
echo "mutation_performed=false"
echo "remote_schema_verified=false"
echo "rls_grants_rpc_verified=false"
echo "anon_rejection_verified=false"
echo "function_jwt_verified=false"
echo "envelope_hash_readback_verified=false"
echo "next_gate=explicit_target_and_user_authorization_then_readonly_remote_verification"
