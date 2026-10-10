#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ -z "${JAVA_HOME:-}" ] && command -v mise >/dev/null; then export JAVA_HOME="$(mise where java@temurin-17)"; fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
mkdir -p build/tests
javac -d build/tests src/com/wesley/flippet/PetRules.java tests/com/wesley/flippet/PetRulesTest.java
java -cp build/tests com.wesley.flippet.PetRulesTest
