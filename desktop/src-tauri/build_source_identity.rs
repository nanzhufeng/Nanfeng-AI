//! Source/resource identity only: never credentials, user stores, QA roots or build outputs.
use sha2::{Digest,Sha256};
use serde_json::{json,Value};
use std::{fs,io::Read,path::{Path,PathBuf}};
pub const RECIPE:&str="nfai.desktop.source-input.v1";
const ROOTS:&[&str]=&["desktop/src","desktop/dist","desktop/src-tauri/src","desktop/src-tauri/permissions","desktop/src-tauri/capabilities","desktop/src-tauri/icons","desktop/src-tauri/vendor/wry-0.55.1","protocol/fixtures","protocol/model-service"];
const FILES:&[&str]=&["desktop/package.json","desktop/package-lock.json","desktop/scripts/build.mjs","desktop/src-tauri/Cargo.toml","desktop/src-tauri/Cargo.lock","desktop/src-tauri/build.rs","desktop/src-tauri/build_source_identity.rs"];
// Cargo's explicit app features are public build switches. Do not enumerate
// the process environment, which may also contain unrelated credentials.
pub fn declared_feature_names(manifest:&str)->Result<Vec<String>,String> {
 let mut active=false;let mut names=Vec::new();
 for raw in manifest.lines(){let line=raw.split('#').next().unwrap_or("").trim();if line.starts_with('['){active=line=="[features]";continue;}
  if !active {continue;}let Some((name,_))=line.split_once('=') else {continue;};let name=name.trim().trim_matches(['\'', '"']);
  if name.is_empty()||!name.chars().all(|c|c.is_ascii_alphanumeric()||matches!(c,'-'|'_')){return Err("Cargo feature名称不在当前构建身份范围".into());}
  names.push(name.replace('-',"_").to_ascii_uppercase());
 }
 names.sort();names.dedup();Ok(names)
}
pub struct Snapshot {pub sha256:String,pub manifest:Vec<u8>,pub file_count:usize,pub byte_count:u64,pub watch_paths:Vec<PathBuf>}
pub fn is_redirect(metadata:&fs::Metadata)->bool {
 if metadata.file_type().is_symlink(){return true;}
 #[cfg(windows)] {use std::os::windows::fs::MetadataExt;return metadata.file_attributes() & 0x400 != 0;}
 #[cfg(not(windows))] {false}
}
fn private_name(path:&Path)->bool {
 let name=path.file_name().and_then(|s|s.to_str()).unwrap_or("").to_ascii_lowercase();
 let extension=path.extension().and_then(|s|s.to_str()).unwrap_or("").to_ascii_lowercase();
 name==".env"||name.starts_with(".env.")||["pem","key","pfx","p12","keystore","jks","p8"].contains(&extension.as_str())||["id_rsa","id_dsa","id_ecdsa","id_ed25519"].contains(&name.as_str())||name.ends_with("credentials.json")||name.ends_with("secrets.json")
}
fn collect(path:&Path,files:&mut Vec<PathBuf>,watch:&mut Vec<PathBuf>)->Result<(),String> {
 let metadata=fs::symlink_metadata(path).map_err(|_|"源码输入路径无法读取".to_owned())?;
 if is_redirect(&metadata){return Err("源码输入范围含符号链接，未读取目标".into());}
 if private_name(path){return Err("源码资源范围出现凭据文件名，停止构建且未读取内容".into());}
 watch.push(path.into());
 if metadata.is_dir(){
  for entry in fs::read_dir(path).map_err(|_|"源码输入目录无法读取")? {
   let entry=entry.map_err(|_|"源码输入目录项无法读取")?;
   if [".git","node_modules","target","output",".runtime-qa"].contains(&entry.file_name().to_string_lossy().as_ref()){continue;}
   collect(&entry.path(),files,watch)?;
  }
 }else if metadata.is_file(){files.push(path.into());}
 Ok(())
}
pub fn snapshot(repository:&Path,context:Value)->Result<Snapshot,String> {
 for required in ["desktop/src","desktop/src-tauri/src"] {if !repository.join(required).is_dir(){return Err("必需源码目录缺失，不能生成输入身份".into());}}
 let mut paths=Vec::new();let mut watch=Vec::new();let mut missing=Vec::new();
 for relative in ROOTS.iter().chain(FILES){let path=repository.join(relative);if path.exists(){collect(&path,&mut paths,&mut watch)?;}else{missing.push(*relative);}}
 let config_root=repository.join("desktop/src-tauri");
 if config_root.is_dir(){for entry in fs::read_dir(&config_root).map_err(|_|"配置目录无法读取")? {
  let entry=entry.map_err(|_|"配置目录项无法读取")?;let name=entry.file_name().to_string_lossy().into_owned();
  if name.starts_with("tauri")&&name.ends_with(".conf.json"){collect(&entry.path(),&mut paths,&mut watch)?;}
 }}
 paths.sort();paths.dedup();watch.sort();watch.dedup();
 let mut entries=Vec::new();let mut total=0u64;
 for path in &paths {
  let relative=path.strip_prefix(repository).map_err(|_|"源码输入越界")?.to_str().ok_or("源码路径编码不可用")?.replace('\\',"/");
  if relative.starts_with('/')||relative.split('/').any(|s|s==".."){return Err("源码输入路径越界".into());}
  let mut file=fs::File::open(path).map_err(|_|"源码输入文件无法读取")?;let mut hash=Sha256::new();let mut bytes=0u64;let mut buffer=[0u8;16384];
  loop {let n=file.read(&mut buffer).map_err(|_|"源码输入文件读取失败")?;if n==0 {break;}hash.update(&buffer[..n]);bytes=bytes.checked_add(n as u64).ok_or("输入大小溢出")?;}
  total=total.checked_add(bytes).ok_or("源码输入总大小溢出")?;
  entries.push(json!({"path":relative,"bytes":bytes,"sha256":format!("{:x}",hash.finalize())}));
 }
 entries.sort_by(|a,b|a["path"].as_str().cmp(&b["path"].as_str()));missing.sort();
 let count=entries.len();let manifest=serde_json::to_vec(&json!({"recipe":RECIPE,"context":context,"files":entries,"missingScopes":missing})).map_err(|_|"源码输入清单编码失败")?;
 let digest=format!("{:x}",Sha256::digest(&manifest));
 Ok(Snapshot{sha256:digest,manifest,file_count:count,byte_count:total,watch_paths:watch})
}
#[cfg(test)]
mod tests {
 use super::*;
 fn fixture()->tempfile::TempDir {let d=tempfile::tempdir().unwrap();for path in ["desktop/src","desktop/src-tauri/src"]{fs::create_dir_all(d.path().join(path)).unwrap();}fs::write(d.path().join("desktop/src/app.mjs"),"source-A").unwrap();fs::write(d.path().join("desktop/src-tauri/src/lib.rs"),"fn main() {}").unwrap();d}
 #[test] fn source_identity_is_stable_and_matches_exact_manifest_bytes(){let d=fixture();let a=snapshot(d.path(),json!({"target":"windows","profile":"debug"})).unwrap();let b=snapshot(d.path(),json!({"target":"windows","profile":"debug"})).unwrap();assert_eq!(a.sha256,b.sha256);assert_eq!(a.sha256,format!("{:x}",Sha256::digest(&a.manifest)));assert_eq!(a.file_count,2);assert!(a.byte_count>0);assert!(a.watch_paths.iter().any(|p|p.ends_with("app.mjs")));}
 #[test] fn source_identity_captures_uncommitted_edits_additions_and_deletions(){let d=fixture();let a=snapshot(d.path(),json!({})).unwrap().sha256;fs::write(d.path().join("desktop/src/app.mjs"),"source-B").unwrap();let b=snapshot(d.path(),json!({})).unwrap().sha256;assert_ne!(a,b);let extra=d.path().join("desktop/src/new-wip.mjs");fs::write(&extra,"untracked source").unwrap();let c=snapshot(d.path(),json!({})).unwrap().sha256;assert_ne!(b,c);fs::remove_file(extra).unwrap();assert_eq!(b,snapshot(d.path(),json!({})).unwrap().sha256);}
 #[test] fn source_identity_ignores_user_qa_and_cache_roots_without_reading_them(){let d=fixture();let a=snapshot(d.path(),json!({})).unwrap().sha256;for name in [".runtime-qa/private.key","desktop/output/private.key","desktop/src-tauri/target/private.key"] {let p=d.path().join(name);fs::create_dir_all(p.parent().unwrap()).unwrap();fs::write(p,"synthetic-only").unwrap();}assert_eq!(a,snapshot(d.path(),json!({})).unwrap().sha256);}
 #[test] fn source_identity_refuses_credentials_in_bundled_inputs(){let d=fixture();fs::write(d.path().join("desktop/src/.env"),"synthetic-only").unwrap();assert!(snapshot(d.path(),json!({})).err().unwrap().contains("未读取内容"));}
 #[test] fn source_identity_refuses_uppercase_private_key_names(){let d=fixture();fs::write(d.path().join("desktop/src/fixture.KEY"),"synthetic-only").unwrap();assert!(snapshot(d.path(),json!({})).is_err());}
 #[test] fn source_identity_reads_only_declared_public_feature_names(){let text="[package]\nname = \"sample\"\n[features]\nlaunchd-acceptance = []\ndefault = [] # public\n[dependencies]\nnot-a-feature = \"1\"\n";assert_eq!(declared_feature_names(text).unwrap(),vec!["DEFAULT","LAUNCHD_ACCEPTANCE"]);}
 #[test] fn source_identity_distinguishes_build_target_and_effective_configuration(){let d=fixture();let a=snapshot(d.path(),json!({"target":"windows","config":{"version":"1"}})).unwrap().sha256;assert_ne!(a,snapshot(d.path(),json!({"target":"macos","config":{"version":"1"}})).unwrap().sha256);assert_ne!(a,snapshot(d.path(),json!({"target":"windows","config":{"version":"2"}})).unwrap().sha256);}
}
