#!/usr/bin/env python3
"""Offline model-maintenance gate. Static consistency is not live Provider acceptance."""
from __future__ import annotations
import argparse
import json
import re
from pathlib import Path

MANIFEST = "protocol/model-service/model-service-v1.json"
MATRIX = "docs/model-service/MODEL_SERVICE_MATRIX.md"
KOTLIN = "app/src/main/java/com/nanzhufeng/ai/"
RUST = "desktop/src-tauri/src/"


def parse_json(text: str):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"duplicate JSON field: {key}")
            result[key] = value
        return result
    def invalid_constant(value):
        raise ValueError(f"invalid JSON constant: {value}")
    return json.loads(text, object_pairs_hook=unique, parse_constant=invalid_constant)


def balanced(text: str, start: int, opening: str, closing: str) -> tuple[str, int]:
    depth, quoted, escaped = 0, False, False
    for i in range(start, len(text)):
        char = text[i]
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char == opening:
            depth += 1
        elif char == closing:
            depth -= 1
            if depth == 0:
                return text[start + 1:i], i + 1
    raise ValueError("unclosed source declaration")


def string_fields(block: str) -> dict:
    return {m[1]: json.loads('"' + m[2] + '"') for m in re.finditer(
        r'\b(\w+)\s*:\s*"((?:[^"\\]|\\.)*)"', block)}


def rust_records(text: str, kind: str) -> list[dict]:
    records = []
    for match in re.finditer(r"\b" + kind + r"\s*\{", text):
        block, _ = balanced(text, match.end() - 1, "{", "}")
        record = string_fields(block)
        if "id" in record:
            selected = re.search(r"\bchat_selectable:\s*(true|false)", block)
            if selected:
                record["chat_selectable"] = selected[1] == "true"
            records.append(record)
    return records


def kotlin_catalog(text: str, relay: str) -> dict[str, str]:
    result = {}
    for match in re.finditer(r"ModelPresetDescriptor\(", text):
        block, _ = balanced(text, match.end() - 1, "(", ")")
        preset = re.search(r"\bid\s*=\s*ModelPresetId\.(\w+)", block)
        if preset:
            usage = re.search(r"\busage\s*=\s*ModelPresetUsage\.(\w+)", block)
            result[preset[1]] = usage[1] if usage else "CHAT"
    for match in re.finditer(r"RelayCoreModelSpec\(ModelPresetId\.(\w+),", relay):
        result[match[1]] = "CHAT"
    return result


def keyed(rows: list[dict], field: str) -> dict:
    result = {}
    for row in rows:
        if row[field] in result:
            raise ValueError(f"duplicate {field}: {row[field]}")
        result[row[field]] = row
    return result


def read_sources(root: Path) -> dict:
    def read(name):
        return (root / name).read_text(encoding="utf-8")
    catalog = read(RUST + "desktop_model_service_v1.rs")
    relay = read(KOTLIN + "domain/RelayCoreModels.kt")
    android = read(KOTLIN + "domain/ModelService.kt")
    profiles = keyed(parse_json(read("app/src/main/assets/model_profiles.json"))["profiles"], "presetId")
    relay_rows = []
    pattern = r'RelayCoreModelSpec\(ModelPresetId\.(\w+), ProviderId\.(\w+), "([^"]+)", "([^"]+)", "([^"]+)", "([^"]+)", ComposerModelSlot\.(\w+)\)'
    for match in re.finditer(pattern, relay):
        relay_rows.append(dict(zip(["presetId", "providerId", "modelId", "modelName", "channel", "multiplier", "tier"], match.groups())))
    esm = read("desktop/src/relaycore-models.mjs")
    start = esm.index("[", esm.index("export const RELAYCORE_MODELS"))
    block, _ = balanced(esm, start, "[", "]")
    relay_esm = keyed(parse_json("[" + block + "]"), "presetId")
    policy_path = parse_json(read(MANIFEST))["autoPolicy"]
    if Path(policy_path).is_absolute() or ".." in Path(policy_path).parts:
        raise ValueError("unsafe Auto policy path")
    auto = parse_json(read(policy_path))
    auto_esm = read("desktop/src/auto-chat-routing.mjs")
    start = auto_esm.index("{", auto_esm.index("export const AUTO_PLANS"))
    block, _ = balanced(auto_esm, start, "{", "}")
    auto_android = {}
    for task in auto["plans"]:
        match = re.search(r"AutoTask\." + task + r"\s+to\s+listOf\(", android)
        if match:
            body, _ = balanced(android, match.end() - 1, "(", ")")
            auto_android[task] = re.findall(r"ModelPresetId\.(\w+)", body)
    fallback = read(KOTLIN + "domain/ResolvedModel.kt")
    fallback_ids = dict(re.findall(r'ModelPresetId\.(\w+)\s+to\s+"([^"]+)"', fallback))
    routing = read(KOTLIN + "domain/ChatModelRouting.kt")
    selections = {}
    for variable, label in [("daily", "日常"), ("deep", "深度")]:
        start = routing.index("(", routing.index("val " + variable + " = listOf"))
        body, _ = balanced(routing, start, "(", ")")
        selections.update({preset: label for preset in re.findall(r"ModelPresetId\.(\w+)", body)})
    selections.update({row["presetId"]: "RelayCore/" + row["tier"] for row in relay_rows})
    return {"providers": keyed(rust_records(catalog, "ProviderDescriptor"), "id"),
            "presets": keyed(rust_records(catalog, "PresetDescriptor"), "id"),
            "android_catalog": kotlin_catalog(android, relay), "profiles": profiles,
            "relay": keyed(relay_rows, "presetId"), "relay_esm": relay_esm,
            "fallback_ids": fallback_ids, "auto": auto, "auto_android": auto_android,
            "auto_esm": parse_json("{" + block + "}"), "selections": selections}


def check(root: Path, manifest: dict, sources: dict, check_matrix=True) -> list[str]:
    errors = []
    def expect(ok, message):
        if not ok:
            errors.append(message)
    expect(set(manifest) == {"schemaVersion", "scope", "providers", "models", "specialModels", "authorities", "exceptions", "autoPolicy", "historicalPolicy"}, "manifest fields differ from v1 schema")
    expect(type(manifest.get("schemaVersion")) is int and manifest["schemaVersion"] == 1, "unsupported manifest version")
    for field in ["scope", "autoPolicy", "historicalPolicy"]:
        expect(isinstance(manifest.get(field), str) and manifest[field].strip(), f"invalid metadata field: {field}")
    schemas = {
        "providers": {"providerId", "platform", "endpoint", "defaultPresetId", "credentialGroup", "offlineProtocol", "webProtocol", "webRoute", "webEndpoint", "nativeMediaWithWeb", "contract"},
        "models": {"presetId", "providerId", "modelId", "displayName", "selection", "exceptionIds"},
        "specialModels": {"presetId", "providerId", "modelId", "usage", "contract"},
        "authorities": {"id", "contract", "androidOwners", "desktopOwners", "verification"},
        "exceptions": {"id", "appliesTo", "contract", "rule"},
    }
    for group, fields in schemas.items():
        rows = manifest.get(group)
        if not isinstance(rows, list) or not rows:
            errors.append(f"{group} must be a nonempty list")
            continue
        for row in rows:
            if not isinstance(row, dict) or set(row) != fields:
                errors.append(f"{group} row fields differ from v1 schema")
                continue
            for field, value in row.items():
                if field == "nativeMediaWithWeb":
                    expect(type(value) is bool, "nativeMediaWithWeb must be boolean")
                elif field in {"exceptionIds", "appliesTo", "androidOwners", "desktopOwners", "verification"}:
                    expect(isinstance(value, list) and all(isinstance(x, str) and x for x in value) and len(value) == len(set(value)), f"invalid list: {group}/{field}")
                else:
                    expect(isinstance(value, str) and value.strip() and not any(ord(c) < 32 for c in value), f"invalid string: {group}/{field}")
    if errors:
        return errors
    try:
        providers = keyed(manifest["providers"], "providerId")
        models = keyed(manifest["models"], "presetId")
        special = keyed(manifest["specialModels"], "presetId")
        keyed(manifest["exceptions"], "id")
        keyed(manifest["authorities"], "id")
    except (KeyError, ValueError) as error:
        return errors + [str(error)]
    expect(not (set(models) & set(special)), "special model also enters chat catalog")
    expect(len({(m["providerId"], m["modelId"]) for m in models.values()}) == len(models), "duplicate deployment identity")
    expect(set(providers) == set(sources["providers"]), "provider namespaces differ from Rust catalog")
    actual = {p for p, row in sources["presets"].items() if row["chat_selectable"]}
    expect(set(models) == actual, "chat model set differs from Rust production catalog")
    expect(set(models) == {p for p, usage in sources["android_catalog"].items() if usage == "CHAT"}, "chat model set differs from Android production catalog")
    protocols = {"CHAT_COMPLETIONS", "MESSAGES", "RESPONSES", "COMPANION"}
    adapter = (root / (KOTLIN + "ai/ChatProviderAdapters.kt")).read_text()
    android_catalog = (root / (KOTLIN + "domain/ModelService.kt")).read_text()
    android_routes = (root / (KOTLIN + "ai/AutomaticWebSearchPolicy.kt")).read_text()
    native = (root / (RUST + "desktop_ordinary_chat_v1.rs")).read_text()
    native_routes = (root / (RUST + "lib.rs")).read_text()
    route_start = native_routes.index("fn ordinary_chat_web_search_route(")
    route_body, _ = balanced(native_routes, native_routes.index("{", route_start), "{", "}")
    native_route_map = {}
    for match in re.finditer(r'((?:"[A-Z_]+"\s*\|\s*)*"[A-Z_]+")\s*=>\s*"([A-Z_]+)"', route_body):
        for provider_id in re.findall(r'"([A-Z_]+)"', match[1]):
            native_route_map[provider_id] = match[2]
    android_route_map = {}
    for match in re.finditer(r"ProviderId\.(\w+)\s*->(.*?)(?=\n\s*ProviderId\.\w+\s*->|\n\s*}\n)", android_routes, re.S):
        android_route_map[match[1]] = re.findall(r"OfficialWebSearchRoute\.(\w+)", match[2])
    default_body = android_catalog.split("fun defaultPreset", 1)[1].split("fun preset", 1)[0]
    provider_body = android_catalog.split("fun providerFor", 1)[1].split("fun defaultPreset", 1)[0]
    for provider_id, row in providers.items():
        expect(set(row) == {"providerId", "platform", "endpoint", "defaultPresetId", "credentialGroup", "offlineProtocol", "webProtocol", "webRoute", "webEndpoint", "nativeMediaWithWeb", "contract"}, f"provider fields invalid: {provider_id}")
        live = sources["providers"].get(provider_id, {})
        expect(row["endpoint"] == live.get("endpoint") and row["defaultPresetId"] == live.get("default_preset_id"), f"endpoint/default mismatch: {provider_id}")
        expect(row["offlineProtocol"] in protocols and row["webProtocol"] in protocols, f"unimplemented protocol: {provider_id}")
        expect(row["credentialGroup"] == provider_id, f"credential namespace collapse: {provider_id}")
        expect(row["defaultPresetId"] in models and models.get(row["defaultPresetId"], {}).get("providerId") == provider_id, f"invalid provider default: {provider_id}")
        expect(re.search(r"ProviderId\." + provider_id + r"\s*->\s*ModelPresetId\." + row["defaultPresetId"] + r"\b", default_body), f"Android default differs: {provider_id}")
        expect(row["endpoint"] in android_catalog, f"Android endpoint missing: {provider_id}")
        expect(row["webRoute"] in android_routes and row["webRoute"] in native_routes, f"web route absent in a platform: {provider_id}")
        expect(native_route_map.get(provider_id) == row["webRoute"] and row["webRoute"] in android_route_map.get(provider_id, []), f"provider search binding differs: {provider_id}")
        expect(row["nativeMediaWithWeb"] == (row["webRoute"] == "OPENROUTER_SERVER_TOOL"), f"search wire native media capability differs: {provider_id}")
        web_protocols = {"OPENROUTER_SERVER_TOOL": "CHAT_COMPLETIONS", "ZHIPU_CHAT_COMPLETIONS": "CHAT_COMPLETIONS", "QWEN_RESPONSES": "RESPONSES", "RELAYCORE_GPT_RESPONSES": "RESPONSES", "DEEPSEEK_MESSAGES": "MESSAGES", "RELAYCORE_COMPANION": "COMPANION"}
        expect(web_protocols.get(row["webRoute"]) == row["webProtocol"], f"search protocol/route mismatch: {provider_id}")
        suffix = "/chat/completions" if row["webProtocol"] == "CHAT_COMPLETIONS" else "/responses"
        expected_web = row["endpoint"] + suffix
        if row["webRoute"] == "DEEPSEEK_MESSAGES":
            expected_web = row["endpoint"].removesuffix("/v1") + "/anthropic/v1/messages"
        elif row["webProtocol"] == "COMPANION":
            expected_web = providers.get("RELAYCORE_GPT", {}).get("endpoint", "") + "/responses"
        expect(row["webEndpoint"] == expected_web, f"search endpoint mismatch: {provider_id}")
    for preset, row in models.items():
        expect(set(row) == {"presetId", "providerId", "modelId", "displayName", "selection", "exceptionIds"}, f"model fields invalid: {preset}")
        live = sources["presets"].get(preset, {})
        expect(row["providerId"] in providers and row["providerId"] == live.get("provider_id"), f"provider mismatch: {preset}")
        expect(row["modelId"] == live.get("model_id"), f"native model ID mismatch: {preset}")
        expect(row["displayName"] == live.get("display_name"), f"model configuration display snapshot differs: {preset}")
        expect(row["selection"] == sources["selections"].get(preset, "Auto候选专用"), f"model selection slot differs: {preset}")
        expect(set(row["exceptionIds"]) == {e["id"] for e in manifest["exceptions"] if preset in e["appliesTo"]}, f"exception binding incomplete: {preset}")
        profile = sources["profiles"].get(preset)
        if profile:
            expect((row["providerId"], row["modelId"]) == (profile["providerId"], profile["modelId"]), f"Android profile mismatch: {preset}")
        else:
            expect(row["providerId"] == "OPENROUTER" and sources["fallback_ids"].get(preset) == row["modelId"], f"Android exact catalog identity missing: {preset}")
        if row["providerId"] in {"QWEN", "DEEPSEEK", "ZHIPU"}:
            expect(re.search(r"ModelPresetId\." + preset + r"\b[^\n]*-> ProviderId\." + row["providerId"], provider_body), f"Android providerFor mismatch: {preset}")
        for exception in row["exceptionIds"]:
            expect(exception in {e["id"] for e in manifest["exceptions"]}, f"undefined exception: {preset}/{exception}")
    for preset, row in special.items():
        expect(sources["android_catalog"].get(preset) == row["usage"], f"special model Android role differs: {preset}")
        expect(sources["presets"].get(preset, {}).get("model_id") == row["modelId"] and not sources["presets"].get(preset, {}).get("chat_selectable", True), f"special model native role differs: {preset}")
    for preset, row in sources["relay"].items():
        actual_esm = sources["relay_esm"].get(preset, {})
        expect(all(actual_esm.get(key) == value for key, value in row.items()), f"Relay UI/configuration parity differs: {preset}")
        expect(models.get(preset, {}).get("modelId") == row["modelId"], f"Relay Android exact identity differs: {preset}")
    expect(set(sources["relay"]) == set(sources["relay_esm"]), "Relay ESM model set differs")
    expect(manifest["autoPolicy"].startswith("protocol/fixtures/") and manifest["autoPolicy"].endswith(".json"), "Auto authority must be a versioned protocol fixture")
    expect(manifest["autoPolicy"] in (root / (RUST + "auto_chat_routing_v1.rs")).read_text(), "native Auto owner consumes another policy version")
    expect(sources["auto"]["plans"] == sources["auto_android"] == sources["auto_esm"], "Auto ordering differs between shared policy, Android and ESM")
    for task, plan in sources["auto"]["plans"].items():
        expect(len(set(plan)) == len(plan) and set(plan) <= set(models), f"Auto has duplicates, special or retired models: {task}")
    # Critical request-shape checks supplement, never replace behavioral Adapter tests.
    expect('\\\"tool_choice\\\":\\\"required\\\"'  in adapter and '"tools":[{"type":"web_search","external_web_access":true}],"tool_choice":"required"' in native, "Relay Responses required tool selection differs")
    expect('external_web_access\\\":true' in adapter, "Relay Responses explicit live web access differs")
    for authority in manifest["authorities"]:
        expect(set(authority) == {"id", "contract", "androidOwners", "desktopOwners", "verification"}, f"authority schema invalid: {authority.get('id')}")
        for path in [authority["contract"], *authority["androidOwners"], *authority["desktopOwners"], *authority["verification"]]:
            expect(not Path(path).is_absolute() and ".." not in Path(path).parts and (root / path).is_file(), f"authority path missing or unsafe: {path}")
    for provider in providers.values():
        expect((root / provider["contract"]).is_file(), f"provider contract missing: {provider['providerId']}")
    for exception in manifest["exceptions"]:
        expect(set(exception) == {"id", "appliesTo", "contract", "rule"}, f"exception schema invalid: {exception.get('id')}")
        expect(set(exception["appliesTo"]) <= set(models), f"exception references absent model: {exception['id']}")
        expect((root / exception["contract"]).is_file(), f"exception contract missing: {exception['id']}")
    if check_matrix:
        expect((root / MATRIX).is_file() and (root / MATRIX).read_text() == render_matrix(manifest), "generated model matrix stale; run --write-matrix")
    return errors


def render_matrix(manifest: dict) -> str:
    lines = ["# 模型与接口维护矩阵", "", "> 自动生成自 `protocol/model-service/model-service-v1.json`；不要手改。", "> 这是仓库当前配置与维护规范，不证明外部服务、价格或真实能力始终可用。", "", "返回[总架构](MODEL_SERVICE_ARCHITECTURE.md)或[新增与升级流程](MODEL_SERVICE_MAINTENANCE.md)。", "", "## 平台与凭据分组", "", "| 配置身份 | 平台 | 默认预设 | 离线协议 | 联网协议／路线 |", "|---|---|---|---|---|"]
    for p in manifest["providers"]:
        lines.append(f"| `{p['providerId']}` | {p['platform']} | `{p['defaultPresetId']}` | {p['offlineProtocol']} | {p['webProtocol']}／`{p['webRoute']}` |")
    lines += ["", "## 当前普通聊天模型", "", "| 预设身份 | 模型 | API ID | 凭据身份 | 选择位置 | 专属规则 |", "|---|---|---|---|---|---|"]
    for m in manifest["models"]:
        lines.append(f"| `{m['presetId']}` | {m['displayName']} | `{m['modelId']}` | `{m['providerId']}` | {m['selection']} | {', '.join(m['exceptionIds']) or '共用规则'} |")
    lines += ["", "## 独立任务模型", "", "| 预设 | API ID | 用途 |", "|---|---|---|"]
    for m in manifest["specialModels"]:
        lines.append(f"| `{m['presetId']}` | `{m['modelId']}` | {m['usage']}；不进入普通聊天／Auto |")
    lines += ["", "## 专属差异", "", "| 规则 ID | 适用预设 | 规则摘要／权威合同 |", "|---|---|---|"]
    for e in manifest["exceptions"]:
        link = "../../" + e["contract"].removeprefix("docs/") if not e["contract"].startswith("docs/") else "../" + e["contract"][5:]
        lines.append(f"| {e['id']} | {', '.join('`'+p+'`' for p in e['appliesTo'])} | {e['rule']}（[合同]({link})） |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--write-matrix", action="store_true")
    args = parser.parse_args()
    try:
        manifest = parse_json((args.repo / MANIFEST).read_text())
        errors = check(args.repo, manifest, read_sources(args.repo), check_matrix=not args.write_matrix)
        if errors:
            print(json.dumps({"state": "FAILED", "errors": errors}, ensure_ascii=False))
            return 1
        if args.write_matrix:
            target = args.repo / MATRIX
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(render_matrix(manifest), encoding="utf-8")
        print(json.dumps({"state": "PASS", "chatModels": len(manifest["models"]), "specialModels": len(manifest["specialModels"]), "credentialGroups": len(manifest["providers"]), "scope": "offline source and contract consistency; not live acceptance"}, ensure_ascii=False))
        return 0
    except (KeyError, ValueError, OSError) as error:
        print(json.dumps({"state": "FAILED", "errors": [str(error)]}, ensure_ascii=False))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
