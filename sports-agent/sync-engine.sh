#!/usr/bin/env bash
# Refresh engine/ from a checkout of jamarius997-a11y/sports-simulators (run its refresh.js first for fresh data).
set -e
SRC="${1:-../../jamarius997-a11y/sports-simulators}"
for f in sim.js travel.js data.js picks-results.js backtest-results.js news-data.js blend-weights.js recap-data.js picks-log.json; do cp "$SRC/$f" "$(dirname "$0")/engine/$f"; done
rm -rf "$(dirname "$0")/engine/reports" && cp -r "$SRC/reports" "$(dirname "$0")/engine/reports"
echo "engine synced from $SRC"
