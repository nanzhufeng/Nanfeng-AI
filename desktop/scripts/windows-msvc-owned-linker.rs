//! Windows-only argument bridge. No command shell and no lossy quoting.
use std::{env, io::{BufWriter, Write}, os::windows::{ffi::OsStrExt, process::CommandExt}, process::{Command, Stdio}};
fn main() {
    if let Err(error) = run() { eprintln!("MSVC 链接 owner 失败：{error}"); std::process::exit(90); }
}
fn run() -> Result<(), Box<dyn std::error::Error>> {
    let python = env::var_os("NFAI_MSVC_LINK_PYTHON").ok_or("未配置 Python owner")?;
    let script = env::var_os("NFAI_MSVC_LINK_OWNER_SCRIPT").ok_or("未配置链接 owner")?;
    let args: Vec<Vec<u16>> = env::args_os().skip(1).map(|v| v.encode_wide().collect()).collect();
    if args.len() > 10000 || args.iter().map(|v| v.len()*2+4).sum::<usize>() > 2*1024*1024 { return Err("链接参数超过固定上限".into()); }
    let compiler = env::current_exe()?.file_name().and_then(|v| v.to_str()).is_some_and(|v| v.eq_ignore_ascii_case("cl.exe"));
    let mut child = Command::new(python).args(["-X", "utf8"]).arg(script).arg(if compiler { "--compiler-bridge" } else { "--bridge" })
        .stdin(Stdio::piped()).stdout(Stdio::inherit()).stderr(Stdio::inherit()).creation_flags(0x08000000).spawn()?;
    {
        let mut input = BufWriter::new(child.stdin.take().ok_or("链接参数管道不可用")?);
        input.write_all(&(args.len() as u32).to_le_bytes())?;
        for arg in args { input.write_all(&(arg.len() as u32).to_le_bytes())?; for unit in arg { input.write_all(&unit.to_le_bytes())?; } }
        input.flush()?;
    }
    std::process::exit(child.wait()?.code().unwrap_or(91));
}
