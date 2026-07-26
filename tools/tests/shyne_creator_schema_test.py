#!/usr/bin/env python3
"""Regression tests for Shyne Creator synced_schema validation."""

from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TMP_ROOT = ROOT / "build" / "test-tmp"
CREATOR_PATH = ROOT / "tools" / "creator" / "shyne_creator.py"
SPEC = importlib.util.spec_from_file_location("shyne_creator", CREATOR_PATH)
assert SPEC is not None and SPEC.loader is not None
shyne_creator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(shyne_creator)


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class SyncedSchemaValidationTest(unittest.TestCase):
    def temp_avatar_dir(self) -> tempfile.TemporaryDirectory[str]:
        TMP_ROOT.mkdir(parents=True, exist_ok=True)
        return tempfile.TemporaryDirectory(prefix="creator-schema-", dir=TMP_ROOT)

    def make_avatar(self, root: Path) -> None:
        write_json(root / "avatar.json", {
            "standard": "2.0",
            "id": "schema_test",
            "name": "Schema Test",
            "version": "1.0.0",
            "profile": "accessory",
            "model": "model.bbmodel",
            "synced_schema": "synced.schema.json",
        })
        write_json(root / "model.bbmodel", {
            "meta": {"format_version": "4.10", "model_format": "free"},
            "name": "Schema Test",
            "resolution": {"width": 16, "height": 16},
            "textures": [],
            "elements": [],
            "outliner": [],
            "animations": [],
        })

    def test_valid_synced_schema_passes_creator_validation(self) -> None:
        with self.temp_avatar_dir() as tmp:
            root = Path(tmp)
            self.make_avatar(root)
            write_json(root / "synced.schema.json", {
                "type": "object",
                "properties": {
                    "ears": {
                        "type": "object",
                        "properties": {
                            "enabled": {"type": "boolean"},
                            "pose": {"type": "string", "enum": ["idle", "alert"]},
                            "bounce": {"type": "number", "minimum": 0.0, "maximum": 1.0},
                        },
                        "additionalProperties": False,
                    }
                },
                "additionalProperties": False,
            })

            report = shyne_creator.validate(root)

            self.assertTrue(report["valid"], report["errors"])

    def test_custom_profile_is_canonical_and_legacy_name_is_migrated(self) -> None:
        with self.temp_avatar_dir() as tmp:
            root = Path(tmp)
            self.make_avatar(root)
            write_json(root / "synced.schema.json", {"type": "object"})
            manifest = json.loads((root / "avatar.json").read_text(encoding="utf-8"))
            manifest["profile"] = "custom"
            write_json(root / "avatar.json", manifest)

            custom_report = shyne_creator.validate(root)
            self.assertTrue(custom_report["valid"], custom_report["errors"])
            self.assertEqual("custom", custom_report["profile"])

            manifest["profile"] = "merling"
            write_json(root / "avatar.json", manifest)
            legacy_report = shyne_creator.validate(root)

            self.assertTrue(legacy_report["valid"], legacy_report["errors"])
            self.assertEqual("custom", legacy_report["profile"])
            self.assertTrue(any("legacy alias" in warning for warning in legacy_report["warnings"]))

    def test_ref_is_rejected_before_runtime(self) -> None:
        with self.temp_avatar_dir() as tmp:
            root = Path(tmp)
            self.make_avatar(root)
            write_json(root / "synced.schema.json", {
                "type": "object",
                "properties": {"bad": {"$ref": "#/defs/bad"}},
            })

            report = shyne_creator.validate(root)

            self.assertFalse(report["valid"])
            self.assertTrue(any("$ref" in error for error in report["errors"]), report["errors"])

    def test_schema_path_must_stay_inside_avatar_folder(self) -> None:
        with self.temp_avatar_dir() as tmp:
            root = Path(tmp)
            self.make_avatar(root)
            manifest = json.loads((root / "avatar.json").read_text(encoding="utf-8"))
            manifest["synced_schema"] = "../synced.schema.json"
            write_json(root / "avatar.json", manifest)

            report = shyne_creator.validate(root)

            self.assertFalse(report["valid"])
            self.assertTrue(any("path escapes Avatar folder" in error for error in report["errors"]), report["errors"])

    def test_full_body_without_humanoid_roots_is_warned(self) -> None:
        warnings: list[str] = []
        shyne_creator.validate_full_body_humanoid("full_body", {"outliner": []}, warnings)

        self.assertTrue(any("top-level humanoid bones" in warning for warning in warnings), warnings)

    def test_v5_humanoid_roots_need_no_authored_animation(self) -> None:
        names = ("Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg")
        origins = (
            (0, 24, 0), (0, 24, 0), (5, 22, 0),
            (-5, 22, 0), (1.9, 12, 0), (-1.9, 12, 0),
        )
        groups = [
            {"name": name, "uuid": name.lower(), "origin": list(origin), "children": []}
            for name, origin in zip(names, origins)
        ]
        model = {
            "groups": groups,
            "outliner": [{"uuid": group["uuid"], "children": []} for group in groups],
            "animations": [],
        }
        warnings: list[str] = []

        shyne_creator.validate_full_body_humanoid("full_body", model, warnings)

        self.assertEqual([], warnings)


if __name__ == "__main__":
    unittest.main()
