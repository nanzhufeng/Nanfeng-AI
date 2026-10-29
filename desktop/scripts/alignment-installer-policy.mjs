// Patch only the freshly generated Tauri process macro. The installer never
// closes or terminates a process; an occupied target path causes a safe stop.
export function replaceInstallerProcessMacro(utils, checkerPath) {
  const start = utils.indexOf('!macro CheckIfAppIsRunning executableName productName');
  const end = start < 0 ? -1 : utils.indexOf('!macroend', start);
  if (start < 0 || end < 0) throw new Error('新NSIS进程宏未找到，停止打包以保留旧产物。');
  const old = utils.slice(start, end);
  if (!old.includes('FindProcessCurrentUser') || !old.includes('KillProcessCurrentUser')) {
    throw new Error('新NSIS进程宏已变化，需先核对；不会套用旧模板。');
  }
  if (/[\r\n\0]/.test(checkerPath)) throw new Error('进程检查脚本路径无效。');
  const literal = checkerPath.replaceAll('$', '$$').replaceAll('"', '$\\"');
  const macro = [
    '!macro CheckIfAppIsRunning executableName productName',
    '  !define NanfengGuardID ${__LINE__}',
    '  InitPluginsDir',
    '  Push $R8',
    '  StrCpy $R8 $OUTDIR',
    '  SetOutPath "$PLUGINSDIR"',
    '  File /oname=NanfengAI-Check-InstalledProcess.ps1 "' + literal + '"',
    '  SetOutPath "$R8"',
    '  Pop $R8',
    "  nsExec::ExecToStack '\"$SYSDIR\\WindowsPowerShell\\v1.0\\powershell.exe\" -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File \"$PLUGINSDIR\\NanfengAI-Check-InstalledProcess.ps1\" -ExecutablePath \"$INSTDIR\\${executableName}\"'",
    '  Pop $R0',
    '  Pop $R1',
    '  StrCmp $R0 "0" nanfeng_guard_ok_${NanfengGuardID}',
    '  IfSilent nanfeng_guard_abort_${NanfengGuardID} 0',
    '  MessageBox MB_ICONEXCLAMATION|MB_OK "安装目录程序仍在运行，或暂时无法确认进程身份。请保存内容并正常关闭该目录中的南枫 AI 后重试；其他程序和数据均已保留。" /SD IDOK',
    '  nanfeng_guard_abort_${NanfengGuardID}:',
    '    SetErrorLevel 2',
    '    Abort',
    '  nanfeng_guard_ok_${NanfengGuardID}:',
    '  !undef NanfengGuardID',
    '!macroend',
  ].join('\r\n');
  const patched = utils.slice(0, start) + macro + utils.slice(end + '!macroend'.length);
  if (/nsis_tauri_utils::KillProcess/.test(patched)) {
    throw new Error('NSIS其余区域仍有全局终止进程路径，停止打包。');
  }
  return patched;
}

export function makeSilentFailuresNoninteractive(source) {
  let changed = 0;
  const patched = source.split(/\r?\n/).map(line => {
    if (!line.includes('MessageBox ') || line.includes('/SD')) return line;
    if (line.includes('"$(unableToUninstall)"')) {
      changed++;
      return line + ' /SD IDOK';
    }
    if (line.includes('"$(webview2InstallError)"') && line.includes('IDIGNORE ignore IDRETRY update_webview')) {
      changed++;
      return line.replace(' IDIGNORE ignore', ' /SD IDABORT IDIGNORE ignore');
    }
    throw new Error('新NSIS存在尚未核对的静默对话框，停止打包。');
  }).join('\r\n');
  if (changed !== 2) throw new Error('新NSIS错误对话框结构已变化，需先核对。');
  return patched;
}
