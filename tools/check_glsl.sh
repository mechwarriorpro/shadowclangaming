#!/usr/bin/env bash
# Compile every core shader with glslangValidator.
#
# This exists because "the sweep passes" and "the shader compiles" are different claims, and the second
# one was unverifiable here: sweep.py checks that uniform names join across fsh/json/java, and the JVM
# compile in CI never sees GLSL at all. A shader that does not compile does not fail the build - it fails
# at runtime, VolumetricFx catches it, sets loadFailed, and the game silently falls back to CPU triangles.
# That looks exactly like "the effects are transparent and all the same", with nothing in the build log.
set -uo pipefail

if ! command -v glslangValidator >/dev/null 2>&1; then
    echo "glslangValidator not installed; skipping (CI installs glslang-tools)"
    exit 0
fi

root="$(cd "$(dirname "$0")/.." && pwd)"
status=0
checked=0
while IFS= read -r -d '' f; do
    case "$f" in
        *.fsh) stage=frag ;;
        *.vsh) stage=vert ;;
        *) continue ;;
    esac
    checked=$((checked + 1))
    # No --target-env: it makes glslang emit SPIR-V, which demands GLSL 330+ and Vulkan-style
    # layout(location=) on every uniform. Minecraft core shaders are #version 150 desktop GL and declare
    # their uniforms plainly, so that flag reports the toolchain's rules as errors in every shader we
    # have. Left to itself glslang validates against the #version each file declares, which is the point.
    if ! out="$(glslangValidator -S "$stage" "$f" 2>&1)"; then
        status=1
        echo "FAIL ${f#"$root"/}"
        echo "$out" | sed 's/^/     /'
    fi
done < <(find "$root/src/main/resources" -type f \( -name '*.fsh' -o -name '*.vsh' \) -print0)

if [ "$status" -eq 0 ]; then
    echo "glsl: $checked shaders compile"
fi
exit "$status"
