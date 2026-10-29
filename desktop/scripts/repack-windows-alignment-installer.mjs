// Adapt only the freshly generated acceptance installer entry points. Never reuse a historical template.
import { spawnSync } from 'node:child_process';
import { readFile, writeFile, mkdir, copyFile, cp, readdir, rename } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { replaceInstallerProcessMacro, makeSilentFailuresNoninteractive } from './alignment-installer-policy.mjs';
import { collectAlignmentPackageInputs } from './alignment-package-inputs.mjs';
import { alignmentProfile } from './windows-alignment-profile.mjs';
const desktop=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const profile=alignmentProfile(process.argv.slice(2));
const config=JSON.parse(await readFile(resolve(desktop,'src-tauri/resources/alignment-launcher/executable.json'),'utf8'));
if(config.buildProfile!==profile)throw Error('Launcher build profile differs from requested NSIS profile');
const target=resolve(desktop,process.env.CARGO_TARGET_DIR||'src-tauri/target',`x86_64-pc-windows-msvc/${profile}`);
const hash=async path=>createHash('sha256').update(await readFile(path)).digest('hex').toUpperCase();
const exe=resolve(target,'nanfeng-ai-desktop-spike.exe');const sha256=await hash(exe);
if(config.sha256!==sha256)throw Error('Launcher executable hash differs from fresh NSIS binary');
const launcherRoot=resolve(desktop,'src-tauri/resources/alignment-launcher');
const generated=await readFile(resolve(target,'nsis/x64/installer.nsi'),'utf8');
const rawUtils=await readFile(resolve(target,'nsis/x64/utils.nsh'),'utf8');
const patchedUtils='\uFEFF'+replaceInstallerProcessMacro(rawUtils,resolve(launcherRoot,'Check-InstalledProcess.ps1')).replace(/^\uFEFF/,'');
const inputs=await collectAlignmentPackageInputs(desktop,target,generated,patchedUtils);
const outputRoot=resolve(desktop,'output/windows-alignment-acceptance');await mkdir(outputRoot,{recursive:true});
const base=resolve(outputRoot,`${sha256}-${inputs.sha256.slice(0,16)}`);
let directory;
for(let attempt=0;attempt<100;attempt++){
 const candidate=base+(attempt?`-attempt${attempt}`:'');
 try{await mkdir(candidate);directory=candidate;break;}catch(error){if(error.code!=='EEXIST')throw error;}
}
if(!directory)throw Error('相同输入已有过多历史尝试，全部产物保留，未覆盖。');
const sourceDir=resolve(directory,'nsis-source');await mkdir(sourceDir,{recursive:true});
let source=makeSilentFailuresNoninteractive(generated);
if(!source.includes('!define BUNDLEID "com.nanzhufeng.ai.desktop.windowsalignmentinstaller"')||source.includes('Software\\Classes\\nanfeng-ai-'))throw Error('Fresh installer identity/protocol isolation failed');
const old='nsis_tauri_utils::RunAsUser "$INSTDIR\\${MAINBINARYNAME}.exe" ""';
if(source.split(old).length!==2)throw Error('Fresh RunMainBinary anchor changed');
const shell='$WINDIR\\System32\\WindowsPowerShell\\v1.0\\powershell.exe';
const args='-WindowStyle Hidden -NoProfile -ExecutionPolicy Bypass -File "$INSTDIR\\alignment-launcher\\Start.ps1"';
source=source.replace(old,`nsis_tauri_utils::RunAsUser "${shell}" '${args}'`);
let shortcuts=0,guards=0;
source=source.split(/\r?\n/).map(line=>{
 if(line.trimStart().startsWith('CreateShortcut ')&&line.endsWith('"$INSTDIR\\${MAINBINARYNAME}.exe"')){shortcuts++;return line.replace('"$INSTDIR\\${MAINBINARYNAME}.exe"',`"${shell}" '${args}' "$INSTDIR\\\${MAINBINARYNAME}.exe" 0`);}
 if(line.includes('IsShortcutTarget ')&&line.endsWith('"$INSTDIR\\${MAINBINARYNAME}.exe"')){guards++;return line.replace('"$INSTDIR\\${MAINBINARYNAME}.exe"',`"${shell}"`);}
 return line;
}).join('\r\n');
if(shortcuts!==3||guards<3)throw Error('Fresh shortcut anchors changed');
const installer=resolve(directory,'NanfengAI-Windows-Alignment-Acceptance-Setup.exe');
source=source.replace('!define OUTFILE "nsis-output.exe"',`!define OUTFILE "${installer}"`);
const nsi=resolve(sourceDir,'installer.nsi');await writeFile(nsi,'\uFEFF'+source.replace(/^\uFEFF/,''));
await writeFile(resolve(sourceDir,'utils.nsh'),patchedUtils);
for(const name of ['FileAssociation.nsh','SimpChinese.nsh'])await copyFile(resolve(target,'nsis/x64',name),resolve(sourceDir,name));
await writeFile(resolve(directory,'input-manifest.json'),JSON.stringify({...inputs.manifest,sha256:inputs.sha256},null,2)+'\n');
await inputs.verify();
const tool=resolve(process.env.LOCALAPPDATA,'tauri/NSIS/makensis.exe');
const result=spawnSync(tool,['/NOCONFIG','/V2',nsi],{cwd:sourceDir,stdio:'inherit',windowsHide:true});if(result.error)throw result.error;if(result.status!==0)process.exit(result.status??1);
await inputs.verify();if(await hash(exe)!==sha256)throw Error('打包期间程序输入变化，已有产物保留。');
const executable=resolve(directory,'nanfeng-ai-desktop-spike.exe');await copyFile(exe,executable);
for(const name of ['media-tools/windows-x86_64','alignment-launcher'])await cp(resolve(desktop,'src-tauri/resources',name),resolve(directory,name),{recursive:true});
const signature=path=>{const child=spawnSync('pwsh',['-NoProfile','-Command',`$ErrorActionPreference='Stop';(Get-AuthenticodeSignature -LiteralPath '${path.replaceAll("'","''")}').Status.ToString()`],{encoding:'utf8',windowsHide:true});if(child.status!==0)throw Error('Signature inspection failed');return child.stdout.trim();};
const receipt={productName:'南枫 AI Windows 对齐验收',installerIdentifier:'com.nanzhufeng.ai.desktop.windowsalignmentinstaller',applicationIdentifier:config.identifier,version:config.version,buildProfile:config.buildProfile,applicationSourceInputSha256:config.sourceInputSha256,packageInputSha256:inputs.sha256,processPolicy:'exact installed path read-only check; no process termination; silent failure aborts',executable,sha256,installer,installerSha256:await hash(installer),executableSignature:signature(executable),installerSignature:signature(installer),createdAt:new Date().toISOString(),source:'fresh Tauri NSIS with isolated acceptance launch entry points'};
await writeFile(resolve(directory,'package.json'),JSON.stringify(receipt,null,2));
const pointer=profile==='release'?'current-release.json':'current.json';
const pending=resolve(desktop,`output/windows-alignment-acceptance/${pointer}.${process.pid}.pending.json`);await writeFile(pending,JSON.stringify(receipt,null,2));await rename(pending,resolve(outputRoot,pointer));
console.log(JSON.stringify(receipt));
