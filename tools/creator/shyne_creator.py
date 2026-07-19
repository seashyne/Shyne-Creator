#!/usr/bin/env python3
"""Shyne Creator command-line helper: scaffold, validate, and inspect Avatar projects."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ID_PATTERN = re.compile(r"^[a-z0-9][a-z0-9_.-]{0,63}$")
MAX_UNPACKED_BYTES = 64 * 1024 * 1024
TEXTURE_EXTENSIONS = {".png"}
ALLOWED_PERMISSIONS = {"particle", "sound", "camera", "microphone", "command", "hud_render", "world_render"}
SUPPORTED_APIS = {"auto", "latest", "1.0", "1.1"}
API_MODULES = {
    "animation": "1.1", "core": "1.1", "diagnostics": "1.1", "input": "1.0",
    "minecraft": "1.0", "modules": "1.0", "network": "1.0", "permissions": "1.1",
    "render": "1.1", "scheduler": "1.1", "ui": "1.1", "vector": "1.1",
}
STANDARD_1_0_MODULES = {"animation", "core", "diagnostics", "input", "minecraft", "modules", "network", "render", "ui", "vector"}


def version_pair(value: str) -> tuple[int, int]:
    parts = value.strip().split(".")
    if not 1 <= len(parts) <= 3:
        raise ValueError(f"invalid API version: {value}")
    return int(parts[0]), int(parts[1]) if len(parts) > 1 else 0


def requirement_matches(available: str, requirement: str) -> bool:
    requirement = requirement.strip()
    if requirement in {"", "*", "latest"}:
        return True
    if requirement.startswith("^"):
        minimum = version_pair(requirement[1:])
        current = version_pair(available)
        return current[0] == minimum[0] and current >= minimum
    operator = "="
    for candidate in (">=", "<=", ">", "<", "="):
        if requirement.startswith(candidate):
            operator, requirement = candidate, requirement[len(candidate):].strip()
            break
    current, target = version_pair(available), version_pair(requirement)
    return {
        ">=": current >= target,
        "<=": current <= target,
        ">": current > target,
        "<": current < target,
        "=": current == target,
    }[operator]


def safe_file(root: Path, relative: str) -> Path:
    target = (root / relative).resolve()
    if root.resolve() not in target.parents and target != root.resolve():
        raise ValueError(f"path escapes Avatar folder: {relative}")
    return target


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def validate(root: Path) -> dict:
    root = root.resolve()
    errors: list[str] = []
    warnings: list[str] = []
    manifest_path = root / "avatar.json"
    manifest: dict = {}
    if not manifest_path.is_file():
        errors.append("missing avatar.json")
    else:
        try:
            manifest = load_json(manifest_path)
        except Exception as exc:
            errors.append(f"invalid avatar.json: {exc}")

    default_id = re.sub(r"[^a-z0-9_.-]+", "_", root.name.lower()).lstrip("._-") or "avatar"
    avatar_id = str(manifest.get("id", default_id))[:64]
    if manifest and not ID_PATTERN.fullmatch(avatar_id):
        errors.append("id must use 1-64 lowercase letters, numbers, dot, dash, or underscore")
    if manifest and not str(manifest.get("name", "")).strip():
        errors.append("name is required")
    if manifest and "api_version" in manifest:
        try:
            if int(manifest["api_version"]) != 1:
                errors.append("api_version must be 1")
        except (TypeError, ValueError):
            errors.append("api_version must be the integer 1")
    selected_api = str(manifest.get("api", "latest")).strip().lower()
    if selected_api not in SUPPORTED_APIS:
        errors.append("api must be auto, latest, 1.0, or 1.1")
    effective_api = "1.0" if "api_version" in manifest and "api" not in manifest else ("1.1" if selected_api in {"auto", "latest"} else selected_api)
    requirements = manifest.get("requires", {})
    if requirements and not isinstance(requirements, dict):
        errors.append("requires must be an object")
        requirements = {}
    for module, requirement in requirements.items():
        if module not in API_MODULES:
            errors.append(f"unknown API module: {module}")
        elif not isinstance(requirement, str):
            errors.append(f"API requirement for {module} must be a string")
        elif effective_api == "1.0" and module not in STANDARD_1_0_MODULES:
            errors.append(f"API module {module} is unavailable in Standard 1.0")
        else:
            try:
                available = "1.0" if effective_api == "1.0" else API_MODULES[module]
                if not requirement_matches(available, requirement):
                    errors.append(f"API module {module} requires {requirement} but {available} is available")
            except (TypeError, ValueError):
                errors.append(f"invalid API requirement for {module}: {requirement}")
    declared_permissions = {str(value).strip().lower() for value in manifest.get("permissions", [])}
    unknown_permissions = sorted(declared_permissions - ALLOWED_PERMISSIONS)
    if unknown_permissions:
        errors.append("unknown permissions: " + ", ".join(unknown_permissions))

    for field, default in (("main", "script.lua"), ("model", "model.bbmodel")):
        relative = str(manifest.get(field, default))
        try:
            if not safe_file(root, relative).is_file():
                errors.append(f"missing {field}: {relative}")
        except ValueError as exc:
            errors.append(str(exc))

    model_path = root / str(manifest.get("model", "model.bbmodel"))
    model = {}
    if model_path.is_file():
        try:
            model = load_json(model_path)
            resolution = model.get("resolution", {})
            for axis in ("width", "height"):
                size = int(resolution.get(axis, 0))
                if size < 1 or size > 4096:
                    errors.append(f"model resolution {axis} must be 1-4096")
            if len(model.get("elements", [])) > 4096:
                errors.append("model has more than 4096 elements")
            if len(model.get("animations", [])) > 256:
                errors.append("model has more than 256 animations")
        except Exception as exc:
            errors.append(f"invalid model JSON: {exc}")

    files = [path for path in root.rglob("*") if path.is_file()]
    folded: dict[str, Path] = {}
    total_bytes = 0
    for path in files:
        relative = path.relative_to(root).as_posix()
        folded_name = relative.casefold()
        if folded_name in folded:
            errors.append(f"duplicate path ignoring case: {folded[folded_name].relative_to(root)} and {relative}")
        folded[folded_name] = path
        total_bytes += path.stat().st_size
        if path.suffix.lower() in TEXTURE_EXTENSIONS and path.stat().st_size > 16 * 1024 * 1024:
            warnings.append(f"large texture file: {relative}")
    if total_bytes > MAX_UNPACKED_BYTES:
        errors.append("Avatar folder exceeds 64 MiB")

    lua_source = "\n".join(path.read_text(encoding="utf-8", errors="replace") for path in files if path.suffix.lower() == ".lua")
    required_permissions: set[str] = set()
    permission_patterns = {
        "particle": r"\bparticle\.spawn\s*\(",
        "sound": r"\bsound\.play\s*\(",
        "camera": r"\bavatar\.camera\.",
        "microphone": r"(?:\bmicrophone\.|\bevents\.on\s*\(\s*[\"']microphone[\"'])",
        "command": r"\bminecraft\.command\s*\(",
        "hud_render": r"\brender\.(?:text|item|block|sprite|line)\s*\(",
        "world_render": r"\brender\.world\s*\(",
    }
    for permission, pattern in permission_patterns.items():
        if re.search(pattern, lua_source):
            required_permissions.add(permission)
    missing_permissions = sorted(required_permissions - declared_permissions)
    if missing_permissions:
        errors.append("script uses undeclared permissions: " + ", ".join(missing_permissions))

    discovered_textures = sorted(path.relative_to(root).as_posix() for path in files if path.suffix.lower() in TEXTURE_EXTENSIONS)
    declared = sorted(str(value).replace("\\", "/") for value in manifest.get("textures", []))
    undeclared = sorted(set(discovered_textures) - set(declared))
    if undeclared:
        warnings.append("textures are auto-discovered; optional manifest list omits: " + ", ".join(undeclared))

    return {
        "valid": not errors,
        "id": avatar_id,
        "files": len(files),
        "bytes": total_bytes,
        "textures": discovered_textures,
        "bones": len(model.get("outliner", [])),
        "cubes": len(model.get("elements", [])),
        "animations": len(model.get("animations", [])),
        "errors": errors,
        "warnings": warnings,
        "permissions": sorted(declared_permissions),
        "required_permissions": sorted(required_permissions),
    }


def create(root: Path, avatar_id: str, name: str) -> None:
    if not ID_PATTERN.fullmatch(avatar_id):
        raise SystemExit("invalid --id; use lowercase letters, numbers, dot, dash, or underscore")
    root.mkdir(parents=True, exist_ok=False)
    manifest = {
        "api": "latest",
        "requires": {"render": ">=1.1", "scheduler": ">=1.1"},
        "id": avatar_id,
        "name": name,
        "version": "1.0.0",
        "main": "script.lua",
        "model": "model.bbmodel",
        "replace_vanilla": False,
        "online_sync": True,
        "permissions": ["hud_render"],
    }
    model = {
        "meta": {"format_version": "4.10", "model_format": "free"},
        "name": name,
        "resolution": {"width": 16, "height": 16},
        "textures": [],
        "elements": [],
        "outliner": [],
        "animations": [],
    }
    script = '''-- Shyne-native Avatar entry point\n\nevents.on("entity_init", function()\n  render.text("welcome", { text = "Hello from Shyne", x = 12, y = 12, color = 0xFF55FFFF, shadow = true })\nend)\n\nevents.on("avatar_unload", function()\n  render.clear()\nend)\n'''
    (root / "avatar.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (root / "model.bbmodel").write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (root / "script.lua").write_text(script, encoding="utf-8")
    (root / "README.md").write_text(f"# {name}\n\nสร้างด้วย Shyne Creator tools แล้ว เปิด `model.bbmodel` ใน Blockbench เพื่อเริ่มทำโมเดล\n", encoding="utf-8")


def print_report(report: dict, as_json: bool) -> None:
    if as_json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return
    print("VALID" if report["valid"] else "INVALID", report.get("id", ""))
    print(f"files={report['files']} size={report['bytes']} bytes textures={len(report['textures'])} cubes={report['cubes']} animations={report['animations']}")
    for warning in report["warnings"]:
        print("WARNING:", warning)
    for error in report["errors"]:
        print("ERROR:", error)


def main() -> None:
    parser = argparse.ArgumentParser(prog="shyne-creator", description="Create and validate Shyne-native Avatars")
    commands = parser.add_subparsers(dest="command", required=True)
    new = commands.add_parser("new", help="create a minimal Avatar project")
    new.add_argument("folder", type=Path)
    new.add_argument("--id", required=True)
    new.add_argument("--name", required=True)
    check = commands.add_parser("validate", help="validate an Avatar folder")
    check.add_argument("folder", type=Path)
    check.add_argument("--json", action="store_true")
    inspect = commands.add_parser("inspect", help="print machine-readable Avatar stats")
    inspect.add_argument("folder", type=Path)

    args = parser.parse_args()
    if args.command == "new":
        create(args.folder, args.id, args.name)
        print(f"Created {args.folder}")
        print_report(validate(args.folder), False)
        return
    report = validate(args.folder)
    print_report(report, args.command == "inspect" or args.json)
    if not report["valid"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
