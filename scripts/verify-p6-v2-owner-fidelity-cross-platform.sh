#!/usr/bin/env bash
set -euo pipefail

# Generates a non-sensitive full-owner v2 package with the Android writer, then proves that the
# Desktop strict reader accepts the exact bytes. It is an automated protocol gate only: no device,
# picker, user data, Keychain, network, or persistent workspace is touched.
repository_root="$(cd "$(dirname "$0")/.." && pwd)"
temporary_root="$(mktemp -d /tmp/nanfeng-ai-p6-v2-owner-fidelity.XXXXXX)"
package_path="$temporary_root/android-owner-fidelity.nfai-exchange"
trap 'rm -rf "$temporary_root"' EXIT

export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -XX:TieredStopAtLevel=1"
export NANFENG_AI_V2_PACKAGE_CONTRACT_OUTPUT="$package_path"
nfai_jbr_home="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [[ -x "$nfai_jbr_home/bin/java" ]]; then
  export JAVA_HOME="$nfai_jbr_home"
fi

(
  cd "$repository_root"
  ./gradlew :app:testDebugUnitTest \
    --tests com.nanzhufeng.ai.domain.WorkspaceExchangeV2OwnerMapperContractsTest \
    --rerun-tasks \
    --no-daemon
  test -s "$package_path"
  node protocol/scripts/verify-v2-package.mjs "$package_path"
)

(
  cd "$repository_root/desktop/src-tauri"
  NANFENG_AI_ANDROID_V2_PACKAGE_GOLDEN="$package_path" \
    cargo test --lib p6_workspace_exchange_v2::tests::android_writer_package_is_accepted_by_the_desktop_reader_when_supplied -- --exact
)

echo "P6 v2 Android full-owner package → Desktop strict reader: passed"
