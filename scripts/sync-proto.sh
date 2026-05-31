#!/usr/bin/env bash
# sync-proto.sh — developer-run check that the vendored fraud_eval.proto matches
# the source-of-truth copy in the sibling fluxa repo.
#
# Behaviour:
#   - If ../fluxa/proto/fraud/v1/fraud_eval.proto is absent, exit 0 with a
#     warning (CI runners that check out bankops alone hit this path).
#   - If it is present and differs from the vendored copy, print a diff and
#     exit 1 so the developer can either copy the new version or push the
#     vendored change back to fluxa.

set -euo pipefail

VENDORED="$(cd "$(dirname "$0")/.." && pwd)/backend/src/main/proto/fraud/v1/fraud_eval.proto"
UPSTREAM="$(cd "$(dirname "$0")/.." && pwd)/../fluxa/proto/fraud/v1/fraud_eval.proto"

if [[ ! -f "$UPSTREAM" ]]; then
    echo "sync-proto: upstream not found at $UPSTREAM — skipping (developer machine only)" >&2
    exit 0
fi

if ! diff -u "$VENDORED" "$UPSTREAM"; then
    echo
    echo "sync-proto: VENDORED proto differs from upstream. Either:" >&2
    echo "  cp \"$UPSTREAM\" \"$VENDORED\"   # accept upstream" >&2
    echo "  cp \"$VENDORED\" \"$UPSTREAM\"   # push your changes to fluxa first" >&2
    exit 1
fi

echo "sync-proto: vendored proto matches upstream"
