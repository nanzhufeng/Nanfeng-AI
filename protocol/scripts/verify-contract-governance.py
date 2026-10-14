#!/usr/bin/env python3
"""Offline contract coverage, scope and reference gate; never modifies application behavior."""
import argparse
import json
import os
import re
from pathlib import Path
from urllib.parse import unquote

REGISTRY = "protocol/contracts/contract-registry-v1.json"
START = "<!-- contract-governance:start -->"
END = "<!-- contract-governance:end -->"
LABELS = {"CURRENT": "现行领域合同", "SCOPED": "领域／模块限定合同", "HISTORICAL": "历史阶段记录", "EXPERIMENTAL": "本地实验／独立专项合同"}


def load_registry(root):
    def unique(pairs):
        result = {}
        for k, v in pairs:
            if k in result:
                raise ValueError("duplicate JSON field: " + k)
            result[k] = v
        return result
    return json.loads((root / REGISTRY).read_text(), object_pairs_hook=unique)


def safe_file(root, path):
    return isinstance(path, str) and not Path(path).is_absolute() and ".." not in Path(path).parts and not (root / path).is_symlink() and (root / path).is_file() and (root / path).resolve().is_relative_to(root.resolve())


def guard(row, domain, registry):
    parent = Path(row["path"]).parent
    index = Path(os.path.relpath(registry["index"], parent)).as_posix()
    authority = Path(os.path.relpath(domain["authority"], parent)).as_posix()
    return (f"{START}\n"
            f"> **合同口径：{LABELS[row['status']]}。** 领域入口：[ {domain['title']} ]({authority})；[全项目口径与维护索引]({index})。本文的独立owner／格式边界不因归类改变；阶段状态、Schema与验收记录只适用于注明的范围，不能覆盖其他领域或推断整机当前能力。\n"
            f"> **适用范围：** {row['scope']}\n"
            f"{END}")


def without_guard(text):
    if START not in text:
        return text
    if text.count(START) != 1 or text.count(END) != 1:
        raise ValueError("duplicate or malformed contract guard")
    start, end = text.index(START), text.index(END) + len(END)
    if text.index(END) < start:
        raise ValueError("reversed contract guard")
    return text[:start] + text[end:].lstrip("\n")


def with_guard(text, block):
    text = without_guard(text)
    title, rest = text.split("\n", 1)
    return title + "\n\n" + block + "\n\n" + rest.lstrip("\n")


def render_index(registry):
    domains = {d["id"]: d for d in registry["domains"]}
    lines = ["# 全项目合同口径与维护索引", "", "> 自动生成自 `protocol/contracts/contract-registry-v1.json`；修改登记表后运行治理脚本，不手改本表。", "", "总规则见[合同治理](../CONTRACT_GOVERNANCE_CURRENT.md)。按领域读取入口，再按任务读取限定子合同；不每次加载全部历史。", "", "## 领域入口", "", "| 领域 | 当前入口 |", "|---|---|"]
    for d in registry["domains"]:
        relative = Path(os.path.relpath(d["authority"], Path(registry["index"]).parent)).as_posix()
        lines.append(f"| {d['title']} | [{Path(d['authority']).name}]({relative}) |")
    lines += ["", "## 全部合同适用范围", "", "| 合同 | 分类 | 领域入口 | 适用范围 |", "|---|---|---|---|"]
    for row in registry["contracts"]:
        relative = Path(os.path.relpath(row["path"], Path(registry["index"]).parent)).as_posix()
        lines.append(f"| [{Path(row['path']).name}]({relative}) | {LABELS[row['status']]} | {domains[row['domain']]['title']} | {row['scope']} |")
    lines += ["", "现行不是全平台全场景授权；领域限定仍约束自己的owner与格式；历史不重新约束新流程；实验不自动开放外部副作用。真实功能、部署和设备证据另见PROJECT_STATUS与CURRENT_HANDOFF。"]
    return "\n".join(lines) + "\n"


def validate(root, registry, generated=True):
    errors = []
    if not isinstance(registry, dict) or set(registry) != {"schemaVersion", "governance", "index", "domains", "contracts"}:
        return ["registry schema mismatch"]
    if type(registry["schemaVersion"]) is not int or registry["schemaVersion"] != 1:
        return ["unsupported registry version"]
    if not all(isinstance(registry.get(key), list) and registry[key] for key in ["domains", "contracts"]):
        return ["domains/contracts must be nonempty lists"]
    if not isinstance(registry["index"], str) or not registry["index"].startswith("docs/") or not registry["index"].endswith(".md") or Path(registry["index"]).is_absolute() or ".." in Path(registry["index"]).parts or (root / registry["index"]).is_symlink() or not (root / registry["index"]).resolve().is_relative_to(root.resolve()):
        return ["unsafe index path"]
    target = root / registry["index"]
    marker = "> 自动生成自 `protocol/contracts/contract-registry-v1.json`"
    if target.exists() and (not target.is_file() or marker not in target.read_text()):
        return ["refusing to overwrite a non-generated document as index"]
    domains, rows = {}, {}
    for d in registry["domains"]:
        if not isinstance(d, dict) or set(d) != {"id", "title", "authority"} or not all(isinstance(v,str) and v.strip() for v in d.values()) or d["id"] in domains:
            errors.append("duplicate or invalid domain")
            continue
        domains[d["id"]] = d
    for row in registry["contracts"]:
        if not isinstance(row, dict) or set(row) != {"path", "domain", "status", "scope"} or not all(isinstance(v,str) and v.strip() for v in row.values()) or row["path"] in rows:
            errors.append("duplicate or invalid contract")
            continue
        if any(char in row["scope"] for char in "\n\r|"):
            errors.append("scope must be one Markdown-table-safe line: " + row["path"])
        rows[row["path"]] = row
        if row["status"] not in LABELS or row["domain"] not in domains:
            errors.append("unknown classification/domain: " + row["path"])
        if not safe_file(root, row["path"]):
            errors.append("missing or unsafe contract path: " + row["path"])
    discovered = {str(p.relative_to(root)) for p in (root / "docs").rglob("*_CONTRACT.md")}
    if discovered != set(rows):
        errors.append("contract coverage differs: " + json.dumps({"unregistered": sorted(discovered - set(rows)), "missing": sorted(set(rows) - discovered)}))
    if not safe_file(root, registry["governance"]):
        errors.append("governance document missing")
    for domain in domains.values():
        owner = rows.get(domain["authority"])
        if not owner or owner["status"] == "HISTORICAL" or owner["domain"] != domain["id"]:
            errors.append("domain entry is orphaned, historical or assigned elsewhere: " + domain["id"])
    for row in rows.values():
        if row["domain"] in domains and row["status"] == "CURRENT" and domains[row["domain"]]["authority"] != row["path"]:
            errors.append("duplicate CURRENT authority for one domain: " + row["domain"])
    if len({d["authority"] for d in domains.values()}) != len(domains):
        errors.append("multiple domains duplicate one authority; declare distinct scopes explicitly")
    if errors:
        return errors
    for row in rows.values():
        text = (root / row["path"]).read_text()
        try:
            if generated and text != with_guard(text, guard(row, domains[row["domain"]], registry)):
                errors.append("scope guard stale: " + row["path"])
        except ValueError:
            errors.append("scope guard malformed: " + row["path"])
        for target in re.findall(r"(?<!!)\[[^\]]*\]\(([^\s]+?)(?:\s+\"[^\"]*\")?\)", text):
            target = unquote(target.split("#")[0])
            if not target or re.match(r"^[A-Za-z][A-Za-z0-9+.-]*:", target) or target.startswith("/"):
                continue
            if not ((root / row["path"]).parent / target).exists():
                errors.append("broken local reference: " + row["path"] + " -> " + target)
        if row["status"] == "CURRENT":
            forbidden = {
                "ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md": "模型选择根层只保留“自动选择”以及“日常”“深度”两个手动分组",
                "DESKTOP_CHAT_FIRST_UI_CONTRACT.md": "根层只显示“自动／日常／深度”",
                "ANSWER_LANGUAGE_CURRENT_CONTRACT.md": "包括其工作区、临时会话、联网／离线",
            }
            phrase = forbidden.get(Path(row["path"]).name)
            if phrase and phrase in text:
                errors.append("known superseded current rule returned: " + row["path"])
    if generated and (not safe_file(root, registry["index"]) or (root / registry["index"]).read_text() != render_index(registry)):
        errors.append("generated index stale")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--write", action="store_true", help="Only regenerate registered scope headers and index")
    args = parser.parse_args()
    try:
        registry = load_registry(args.repo)
        errors = validate(args.repo, registry, generated=not args.write)
        if errors:
            print(json.dumps({"state": "FAILED", "errors": errors}, ensure_ascii=False))
            return 1
        if args.write:
            domains = {d["id"]: d for d in registry["domains"]}
            for row in registry["contracts"]:
                path = args.repo / row["path"]
                text = with_guard(path.read_text(), guard(row, domains[row["domain"]], registry))
                path.write_text(text, encoding="utf-8")
            target = args.repo / registry["index"]
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(render_index(registry), encoding="utf-8")
        print(json.dumps({"state": "PASS", "contracts": len(registry["contracts"]), "domains": len(registry["domains"]), "scope": "offline contract coverage and precedence; not full semantic or runtime proof"}, ensure_ascii=False))
        return 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(json.dumps({"state": "FAILED", "errors": [str(error)]}, ensure_ascii=False))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
