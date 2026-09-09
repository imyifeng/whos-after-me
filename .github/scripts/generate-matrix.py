#!/usr/bin/env python3
"""Generates the CI Anchor matrix from the Stonecutter workspace (ADR-0007).

Parses the workspace's `settings.gradle.kts` so CI never hand-maintains the
Anchor list: rolling version pickup (ADR-0006) then extends CI by adding the
Anchor subproject alone - no workflow edits.

Sources of truth:
- `versions("1.21.1", ...)` -> the Anchors; one build/server-gametest job each.
- `vcsVersion = "26.2"`     -> the CI canary, i.e. the default development
                               Anchor (ADR-0006, ADR-0007).
- Client gametests exist from 1.21.4 onward; 1.21.1 ships no
  `fabric-client-gametest-api-v1` module at any Fabric API version (testing
  research #17). This mirrors the `client_gametest` stonecutter constant in
  `stonecutter.gradle.kts` - if that boundary ever moves, update
  CLIENT_GAMETEST_MIN together with it.

Prints one line of compact JSON (GitHub Actions matrix form):
{
  "anchor": {"include": [{"version", "client", "canary"}, ...]},  # every Anchor
  "client": {"include": [...]},   # Anchors that ship client gametests (1.21.4+)
  "canary": {"include": [ ... ]}  # exactly one entry: the vcsVersion Anchor
}

Usage: generate-matrix.py [path/to/settings.gradle.kts] (default: ./settings.gradle.kts)
"""

import json
import re
import sys
from pathlib import Path

SETTINGS_FILE = "settings.gradle.kts"

# First Minecraft version whose Fabric API ships the client gametest module.
# Keep in sync with `client_gametest` in stonecutter.gradle.kts (ADR-0004/0007).
CLIENT_GAMETEST_MIN = (1, 21, 4)


def parse_version(version: str) -> tuple[int, ...]:
    return tuple(int(part) for part in version.split("."))


def main() -> int:
    settings = Path(sys.argv[1] if len(sys.argv) > 1 else SETTINGS_FILE)
    try:
        text = settings.read_text(encoding="utf-8")
    except OSError as error:
        print(f"error: cannot read {settings}: {error}", file=sys.stderr)
        return 1

    versions_match = re.search(r"versions\s*\(([^)]*)\)", text, re.DOTALL)
    if not versions_match:
        print(f"error: no `versions(...)` block found in {settings}", file=sys.stderr)
        return 1
    anchors = re.findall(r'"([^"]+)"', versions_match.group(1))
    if not anchors:
        print(f"error: the `versions(...)` block in {settings} lists no Anchors", file=sys.stderr)
        return 1

    vcs_match = re.search(r'vcsVersion\s*=\s*"([^"]+)"', text)
    if not vcs_match:
        print(f'error: no `vcsVersion = "..."` found in {settings}', file=sys.stderr)
        return 1
    canary = vcs_match.group(1)
    if canary not in anchors:
        print(
            f"error: canary {canary!r} (vcsVersion) is not one of the Anchors {anchors}",
            file=sys.stderr,
        )
        return 1

    def entry(version: str) -> dict:
        return {
            "version": version,
            "client": parse_version(version) >= CLIENT_GAMETEST_MIN,
            "canary": version == canary,
        }

    entries = [entry(version) for version in anchors]
    payload = {
        "anchor": {"include": entries},
        "client": {"include": [e for e in entries if e["client"]]},
        "canary": {"include": [e for e in entries if e["canary"]]},
    }
    print(json.dumps(payload, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
