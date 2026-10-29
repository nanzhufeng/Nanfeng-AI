mod build_source_identity;

use std::{
    env, fs,
    path::{Path, PathBuf},
    process::Command,
    time::{SystemTime, UNIX_EPOCH},
};

/// Tauri embeds the static frontend at Rust build time.  Keep every current
/// asset (and the directory for additions/removals) in Cargo's dependency
/// graph so a rebuilt `.app` can never keep an older chat shell.
fn watch_frontend_assets(path: &Path) {
    println!("cargo:rerun-if-changed={}", path.display());
    if fs::symlink_metadata(path).ok().is_some_and(|metadata| build_source_identity::is_redirect(&metadata)) { return; }
    let Ok(entries) = fs::read_dir(path) else {
        return;
    };
    for entry in entries.flatten() {
        watch_frontend_assets(&entry.path());
    }
}

fn git_output(repository: &Path, args: &[&str]) -> Option<String> {
    Command::new("git")
        .args(args)
        .current_dir(repository)
        .output()
        .ok()
        .filter(|output| output.status.success())
        .and_then(|output| String::from_utf8(output.stdout).ok())
        .map(|value| value.trim().to_owned())
        .filter(|value| !value.is_empty())
}

fn watch_git_path(repository: &Path, name: &str) {
    let Some(path) = git_output(repository, &["rev-parse", "--git-path", name]) else {
        return;
    };
    let path = PathBuf::from(path);
    let path = if path.is_absolute() {
        path
    } else {
        repository.join(path)
    };
    println!("cargo:rerun-if-changed={}", path.display());
}

fn product_version() -> String {
    let base = Path::new("tauri.conf.json");
    println!("cargo:rerun-if-changed={}", base.display());
    let platform = if env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows") {
        Some(Path::new("tauri.windows.conf.json"))
    } else {
        None
    };
    let mut config: serde_json::Value =
        serde_json::from_slice(&fs::read(base).expect("Tauri base config must be readable"))
            .expect("Tauri base config must be valid JSON");
    if let Some(path) = platform {
        println!("cargo:rerun-if-changed={}", path.display());
        let override_config: serde_json::Value = serde_json::from_slice(
            &fs::read(path).expect("Tauri platform config must be readable"),
        )
        .expect("Tauri platform config must be valid JSON");
        if let Some(version) = override_config.get("version") {
            config["version"] = version.clone();
        }
    }
    // The CLI may supply a selected acceptance config. Its top-level version
    // takes precedence over the ordinary Windows override when present.
    println!("cargo:rerun-if-env-changed=TAURI_CONFIG");
    if let Ok(value) = env::var("TAURI_CONFIG") {
        let selected: serde_json::Value =
            serde_json::from_str(&value).expect("selected Tauri config must be valid JSON");
        if let Some(version) = selected.get("version") {
            config["version"] = version.clone();
        }
    }
    let version = config["version"]
        .as_str()
        .filter(|value| !value.trim().is_empty() && !value.contains(['\r', '\n']))
        .expect("Tauri product version must be a nonempty string");
    version.to_owned()
}

fn main() {
    watch_frontend_assets(Path::new("../dist"));
    println!(
        "cargo:rustc-env=NANFENG_AI_PRODUCT_VERSION={}",
        product_version()
    );
    println!("cargo:rerun-if-env-changed=SOURCE_DATE_EPOCH");
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
    if let Ok(manifest_dir) = env::var("CARGO_MANIFEST_DIR") {
        if let Some(repository) = Path::new(&manifest_dir).parent().and_then(Path::parent) {
            let git_metadata = repository.join(".git");
            if git_metadata.exists() {
                println!("cargo:rerun-if-changed={}", git_metadata.display());
                // A commit updates the branch ref's contents, not the .git directory mtime.
                // Resolve Git's own paths so linked worktrees and packed refs stay accurate.
                watch_git_path(repository, "HEAD");
                if let Some(head_ref) = git_output(repository, &["symbolic-ref", "-q", "HEAD"]) {
                    watch_git_path(repository, &head_ref);
                }
                watch_git_path(repository, "packed-refs");
                watch_git_path(repository, "index");
                let commit =
                    git_output(repository, &["rev-parse", "--verify", "HEAD"]).filter(|value| {
                        (value.len() == 40 || value.len() == 64)
                            && value.bytes().all(|byte| byte.is_ascii_hexdigit())
                    });
                if let Some(commit) = commit {
                    println!("cargo:rustc-env=NANFENG_AI_SOURCE_COMMIT={commit}");
                    if let Ok(output) = Command::new("git")
                        .args(["status", "--porcelain=v1", "--untracked-files=normal"])
                        .current_dir(repository)
                        .output()
                    {
                        if output.status.success() {
                            println!(
                                "cargo:rustc-env=NANFENG_AI_SOURCE_DIRTY={}",
                                !output.stdout.is_empty()
                            );
                        }
                    }
                }
            }
        }
    }
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
    let manifest_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR").expect("manifest directory"));
    let repository = manifest_dir.parent().and_then(Path::parent).expect("repository root");
    let manifest = fs::read_to_string(manifest_dir.join("Cargo.toml")).expect("public Cargo manifest");
    let features = build_source_identity::declared_feature_names(&manifest).expect("declared Cargo feature names")
        .into_iter().filter(|name| env::var_os(format!("CARGO_FEATURE_{name}")).is_some()).collect::<Vec<_>>();
    let selected_config = env::var("TAURI_CONFIG").ok().map(|text| serde_json::from_str::<serde_json::Value>(&text).expect("selected public Tauri config"));
    let context = serde_json::json!({"target":env::var("TARGET").unwrap_or_default(),"profile":env::var("PROFILE").unwrap_or_default(),"features":features,"selectedConfig":selected_config});
    let inputs = build_source_identity::snapshot(repository,context).expect("cannot identify actual source/resource inputs");
    for path in &inputs.watch_paths {println!("cargo:rerun-if-changed={}",path.display());}
    println!("cargo:rerun-if-changed={}", manifest_dir.join("build_source_identity.rs").display());
    println!("cargo:rustc-env=NANFENG_AI_SOURCE_INPUT_SHA256={}",inputs.sha256);
    println!("cargo:rustc-env=NANFENG_AI_SOURCE_INPUT_FILE_COUNT={}",inputs.file_count);
    println!("cargo:rustc-env=NANFENG_AI_SOURCE_INPUT_BYTES={}",inputs.byte_count);
    println!("cargo:rustc-env=NANFENG_AI_SOURCE_INPUT_RECIPE={}",build_source_identity::RECIPE);
    println!("cargo:rustc-env=NANFENG_AI_BUILD_PROFILE={}",env::var("PROFILE").unwrap_or_default());
    fs::write(PathBuf::from(env::var("OUT_DIR").expect("build output directory")).join("source-input-manifest-v1.json"),&inputs.manifest).expect("source input manifest receipt");
    tauri_build::build()
}
