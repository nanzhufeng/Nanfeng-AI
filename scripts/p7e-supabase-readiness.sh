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
base_migration=false; list_migration=false; e2ee_migration=false; rpc_contract=false
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

# The cloud recovery list is not an independent feature: it depends on the P7-C
# tables and four protected RPCs. Keep this local check secret-free so a release
# cannot be marked ready when only the later list migration is present.
base_sql="$root/supabase/migrations/202608130001_p7c_secure_sync.sql"
list_sql="$root/supabase/migrations/202609120002_p7f_list_sync_documents.sql"
e2ee_sql="$root/supabase/migrations/202609150006_p8_restore_e2ee_sync.sql"
if [[ -f "$base_sql" ]] \
  && rg -q 'create table if not exists public\.nfai_account_keys' "$base_sql" \
  && rg -q 'create table if not exists public\.nfai_sync_documents' "$base_sql" \
  && rg -q 'nanfeng_sync_read_account_key' "$base_sql" \
  && rg -q 'nanfeng_sync_put_account_key' "$base_sql" \
  && rg -q 'nanfeng_sync_read_document' "$base_sql" \
  && rg -q 'nanfeng_sync_commit_document' "$base_sql"; then
  base_migration=true
fi
if [[ -f "$list_sql" ]] \
  && rg -q 'nanfeng_sync_list_documents' "$list_sql" \
  && rg -q 'grant execute on function public\.nanfeng_sync_list_documents\(text\) to authenticated' "$list_sql"; then
  list_migration=true
fi
if [[ -f "$e2ee_sql" ]] \
  && rg -q "p_envelope->>'format' = 'nfai\.sync\.envelope'" "$e2ee_sql" \
  && rg -q 'drop function if exists public\.nanfeng_sync_valid_direct_envelope' "$e2ee_sql" \
  && rg -q 'grant execute on function public\.nanfeng_sync_commit_document\(text,text,bigint,jsonb\) to authenticated' "$e2ee_sql"; then
  e2ee_migration=true
fi
if [[ "$base_migration" == true && "$list_migration" == true && "$e2ee_migration" == true ]]; then rpc_contract=true; fi

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
echo "base_sync_migration_contract_present=$base_migration"
echo "list_sync_migration_contract_present=$list_migration"
echo "e2ee_restore_migration_contract_present=$e2ee_migration"
echo "all_required_sync_rpc_contracts_present=$rpc_contract"
echo "mutation_performed=false"
echo "remote_schema_verified=false"
echo "rls_grants_rpc_verified=false"
echo "anon_rejection_verified=false"
echo "function_jwt_verified=false"
echo "envelope_hash_readback_verified=false"
echo "next_gate=explicit_target_and_user_authorization_then_deploy_all_migrations_including_e2ee_restore_then_authenticated_remote_envelope_verification"
