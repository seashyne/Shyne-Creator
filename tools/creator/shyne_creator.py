#!/usr/bin/env python3
"""Shyne Creator command-line helper: scaffold, validate, and inspect Avatar projects."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import uuid
from pathlib import Path

ID_PATTERN = re.compile(r"^[a-z0-9][a-z0-9_.-]{0,63}$")
MAX_UNPACKED_BYTES = 64 * 1024 * 1024
MAX_SYNCED_SCHEMA_BYTES = 256 * 1024
MAX_SYNCED_SCHEMA_DEPTH = 8
MAX_SYNCED_SCHEMA_RULES = 512
TEXTURE_EXTENSIONS = {".png"}
ALLOWED_PERMISSIONS = {"particle", "sound", "camera", "microphone", "command", "hud_render", "world_render"}
LATEST_API = "2.0"
SUPPORTED_APIS = {"auto", "latest", LATEST_API}


def suggested_avatar_id(folder_name: str) -> str:
    raw = folder_name.strip().lower()
    slug = re.sub(r"[^a-z0-9_.-]+", "_", raw).strip("._-")
    if any(ord(char) > 127 for char in raw) or len(slug) > 64:
        value = 2166136261
        utf16 = raw.encode("utf-16-le")
        for index in range(0, len(utf16), 2):
            code_unit = utf16[index] | (utf16[index + 1] << 8)
            value = ((value ^ code_unit) * 16777619) & 0xFFFFFFFF
        return f"{(slug or 'avatar')[:55]}_{value:08x}"
    return slug or "avatar"


API_MODULES = {
    "animation": "1.1", "core": "1.1", "diagnostics": "1.1", "easy": "1.0",
    "events": "2.0", "input": "1.0",
    "minecraft": "1.0", "modules": "1.0", "network": "1.0", "permissions": "1.1",
    "render": "1.3", "scheduler": "1.1", "ui": "1.1", "transform": "1.0", "vector": "1.1",
    "rig": "1.3", "behavior": "2.0",
}
PROFILES = {"accessory", "full_body", "custom"}
PROFILE_ALIASES = {"merling": "custom", "aquatic": "custom"}
BEHAVIOR_PRESETS = {"auto", "manual", "off"}
BEHAVIOR_STATES = {"idle", "walk", "sprint", "swim", "crouch", "sleep", "fly", "sit"}
SYNCED_SCHEMA_TYPES = {"object", "array", "string", "number", "integer", "boolean", "null"}


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


def validate_synced_schema_rule(
    rule: object,
    path: str,
    depth: int,
    counter: list[int],
    errors: list[str],
) -> None:
    """Mirror the deterministic JSON-Schema subset implemented by the game runtime."""
    if not isinstance(rule, dict):
        errors.append(f"{path} must be an object rule")
        return
    counter[0] += 1
    if depth > MAX_SYNCED_SCHEMA_DEPTH or counter[0] > MAX_SYNCED_SCHEMA_RULES:
        errors.append("synced_schema is too deeply nested or complex")
        return
    if "$ref" in rule:
        errors.append(f"{path} does not support $ref")

    raw_types = rule.get("type")
    if raw_types is None:
        types: list[object] = []
    elif isinstance(raw_types, str):
        types = [raw_types]
    elif isinstance(raw_types, list) and all(isinstance(value, str) for value in raw_types):
        types = raw_types
    else:
        errors.append(f"{path}.type must be a string or array of strings")
        types = []
    unsupported = sorted({str(value) for value in types} - SYNCED_SCHEMA_TYPES)
    if unsupported:
        errors.append(f"{path}.type contains unsupported values: {', '.join(unsupported)}")

    if "enum" in rule and not isinstance(rule["enum"], list):
        errors.append(f"{path}.enum must be an array")
    for key in ("minimum", "maximum"):
        if key in rule and (
            isinstance(rule[key], bool)
            or not isinstance(rule[key], (int, float))
            or not math.isfinite(float(rule[key]))
        ):
            errors.append(f"{path}.{key} must be a finite number")
    for key in ("minLength", "maxLength", "minItems", "maxItems"):
        if key in rule and (
            isinstance(rule[key], bool)
            or not isinstance(rule[key], int)
            or rule[key] < 0
        ):
            errors.append(f"{path}.{key} must be a non-negative integer")

    properties = rule.get("properties")
    if properties is not None:
        if not isinstance(properties, dict):
            errors.append(f"{path}.properties must be an object")
        else:
            for key, child in properties.items():
                validate_synced_schema_rule(child, f"{path}.properties.{key}", depth + 1, counter, errors)

    additional = rule.get("additionalProperties")
    if additional is not None and not isinstance(additional, bool):
        if isinstance(additional, dict):
            validate_synced_schema_rule(additional, f"{path}.additionalProperties", depth + 1, counter, errors)
        else:
            errors.append(f"{path}.additionalProperties must be a boolean or object rule")

    if "items" in rule:
        validate_synced_schema_rule(rule["items"], f"{path}.items", depth + 1, counter, errors)


def validate_synced_schema(root: Path, relative: object, errors: list[str]) -> None:
    if not isinstance(relative, str) or not relative.strip():
        errors.append("synced_schema must name a JSON file inside the Avatar folder")
        return
    try:
        path = safe_file(root, relative.strip())
    except ValueError as exc:
        errors.append(str(exc))
        return
    if not path.is_file():
        errors.append(f"missing synced_schema: {relative}")
        return
    size = path.stat().st_size
    if size < 1 or size > MAX_SYNCED_SCHEMA_BYTES:
        errors.append("synced_schema must be between 1 byte and 256 KiB")
        return
    try:
        schema = load_json(path)
    except Exception as exc:
        errors.append(f"invalid synced_schema JSON: {exc}")
        return
    if not isinstance(schema, dict):
        errors.append("synced_schema root must be an object")
        return
    raw_type = schema.get("type")
    root_types = [raw_type] if isinstance(raw_type, str) else raw_type
    if isinstance(root_types, list) and root_types and "object" not in root_types:
        errors.append("synced_schema root type must allow object")
    validate_synced_schema_rule(schema, "synced_schema", 0, [0], errors)


def animation_names(value: object, field: str, errors: list[str]) -> list[str]:
    if isinstance(value, str):
        names = [value.strip()] if value.strip() else []
    elif isinstance(value, list):
        names = [item.strip() for item in value if isinstance(item, str) and item.strip()]
        if any(not isinstance(item, str) for item in value):
            errors.append(f"{field} may contain only animation-name strings")
        if len(names) != len(set(names)):
            errors.append(f"{field} contains duplicate animation names")
    else:
        errors.append(f"{field} must be an animation name or array of names")
        return []
    if not names:
        errors.append(f"{field} must contain at least one animation name")
    return names


def count_model_bones(model: dict) -> int:
    """Count real groups in both inline V4 and UUID-referenced V5 outliners."""
    seen: set[str] = set()

    def visit(value: object) -> None:
        if not isinstance(value, dict):
            return
        children = value.get("children")
        if not isinstance(children, list):
            return
        key = str(value.get("uuid") or f"inline:{id(value)}")
        if key in seen:
            return
        seen.add(key)
        for child in children:
            visit(child)

    outliner = model.get("outliner", [])
    if isinstance(outliner, list):
        for root in outliner:
            visit(root)
    if seen:
        return len(seen)
    groups = model.get("groups")
    return len({str(group.get("uuid", index)) for index, group in enumerate(groups) if isinstance(group, dict)}) if isinstance(groups, list) else 0


def normalized_humanoid_name(value: object) -> str:
    normalized = re.sub(r"[^a-z0-9]", "", str(value or "").lower())
    return {
        "head": "Head",
        "body": "Body",
        "torso": "Body",
        "leftarm": "LeftArm",
        "rightarm": "RightArm",
        "leftleg": "LeftLeg",
        "rightleg": "RightLeg",
    }.get(normalized, "")


def top_level_model_bones(model: dict) -> list[dict]:
    """Resolve V4 inline and V5 UUID-referenced top-level outliner groups."""
    groups = model.get("groups", [])
    definitions = {
        str(group.get("uuid")): group
        for group in groups
        if isinstance(group, dict) and group.get("uuid") is not None
    } if isinstance(groups, list) else {}
    roots: list[dict] = []
    outliner = model.get("outliner", [])
    if not isinstance(outliner, list):
        return roots
    for entry in outliner:
        if not isinstance(entry, dict):
            continue
        definition = definitions.get(str(entry.get("uuid")), {})
        combined = dict(definition)
        combined.update(entry)
        if combined.get("name") is not None:
            roots.append(combined)
    return roots


def validate_full_body_humanoid(profile: str, model: dict, warnings: list[str]) -> None:
    if profile != "full_body":
        return
    roots: dict[str, dict] = {}
    for bone in top_level_model_bones(model):
        key = normalized_humanoid_name(bone.get("name"))
        if key and key not in roots:
            roots[key] = bone
    expected = ("Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg")
    missing = [name for name in expected if name not in roots]
    if missing:
        warnings.append(
            "full_body Minecraft pose needs top-level humanoid bones: "
            + ", ".join(missing)
            + "; zero authored animations is valid once all six roots exist"
        )
        return
    attached = [name for name, bone in roots.items() if str(bone.get("parent_type", "")).strip()]
    if attached:
        warnings.append(
            "full_body humanoid roots must not use parent_type (reserved for accessories): "
            + ", ".join(attached)
        )

    expected_pivots = {
        "Head": (0.0, 24.0, 0.0),
        "Body": (0.0, 24.0, 0.0),
        "LeftArm": (5.0, 22.0, 0.0),
        "RightArm": (5.0, 22.0, 0.0),
        "LeftLeg": (1.9, 12.0, 0.0),
        "RightLeg": (1.9, 12.0, 0.0),
    }
    bad_pivots: list[str] = []
    for name, expected_pivot in expected_pivots.items():
        origin = roots[name].get("origin", [])
        if not isinstance(origin, list) or len(origin) < 3:
            bad_pivots.append(name)
            continue
        try:
            actual = (abs(float(origin[0])), float(origin[1]), float(origin[2]))
        except (TypeError, ValueError):
            bad_pivots.append(name)
            continue
        if any(abs(actual[index] - expected_pivot[index]) > 0.25 for index in range(3)):
            bad_pivots.append(name)
    if bad_pivots:
        warnings.append("full_body humanoid roots use non-standard Minecraft pivots: " + ", ".join(bad_pivots))


def validate_behavior(
    behavior: object,
    model_animations: set[str],
    errors: list[str],
    warnings: list[str],
) -> None:
    if behavior is None:
        return
    if isinstance(behavior, str):
        if behavior.strip().lower() not in BEHAVIOR_PRESETS:
            errors.append("behavior preset must be auto, manual, or off")
        return
    if not isinstance(behavior, dict):
        errors.append("behavior must be a preset name or object")
        return

    allowed = {"preset", "autoplay", "animations", "blend_ticks", "blink"}
    unknown = sorted(set(behavior) - allowed)
    if unknown:
        errors.append("unknown behavior fields: " + ", ".join(unknown))

    preset = behavior.get("preset", "auto")
    if not isinstance(preset, str) or preset.strip().lower() not in BEHAVIOR_PRESETS:
        errors.append("behavior.preset must be auto, manual, or off")

    autoplay = behavior.get("autoplay", [])
    if not isinstance(autoplay, list):
        errors.append("behavior.autoplay must be an array of animation names")
    else:
        names = animation_names(autoplay, "behavior.autoplay", errors) if autoplay else []
        for name in names:
            if name not in model_animations:
                errors.append(f"behavior.autoplay references missing animation: {name}")

    mappings = behavior.get("animations", {})
    if not isinstance(mappings, dict):
        errors.append("behavior.animations must be an object")
    else:
        for state, value in mappings.items():
            if not isinstance(state, str) or not state.strip():
                errors.append("behavior animation-state names must be non-empty strings")
                continue
            if state.strip().lower() not in BEHAVIOR_STATES:
                errors.append(f"unsupported behavior animation state: {state}")
                continue
            names = animation_names(value, f"behavior.animations.{state}", errors)
            existing = [name for name in names if name in model_animations]
            if names and not existing:
                errors.append(f"behavior animation state '{state}' cannot resolve any model animation")
            for name in names:
                if name not in model_animations and existing:
                    warnings.append(f"behavior animation state '{state}' will skip missing fallback: {name}")

    blend_ticks = behavior.get("blend_ticks", 5)
    if isinstance(blend_ticks, bool) or not isinstance(blend_ticks, int) or not 0 <= blend_ticks <= 1200:
        errors.append("behavior.blend_ticks must be an integer from 0 to 1200")

    blink = behavior.get("blink")
    if blink is None or isinstance(blink, bool):
        return
    if isinstance(blink, str):
        names = animation_names(blink, "behavior.blink", errors)
    elif isinstance(blink, dict):
        blink_unknown = sorted(set(blink) - {"enabled", "animation", "min_ticks", "max_ticks"})
        if blink_unknown:
            errors.append("unknown behavior.blink fields: " + ", ".join(blink_unknown))
        if "enabled" in blink and not isinstance(blink["enabled"], bool):
            errors.append("behavior.blink.enabled must be a boolean")
        names = animation_names(blink.get("animation"), "behavior.blink.animation", errors) if "animation" in blink else []
        minimum = blink.get("min_ticks", 50)
        maximum = blink.get("max_ticks", 110)
        if isinstance(minimum, bool) or not isinstance(minimum, int) or not 1 <= minimum <= 72000:
            errors.append("behavior.blink.min_ticks must be an integer from 1 to 72000")
        if isinstance(maximum, bool) or not isinstance(maximum, int) or not 1 <= maximum <= 72000:
            errors.append("behavior.blink.max_ticks must be an integer from 1 to 72000")
        if isinstance(minimum, int) and not isinstance(minimum, bool) and isinstance(maximum, int) and not isinstance(maximum, bool) and maximum < minimum:
            errors.append("behavior.blink.max_ticks must be greater than or equal to min_ticks")
    else:
        errors.append("behavior.blink must be a boolean, animation name, or object")
        return
    if names and not any(name in model_animations for name in names):
        errors.append("behavior.blink cannot resolve any model animation")


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
    declared_standard = str(manifest.get("standard", "2.0")).strip()
    if manifest and declared_standard != "2.0":
        errors.append("standard must be 2.0")
    declared_profile = str(manifest.get("profile", "accessory")).strip().lower()
    profile = PROFILE_ALIASES.get(declared_profile, declared_profile)
    if manifest and declared_profile in PROFILE_ALIASES:
        warnings.append(f"profile '{declared_profile}' is a legacy alias; use '{profile}'")
    if manifest and profile not in PROFILES:
        errors.append("profile must be accessory, full_body, or custom")
    if manifest and "api_version" in manifest:
        errors.append('api_version is not supported by Standard 2.0; use api "2.0"')
    selected_api = str(manifest.get("api", "latest")).strip().lower()
    if selected_api not in SUPPORTED_APIS:
        errors.append("api must be auto, latest, or 2.0")
    effective_api = LATEST_API if selected_api in {"auto", "latest"} else selected_api
    requirements = manifest.get("requires", {})
    if requirements and not isinstance(requirements, dict):
        errors.append("requires must be an object")
        requirements = {}
    for module, requirement in requirements.items():
        if module not in API_MODULES:
            errors.append(f"unknown API module: {module}")
        elif not isinstance(requirement, str):
            errors.append(f"API requirement for {module} must be a string")
        else:
            try:
                available = API_MODULES[module]
                if not requirement_matches(available, requirement):
                    errors.append(f"API module {module} requires {requirement} but {available} is available")
            except (TypeError, ValueError):
                errors.append(f"invalid API requirement for {module}: {requirement}")
    raw_permissions = manifest.get("permissions", [])
    if not isinstance(raw_permissions, list):
        errors.append("permissions must be an array")
        raw_permissions = []
    declared_permissions = {str(value).strip().lower() for value in raw_permissions}
    unknown_permissions = sorted(declared_permissions - ALLOWED_PERMISSIONS)
    if unknown_permissions:
        errors.append("unknown permissions: " + ", ".join(unknown_permissions))

    if "main" in manifest:
        relative = str(manifest.get("main", "")).strip()
        if not relative:
            errors.append("main must be omitted or name a Lua entry file")
        else:
            try:
                if not safe_file(root, relative).is_file():
                    errors.append(f"missing main: {relative}")
            except ValueError as exc:
                errors.append(str(exc))

    for field, default in (("model", "model.bbmodel"),):
        relative = str(manifest.get(field, default)).strip()
        try:
            if not safe_file(root, relative).is_file():
                errors.append(f"missing {field}: {relative}")
        except ValueError as exc:
            errors.append(str(exc))

    if "synced_schema" in manifest:
        validate_synced_schema(root, manifest["synced_schema"], errors)

    try:
        model_path = safe_file(root, str(manifest.get("model", "model.bbmodel")))
    except ValueError:
        model_path = root / "__invalid_model_path__"
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
            if count_model_bones(model) > 4096:
                errors.append("model has more than 4096 bones")
            if len(model.get("animations", [])) > 256:
                errors.append("model has more than 256 animations")
        except Exception as exc:
            errors.append(f"invalid model JSON: {exc}")

    model_animation_names = {
        str(animation.get("name", ""))
        for animation in model.get("animations", [])
        if isinstance(animation, dict) and str(animation.get("name", ""))
    }
    validate_behavior(manifest.get("behavior"), model_animation_names, errors, warnings)
    validate_full_body_humanoid(profile, model, warnings)

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

    lua_source = ""
    if "main" in manifest:
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

    discovered_textures = sorted(
        relative
        for path in files
        if path.suffix.lower() in TEXTURE_EXTENSIONS
        for relative in [path.relative_to(root).as_posix()]
        if relative.casefold() != "avatar.png" and not relative.casefold().startswith("outfit/")
    )
    declared = sorted(str(value).replace("\\", "/") for value in manifest.get("textures", []))
    undeclared = sorted(set(discovered_textures) - set(declared))
    if undeclared:
        warnings.append("textures are auto-discovered; optional manifest list omits: " + ", ".join(undeclared))

    elements = model.get("elements", []) if isinstance(model.get("elements", []), list) else []
    cube_count = sum(1 for element in elements if isinstance(element, dict) and element.get("type", "cube") == "cube")
    mesh_count = sum(1 for element in elements if isinstance(element, dict) and element.get("type") == "mesh")

    return {
        "valid": not errors,
        "id": avatar_id,
        "standard": declared_standard,
        "api": effective_api,
        "profile": profile,
        "files": len(files),
        "bytes": total_bytes,
        "textures": discovered_textures,
        "bones": count_model_bones(model),
        "cubes": cube_count,
        "meshes": mesh_count,
        "animations": len(model.get("animations", [])),
        "errors": errors,
        "warnings": warnings,
        "permissions": sorted(declared_permissions),
        "required_permissions": sorted(required_permissions),
    }


def create(root: Path, avatar_id: str | None, name: str | None, with_lua: bool = False) -> None:
    display_name = (name or re.sub(r"[_.-]+", " ", root.name).strip() or "My Avatar").strip()
    if not display_name or len(display_name) > 96:
        raise SystemExit("Avatar name must contain 1-96 characters")
    if avatar_id is not None and not ID_PATTERN.fullmatch(avatar_id):
        raise SystemExit("invalid --id; use lowercase letters, numbers, dot, dash, or underscore")
    root.mkdir(parents=True, exist_ok=False)
    manifest = {"name": display_name}
    if avatar_id is not None:
        manifest["id"] = avatar_id
    else:
        runtime_id = re.sub(r"[^a-z0-9_.-]+", "_", root.name.lower()).lstrip("._-")[:64] or "avatar"
        suggested_id = suggested_avatar_id(root.name)
        if suggested_id != runtime_id:
            manifest["id"] = suggested_id
    if with_lua:
        manifest["main"] = "script.lua"
        manifest["api"] = LATEST_API
    root_bone_id = str(uuid.uuid4())
    model = {
        "meta": {"format_version": "4.10", "model_format": "free"},
        "name": display_name,
        "resolution": {"width": 16, "height": 16},
        "textures": [],
        "elements": [],
        "outliner": [{
            "name": "HeadAccessory",
            "origin": [0, 24, 0],
            "rotation": [0, 0, 0],
            "uuid": root_bone_id,
            "parent_type": "Head",
            "role": "accessory",
            "export": True,
            "visibility": True,
            "isOpen": True,
            "children": [],
        }],
        "animations": [],
    }
    (root / "avatar.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (root / "model.bbmodel").write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if with_lua:
        (root / "script.lua").write_text(
            "-- Optional custom behavior. Idle, Walk and Blink work without Lua.\n"
            "events.on(\"entity_init\", function()\n"
            "  -- Add custom behavior here.\n"
            "end)\n",
            encoding="utf-8",
        )
    (root / "README.md").write_text(
        f"# {display_name}\n\n"
        "โปรเจกต์ Shyne Standard 2.0 แบบ model-first: เปิด `model.bbmodel` ใน Blockbench "
        "แล้ววางชิ้นส่วนไว้ใต้ `HeadAccessory` ได้ทันที ไม่ต้องเขียน Lua\n\n"
        "ตั้งชื่อ animation เช่น `Idle`, `Walk`, `Sprint`, `Swim`, `Crouch`, `Sleep`, "
        "`Elytra` หรือ `Blink` เพื่อให้ Shyne ผูกพฤติกรรมให้อัตโนมัติ\n"
        + ("\nถ้าต้องการพฤติกรรมพิเศษ ให้แก้ `script.lua` ที่สร้างไว้ให้\n" if with_lua else ""),
        encoding="utf-8",
    )


def print_report(report: dict, as_json: bool) -> None:
    if as_json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return
    print("VALID" if report["valid"] else "INVALID", report.get("id", ""))
    print(
        f"standard={report['standard']} api={report['api']} profile={report['profile']} "
        f"files={report['files']} size={report['bytes']} bytes textures={len(report['textures'])} "
        f"bones={report['bones']} cubes={report['cubes']} meshes={report['meshes']} animations={report['animations']}"
    )
    for warning in report["warnings"]:
        print("WARNING:", warning)
    for error in report["errors"]:
        print("ERROR:", error)


def main() -> None:
    parser = argparse.ArgumentParser(prog="shyne-creator", description="Create and validate Shyne-native Avatars")
    commands = parser.add_subparsers(dest="command", required=True)
    new = commands.add_parser("new", help="create a minimal Avatar project")
    new.add_argument("folder", type=Path)
    new.add_argument("--id", help="optional stable id; defaults to the folder name")
    new.add_argument("--name", help="display name; defaults to the folder name")
    new.add_argument("--lua", action="store_true", help="include an optional script.lua starter")
    check = commands.add_parser("validate", help="validate an Avatar folder")
    check.add_argument("folder", type=Path)
    check.add_argument("--json", action="store_true")
    inspect = commands.add_parser("inspect", help="print machine-readable Avatar stats")
    inspect.add_argument("folder", type=Path)

    args = parser.parse_args()
    if args.command == "new":
        create(args.folder, args.id, args.name, args.lua)
        print(f"Created {args.folder}")
        print_report(validate(args.folder), False)
        return
    report = validate(args.folder)
    print_report(report, args.command == "inspect" or args.json)
    if not report["valid"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
