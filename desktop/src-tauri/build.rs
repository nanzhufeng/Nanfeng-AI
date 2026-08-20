use std::{fs, path::Path};

/// Tauri embeds the static frontend at Rust build time.  Keep every current
/// asset (and the directory for additions/removals) in Cargo's dependency
/// graph so a rebuilt `.app` can never keep an older chat shell.
fn watch_frontend_assets(path: &Path) {
    println!("cargo:rerun-if-changed={}", path.display());
    let Ok(entries) = fs::read_dir(path) else {
        return;
    };
    for entry in entries.flatten() {
        watch_frontend_assets(&entry.path());
    }
}

fn main() {
    watch_frontend_assets(Path::new("../dist"));
    tauri_build::build()
}
