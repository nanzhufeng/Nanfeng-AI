use std::{env, fs, path::Path};

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
    // These are public Supabase client settings, never client secrets. The macOS bundle
    // receives the same local build injection as Android so Finder-launched Desktop has
    // a real availability state instead of inheriting an empty shell environment.
    for key in [
        "NANFENG_DESKTOP_BUNDLED_SUPABASE_URL",
        "NANFENG_DESKTOP_BUNDLED_SUPABASE_PUBLISHABLE_KEY",
    ] {
        println!("cargo:rerun-if-env-changed={key}");
        if let Ok(value) = env::var(key) {
            if !value.trim().is_empty() && !value.contains(['\n', '\r']) {
                println!("cargo:rustc-env={key}={value}");
            }
        }
    }
    tauri_build::build()
}
