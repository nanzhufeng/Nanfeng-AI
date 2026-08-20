#!/usr/bin/env bash
set -euo pipefail

keystore_path="${NANFENG_AI_RELEASE_V2_KEYSTORE_PATH:-$HOME/Library/Application Support/NanzhufengSigning/NanfengAI-Android/nanfeng-ai-release-v2.jks}"
key_alias='nanfeng-ai-release-v2'
gradle_properties_path="${NANFENG_AI_RELEASE_V2_GRADLE_PROPERTIES_PATH:-$HOME/.gradle/gradle.properties}"
property_names=(
  'nanfengAi.releaseV2.keystore'
  'nanfengAi.releaseV2.storePassword'
  'nanfengAi.releaseV2.keyAlias'
  'nanfengAi.releaseV2.keyPassword'
)

if [[ ! -f "$keystore_path" ]]; then
  printf '%s\n' "找不到南枫 AI release v2 keystore：$keystore_path" >&2
  exit 1
fi

if [[ -f "$gradle_properties_path" ]]; then
  for property_name in "${property_names[@]}"; do
    if /usr/bin/grep -q "^${property_name}=" "$gradle_properties_path"; then
      printf '%s\n' "拒绝覆盖既有用户级签名配置：$property_name" >&2
      exit 1
    fi
  done
fi

read -r -s -p '输入 release v2 keystore 密码：' store_password
printf '\n'
read -r -s -p '再次输入 release v2 keystore 密码：' store_password_confirmation
printf '\n'
if [[ -z "$store_password" || "$store_password" != "$store_password_confirmation" ]]; then
  printf '%s\n' 'keystore 密码为空或两次输入不一致；未写入任何配置。' >&2
  exit 1
fi

read -r -s -p '输入 release v2 key 密码（直接回车表示与 keystore 密码相同）：' key_password
printf '\n'
key_password="${key_password:-$store_password}"
read -r -s -p '再次输入 release v2 key 密码：' key_password_confirmation
printf '\n'
key_password_confirmation="${key_password_confirmation:-$store_password}"
if [[ "$key_password" != "$key_password_confirmation" ]]; then
  printf '%s\n' 'key 密码两次输入不一致；未写入任何配置。' >&2
  exit 1
fi

escape_properties_value() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  value="${value//=/\\=}"
  value="${value//:/\\:}"
  if [[ "$value" == ' '* || "$value" == \#* || "$value" == \!* ]]; then
    value="\\$value"
  fi
  printf '%s' "$value"
}

umask 077
mkdir -p "$(dirname "$gradle_properties_path")"
staging_properties_path="$(mktemp "$(dirname "$gradle_properties_path")/.gradle.properties.nanfeng-ai-v2.XXXXXX")"
trap 'unset store_password store_password_confirmation key_password key_password_confirmation; rm -f "$staging_properties_path"' EXIT

if [[ -f "$gradle_properties_path" ]]; then
  cat "$gradle_properties_path" > "$staging_properties_path"
fi
{
  printf '\n# Nanfeng AI release v2 signing: managed locally; never commit this file.\n'
  printf 'nanfengAi.releaseV2.keystore=%s\n' "$(escape_properties_value "$keystore_path")"
  printf 'nanfengAi.releaseV2.storePassword=%s\n' "$(escape_properties_value "$store_password")"
  printf 'nanfengAi.releaseV2.keyAlias=%s\n' "$(escape_properties_value "$key_alias")"
  printf 'nanfengAi.releaseV2.keyPassword=%s\n' "$(escape_properties_value "$key_password")"
} >> "$staging_properties_path"

chmod 600 "$staging_properties_path"
mv "$staging_properties_path" "$gradle_properties_path"
printf '%s\n' '已写入南枫 AI release v2 的四项用户级 Gradle 配置；未回显密码。'
