#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=gradle-build-fingerprint.sh
source "${SCRIPT_DIR}/gradle-build-fingerprint.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "${tmp}"' EXIT

mkdir -p "${tmp}/gradle/wrapper"
printf 'distributionUrl=https://services.gradle.org/distributions/gradle-9.2.1-bin.zip\n' \
  > "${tmp}/gradle/wrapper/gradle-wrapper.properties"
cat > "${tmp}/build.gradle.kts" <<'EOF'
plugins {
    id("org.springframework.boot") version "4.0.5"
}
version = "0.1.7"
dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
}
EOF
printf 'rootProject.name = "mdwiki-api"\n' > "${tmp}/settings.gradle.kts"
printf 'org.gradle.jvmargs=-Xmx1g\n' > "${tmp}/gradle.properties"

fp_v17="$(gradle_build_fingerprint "${tmp}")"

cat > "${tmp}/build.gradle.kts" <<'EOF'
plugins {
    id("org.springframework.boot") version "4.0.5"
}
version = "0.1.8"
dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
}
EOF

fp_v18="$(gradle_build_fingerprint "${tmp}")"
if [[ "${fp_v17}" != "${fp_v18}" ]]; then
  echo "FAIL: project version change must not alter fingerprint (${fp_v17} -> ${fp_v18})" >&2
  exit 1
fi

cat > "${tmp}/build.gradle.kts" <<'EOF'
plugins {
    id("org.springframework.boot") version "4.0.6"
}
version = "0.1.8"
dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
}
EOF

fp_plugin="$(gradle_build_fingerprint "${tmp}")"
if [[ "${fp_v18}" == "${fp_plugin}" ]]; then
  echo "FAIL: plugin version change must alter fingerprint" >&2
  exit 1
fi

echo "OK"
