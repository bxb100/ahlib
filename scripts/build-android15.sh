#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly script_dir
project_dir="$(cd "${script_dir}/.." && pwd)"
readonly project_dir

if [ "$#" -eq 0 ]; then
    set -- testDebugUnitTest assembleDebug
fi

exec "${project_dir}/gradlew" "$@"
