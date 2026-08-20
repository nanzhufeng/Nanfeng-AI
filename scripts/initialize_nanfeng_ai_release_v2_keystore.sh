#!/usr/bin/env bash
set -euo pipefail

signing_directory="${NANFENG_AI_RELEASE_V2_DIRECTORY:-$HOME/Library/Application Support/NanzhufengSigning/NanfengAI-Android}"
keystore_path="$signing_directory/nanfeng-ai-release-v2.jks"
key_alias="nanfeng-ai-release-v2"
validity_days=18263
default_keytool='/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool'

if [[ -x "$default_keytool" ]]; then
  keytool_path="$default_keytool"
elif command -v keytool >/dev/null 2>&1; then
  keytool_path="$(command -v keytool)"
else
  printf '%s\n' '找不到 keytool。请安装 Android Studio JBR 或设置可用 JDK。' >&2
  exit 1
fi

if [[ -e "$keystore_path" ]]; then
  printf '%s\n' "拒绝覆盖既有 release v2 keystore：$keystore_path" >&2
  exit 1
fi

umask 077
mkdir -p "$signing_directory"

printf '%s\n' '将创建南枫 AI 专属 release v2 keystore。'
printf '%s\n' '密码仅由 keytool 在本机交互式读取；脚本不会读取、回显或写入密码。'
"$keytool_path" -genkeypair \
  -storetype JKS \
  -keystore "$keystore_path" \
  -alias "$key_alias" \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -validity "$validity_days" \
  -dname 'CN=Nanfeng AI, OU=Android Release, O=Nanzhufeng, C=CN'

printf '%s\n' 'release v2 keystore 已创建。请仅在用户级 ~/.gradle/gradle.properties 或受控环境变量配置同一套四项签名值。'
printf '%s\n' "keystore path: $keystore_path"
printf '%s\n' "key alias: $key_alias"
