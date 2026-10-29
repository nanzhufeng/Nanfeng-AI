# Wry 0.55.1 Windows teardown backport

Source: crates.io `wry 0.55.1`, checksum from the original Cargo.lock:
`186f9871daa55fd9c016578b810d149de58367113db7fb72b462d2323ce19514`.
Original MIT and Apache-2.0 license files are retained.

Only `src/webview2/mod.rs` differs from the published source: the parent subclass
is removed before releasing the WebView2 controller, following the final fix in
https://github.com/tauri-apps/wry/pull/1795 (released in Wry 0.56.1).
Unrelated match-arm/style edits from that pull request are omitted.
All non-Windows platform sources and the package/dependency versions are unchanged.

Tauri runtime 2.11.4 requires Wry 0.55.x. This local Cargo patch avoids a broad
runtime/Tao/Windows SDK upgrade while fixing the proven teardown boundary.
Do not edit the global Cargo registry. Remove this patch and source copy only when
the selected released Tauri runtime includes the upstream fix and Windows plus
same-source Mac regressions pass. Current Windows evidence is recorded separately
in the project checkpoint; this document does not claim Mac execution.
