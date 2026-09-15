#!/usr/bin/env python3
"""Validate that release screenshots are bound to one exact candidate package.

This script is intentionally local and read-only.  It never captures a screen,
installs an app, uploads an asset, or reads credentials.  A successful result
checks file hashes and provenance structure; the release owner still confirms
that the recorded installed-package hash came from the stated device.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import subprocess
import sys
from pathlib import Path


REQUIRED_TOP_LEVEL = {
    "schema_version",
    "release_tag",
    "platform",
    "source_commit",
    "candidate",
    "installed_package_sha256",
    "environment",
    "screenshots",
}
REQUIRED_CANDIDATE = {"path", "sha256", "built_at"}
REQUIRED_SCREENSHOT = {"path", "sha256", "captured_at", "description"}


def fail(message: str) -> None:
    raise ValueError(message)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_utc(value: object, field: str) -> dt.datetime:
    if not isinstance(value, str):
        fail(f"{field} must be an ISO-8601 UTC timestamp")
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        fail(f"{field} is not ISO-8601: {error}")
    if parsed.tzinfo is None:
        fail(f"{field} must include a timezone")
    return parsed.astimezone(dt.timezone.utc)


def safe_relative_file(root: Path, value: object, field: str) -> Path:
    if not isinstance(value, str) or not value:
        fail(f"{field} must be a non-empty relative path")
    candidate = Path(value)
    if candidate.is_absolute() or ".." in candidate.parts:
        fail(f"{field} must stay inside the manifest directory")
    resolved_root = root.resolve()
    resolved_file = (root / candidate).resolve()
    if resolved_root not in resolved_file.parents:
        fail(f"{field} escapes the manifest directory")
    if not resolved_file.is_file():
        fail(f"{field} does not exist: {value}")
    return resolved_file


def current_commit(repo: Path) -> str:
    return subprocess.check_output(
        ["git", "-C", str(repo), "rev-parse", "HEAD"], text=True
    ).strip()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument(
        "--repo",
        type=Path,
        default=Path.cwd(),
        help="repository whose HEAD must equal source_commit (default: cwd)",
    )
    args = parser.parse_args()

    manifest_path = args.manifest.resolve()
    with manifest_path.open(encoding="utf-8") as source:
        manifest = json.load(source)
    if not isinstance(manifest, dict):
        fail("manifest root must be an object")
    missing = REQUIRED_TOP_LEVEL - manifest.keys()
    if missing:
        fail(f"manifest is missing fields: {', '.join(sorted(missing))}")
    if manifest.get("schema_version") != 1:
        fail("schema_version must be 1")
    if manifest.get("platform") not in {"android", "macos", "windows"}:
        fail("platform must be android, macos, or windows")
    if not isinstance(manifest["source_commit"], str) or len(manifest["source_commit"]) != 40:
        fail("source_commit must be a full 40-character Git SHA")
    if current_commit(args.repo.resolve()) != manifest["source_commit"]:
        fail("source_commit does not match the repository HEAD being released")

    candidate = manifest["candidate"]
    if not isinstance(candidate, dict):
        fail("candidate must be an object")
    missing_candidate = REQUIRED_CANDIDATE - candidate.keys()
    if missing_candidate:
        fail(f"candidate is missing fields: {', '.join(sorted(missing_candidate))}")
    candidate_path = safe_relative_file(manifest_path.parent, candidate["path"], "candidate.path")
    candidate_hash = sha256_file(candidate_path)
    if candidate_hash != candidate["sha256"]:
        fail("candidate.sha256 does not match the candidate file")
    if manifest["installed_package_sha256"] != candidate_hash:
        fail("installed_package_sha256 must equal the verified candidate SHA-256")
    built_at = parse_utc(candidate["built_at"], "candidate.built_at")

    screenshots = manifest["screenshots"]
    if not isinstance(screenshots, list) or not screenshots:
        fail("screenshots must be a non-empty array")
    for index, screenshot in enumerate(screenshots):
        prefix = f"screenshots[{index}]"
        if not isinstance(screenshot, dict):
            fail(f"{prefix} must be an object")
        missing_screenshot = REQUIRED_SCREENSHOT - screenshot.keys()
        if missing_screenshot:
            fail(f"{prefix} is missing fields: {', '.join(sorted(missing_screenshot))}")
        screenshot_path = safe_relative_file(manifest_path.parent, screenshot["path"], f"{prefix}.path")
        if sha256_file(screenshot_path) != screenshot["sha256"]:
            fail(f"{prefix}.sha256 does not match its screenshot")
        if parse_utc(screenshot["captured_at"], f"{prefix}.captured_at") < built_at:
            fail(f"{prefix} predates candidate.built_at and cannot be current evidence")

    if not isinstance(manifest["environment"], dict) or not manifest["environment"]:
        fail("environment must identify the capture device and operating system")
    print(f"release evidence verified: {manifest['release_tag']} ({manifest['platform']})")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"release evidence rejected: {error}", file=sys.stderr)
        raise SystemExit(1)
