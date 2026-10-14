use std::{
    env, fs,
    path::Path,
    process::Command,
    time::{SystemTime, UNIX_EPOCH},
};

fn build_git_commit() -> String {
    if let Ok(value) = env::var("NANFENG_AI_GIT_COMMIT") {
        let value = value.trim();
        if (7..=64).contains(&value.len()) && value.bytes().all(|byte| byte.is_ascii_hexdigit()) {
            return value.to_ascii_lowercase();
        }
    }
    Command::new("git")
        .args(["rev-parse", "--short=12", "HEAD"])
        .current_dir("../..")
        .output()
        .ok()
        .filter(|output| output.status.success())
        .and_then(|output| String::from_utf8(output.stdout).ok())
        .map(|value| value.trim().to_ascii_lowercase())
        .filter(|value| (7..=64).contains(&value.len()) && value.bytes().all(|byte| byte.is_ascii_hexdigit()))
        .unwrap_or_else(|| "unknown".into())
}

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
    println!("cargo:rerun-if-env-changed=SOURCE_DATE_EPOCH");
    println!("cargo:rerun-if-env-changed=NANFENG_AI_GIT_COMMIT");
    println!("cargo:rerun-if-changed=../../.git/HEAD");
    let build_epoch_seconds = env::var("SOURCE_DATE_EPOCH")
        .ok()
        .filter(|value| value.parse::<u64>().is_ok())
        .unwrap_or_else(|| {
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .expect("system clock must be after Unix epoch")
                .as_secs()
                .to_string()
        });
    println!("cargo:rustc-env=NANFENG_AI_BUILD_EPOCH_SECONDS={build_epoch_seconds}");
    println!("cargo:rustc-env=NANFENG_AI_GIT_COMMIT={}", build_git_commit());
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
