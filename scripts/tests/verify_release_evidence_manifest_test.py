#!/usr/bin/env python3
"""Public-CLI behavior tests for the release-evidence verifier."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT_PATH = Path(__file__).parents[1] / "verify_release_evidence_manifest.py"
SPEC = importlib.util.spec_from_file_location("release_evidence_verifier", SCRIPT_PATH)
assert SPEC and SPEC.loader
VERIFIER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFIER)


def sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


class ReleaseEvidenceManifestTest(unittest.TestCase):
    source_commit = "a" * 40

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.candidate = self.root / "candidate.apk"
        self.screenshot = self.root / "latest.png"
        self.candidate.write_bytes(b"final candidate package")
        self.screenshot.write_bytes(b"fresh screenshot from final candidate")
        self.manifest = self.root / "manifest.json"

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def payload(self, captured_at: str = "2026-09-15T09:05:00Z") -> dict[str, object]:
        candidate_hash = sha256(self.candidate.read_bytes())
        return {
            "schema_version": 1,
            "release_tag": "android-v1.0.0",
            "platform": "android",
            "source_commit": self.source_commit,
            "candidate": {
                "path": self.candidate.name,
                "sha256": candidate_hash,
                "built_at": "2026-09-15T09:00:00Z",
            },
            "installed_package_sha256": candidate_hash,
            "environment": {"device": "isolated test device", "os": "test OS"},
            "screenshots": [
                {
                    "path": self.screenshot.name,
                    "sha256": sha256(self.screenshot.read_bytes()),
                    "captured_at": captured_at,
                    "description": "fresh post-install capture",
                }
            ],
        }

    def invoke(self, payload: dict[str, object]) -> int:
        self.manifest.write_text(json.dumps(payload), encoding="utf-8")
        with (
            patch.object(VERIFIER, "current_commit", return_value=self.source_commit),
            patch.object(sys, "argv", [str(SCRIPT_PATH), "--manifest", str(self.manifest), "--repo", str(self.root)]),
        ):
            return VERIFIER.main()

    def test_accepts_a_fresh_capture_bound_to_the_final_candidate(self) -> None:
        self.assertEqual(self.invoke(self.payload()), 0)

    def test_rejects_a_screenshot_that_predates_the_candidate(self) -> None:
        with self.assertRaisesRegex(ValueError, "predates candidate.built_at"):
            self.invoke(self.payload(captured_at="2026-09-15T08:59:59Z"))

    def test_rejects_a_screenshot_manifest_with_a_different_installed_hash(self) -> None:
        payload = self.payload()
        payload["installed_package_sha256"] = "b" * 64
        with self.assertRaisesRegex(ValueError, "installed_package_sha256"):
            self.invoke(payload)


if __name__ == "__main__":
    unittest.main()
