#!/usr/bin/env python3
"""Capture the production Compose C16 matrix on an isolated Android emulator."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


APPEARANCES = (
    {"id": "system-light", "mode": "system", "host_dark": False, "resolved": "light"},
    {"id": "system-dark", "mode": "system", "host_dark": True, "resolved": "dark"},
    {"id": "light", "mode": "light", "host_dark": True, "resolved": "light"},
    {"id": "dark", "mode": "dark", "host_dark": False, "resolved": "dark"},
)
FONTS = (
    {"id": "small", "label": "小", "scale": 0.80},
    {"id": "standard", "label": "标准", "scale": 1.00},
    {"id": "large", "label": "大", "scale": 1.24},
)
LAYERS = (
    ("model-root", ("选择模型", "自动选择")),
    ("model-daily", ("日常", "DeepSeek V4 Flash")),
    ("model-deep", ("深度", "DeepSeek V4 Pro")),
    ("add-root", ("基础风格和语气", "实时网页搜索")),
    ("style", ("基础风格和语气", "直言不讳")),
    ("search-history", ("搜索历史", "清空")),
    ("settings-theme", ("橙色", "紫色")),
)
MODE_LABELS = {"system": "系统（默认）", "light": "浅色", "dark": "深色"}
BOUNDS_RE = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class Device:
    def __init__(self, serial: str, package: str, activity: str) -> None:
        self.serial = serial
        self.package = package
        self.activity = activity

    def adb(self, *args: str, binary: bool = False, timeout: int = 30):
        result = subprocess.run(
            ["adb", "-s", self.serial, *args],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
        )
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace").strip()

    def shell(self, *args: str) -> str:
        return self.adb("shell", *args)

    def start(self, route: str) -> None:
        self.shell("am", "force-stop", self.package)
        self.shell(
            "am", "start", "-W", "-n", f"{self.package}/{self.activity}",
            "--es", "com.nanzhufeng.ai.extra.P5A_ROUTE", route,
        )
        time.sleep(0.55)

    def xml(self) -> bytes:
        self.shell("uiautomator", "dump", "/sdcard/c16-window.xml")
        return self.adb("exec-out", "cat", "/sdcard/c16-window.xml", binary=True)

    def nodes(self) -> list[dict[str, object]]:
        root = ET.fromstring(self.xml())
        found = []
        for node in root.iter("node"):
            match = BOUNDS_RE.fullmatch(node.attrib.get("bounds", ""))
            if not match:
                continue
            x1, y1, x2, y2 = map(int, match.groups())
            found.append({
                "text": node.attrib.get("text", ""),
                "desc": node.attrib.get("content-desc", ""),
                "bounds": [x1, y1, x2, y2],
                "center": [(x1 + x2) // 2, (y1 + y2) // 2],
            })
        return found

    def tap(self, *, text: str | None = None, desc: str | None = None, prefer_last: bool = False) -> dict[str, object]:
        candidates = []
        for node in self.nodes():
            if text is not None and node["text"] == text:
                candidates.append(node)
            elif desc is not None and str(node["desc"]).startswith(desc):
                candidates.append(node)
        if not candidates:
            raise RuntimeError(f"UI target not found: text={text!r} desc={desc!r}")
        candidates.sort(key=lambda node: (node["center"][1], node["center"][0]))
        target = candidates[-1] if prefer_last else candidates[0]
        x, y = target["center"]
        self.shell("input", "tap", str(x), str(y))
        time.sleep(0.38)
        return target


def select_combo(device: Device, appearance: dict, font: dict) -> None:
    device.shell("cmd", "uimode", "night", "yes" if appearance["host_dark"] else "no")
    device.start("settings")
    device.tap(text="外观", prefer_last=True)
    device.tap(text=MODE_LABELS[appearance["mode"]])
    device.tap(text="字体大小")
    device.tap(text=font["label"])
    time.sleep(0.55)


def open_layer(device: Device, layer: str) -> None:
    if layer == "settings-theme":
        device.start("settings")
        device.tap(text="主题色")
        return
    device.start("conversation")
    if layer.startswith("model-"):
        device.tap(desc="选择模型：")
        if layer == "model-daily":
            device.tap(text="日常")
        elif layer == "model-deep":
            device.tap(text="深度")
    elif layer in {"add-root", "style"}:
        device.tap(desc="添加到草稿")
        if layer == "style":
            device.tap(text="基础风格和语气")
    elif layer == "search-history":
        device.tap(desc="打开对话导航")
        device.tap(text="搜索")
        device.tap(text="历史")
    else:
        raise AssertionError(layer)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output_root", type=Path)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--package", default="com.nanzhufeng.ai.searchattachmentacceptance")
    parser.add_argument("--activity", default="com.nanzhufeng.ai.NanfengAiActivity")
    args = parser.parse_args()
    output = args.output_root.resolve() / "android"
    output.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((args.output_root / "evidence-manifest.json").read_text())
    device = Device(args.serial, args.package, args.activity)
    apk = Path("app/build/outputs/apk/searchAttachmentAcceptance/南枫AI-开发验收.apk").resolve()
    environment = {
        "serial": args.serial,
        "device": device.shell("getprop", "ro.product.model"),
        "sdk": device.shell("getprop", "ro.build.version.sdk"),
        "wmSize": device.shell("wm", "size"),
        "wmDensity": device.shell("wm", "density"),
        "package": args.package,
        "activity": args.activity,
        "apk": str(apk),
        "apkSha256": sha256(apk.read_bytes()),
        "network": "wifi and mobile data disabled before capture",
    }
    results = []
    for appearance in APPEARANCES:
        for font in FONTS:
            select_combo(device, appearance, font)
            for layer, expected_tokens in LAYERS:
                item_id = f"C16-{appearance['id']}-{font['id']}-{layer}"
                open_layer(device, layer)
                xml_bytes = device.xml()
                screenshot = device.adb("exec-out", "screencap", "-p", binary=True)
                semantic_text = " ".join(
                    value for node in ET.fromstring(xml_bytes).iter("node")
                    for value in (node.attrib.get("text", ""), node.attrib.get("content-desc", "")) if value
                )
                tokens_found = {token: token in semantic_text for token in expected_tokens}
                metadata = {
                    "schemaVersion": 1,
                    "platform": "android",
                    "itemId": item_id,
                    "requested": {
                        "appearance": appearance["id"], "mode": appearance["mode"],
                        "hostDark": appearance["host_dark"], "resolvedMode": appearance["resolved"],
                        "font": font["id"], "fontScale": font["scale"], "layer": layer,
                    },
                    "environment": environment,
                    "sourceFingerprint": manifest["sourceFingerprint"],
                    "capturedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                    "hostNightMode": device.shell("cmd", "uimode", "night"),
                    "screenshotSha256": sha256(screenshot),
                    "semanticTreeSha256": sha256(xml_bytes),
                    "expectedTokens": tokens_found,
                    "pass": all(tokens_found.values()),
                }
                (output / f"{item_id}.png").write_bytes(screenshot)
                (output / f"{item_id}.xml").write_bytes(xml_bytes)
                (output / f"{item_id}.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
                results.append({"itemId": item_id, "pass": metadata["pass"], "sha256": metadata["screenshotSha256"]})
                print(json.dumps({"captured": len(results), "itemId": item_id, "pass": metadata["pass"]}, ensure_ascii=False), flush=True)
    print(json.dumps({
        "captured": len(results),
        "passed": sum(result["pass"] for result in results),
        "failed": [result["itemId"] for result in results if not result["pass"]],
        "uniqueScreenshots": len({result["sha256"] for result in results}),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
