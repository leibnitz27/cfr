#!/usr/bin/env bash
set -euo pipefail

baseline_classes="$1"
current_classes="$2"
fixture_set="$3"
fixture_root="decompilation-test/test-data"
report_root="target/corpus-regression"
agent_jar="$HOME/.m2/repository/org/jacoco/org.jacoco.agent/0.8.16/org.jacoco.agent-0.8.16-runtime.jar"
execution_data="$PWD/target/jacoco.exec"

case "$fixture_set" in
    hardcoded)
        fixture_directory="$fixture_root/hardcoded"
        minimum_inputs=80
        ;;
    java_21|java_25)
        fixture_directory="$fixture_root/output/$fixture_set"
        minimum_inputs=1000
        ;;
    *)
        fixture_directory="$fixture_root/precompiled_tests/$fixture_set"
        minimum_inputs=1000
        ;;
esac

if [[ ! -f "$agent_jar" || ! -d "$fixture_directory" ]]; then
    echo "JaCoCo agent or compiled fixture set is missing: $fixture_set" >&2
    exit 1
fi

fixture_files=()
while IFS= read -r -d '' fixture_file; do
    fixture_files+=("$fixture_file")
done < <(find "$fixture_directory" -type f \( -name '*.class' -o -name '*.jar' \) -print0 | sort -z)

if (( ${#fixture_files[@]} < minimum_inputs )); then
    echo "Unexpectedly few compiled fixture inputs for $fixture_set: ${#fixture_files[@]}" >&2
    exit 1
fi

baseline_output="$report_root/baseline"
current_output="$report_root/current"
mkdir -p "$baseline_output" "$current_output"
echo "Comparing $fixture_set: ${#fixture_files[@]} compiled fixture inputs"

timeout 20m java -Xmx2g -javaagent:"$agent_jar=destfile=$report_root/baseline.exec,append=false" \
    -cp "$baseline_classes" org.benf.cfr.reader.Main \
    "${fixture_files[@]}" --showversion false --dumpexceptionstacktrace false \
    --renameillegalidents true --outputdir "$baseline_output" > "$report_root/baseline.log" 2>&1

timeout 20m java -Xmx2g -javaagent:"$agent_jar=destfile=$execution_data,append=true" \
    -cp "$current_classes" org.benf.cfr.reader.Main \
    "${fixture_files[@]}" --showversion false --dumpexceptionstacktrace false \
    --renameillegalidents true --outputdir "$current_output" > "$report_root/current.log" 2>&1

if [[ -z "$(find "$current_output" -type f -name '*.java' -print -quit)" ]]; then
    echo "No decompilation output generated for $fixture_set" >&2
    exit 1
fi

for comparison_output in "$baseline_output" "$current_output"; do
    if [[ -f "$comparison_output/summary.txt" ]]; then
        sed -i '/^Decompiled with CFR /d' "$comparison_output/summary.txt"
    fi
done

if ! diff -qr "$baseline_output" "$current_output" > "$report_root/output.diff"; then
    echo "Decompilation output differs from master for $fixture_set:" >&2
    head -n 40 "$report_root/output.diff" >&2
    diff -ru "$baseline_output" "$current_output" | head -n 100 >&2 || true
    exit 1
fi

echo "Verified $fixture_set: ${#fixture_files[@]} inputs match master."
