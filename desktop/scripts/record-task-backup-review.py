"""Non-overwriting stage evidence and byte-preserving documentation update."""
import ast,hashlib,json,subprocess
from pathlib import Path
repo=Path(__file__).resolve().parents[2]
review=repo/'docs/review/20261001-w15-task-assets-backup'
root=Path('D:/CodexTemp/nfa-w15-task-assets-fixed-01a0f7ab/nanfeng-ai-windows-acceptance.267f83c7-b307-445f-9573-8dd11c9da2a1')
def digest(b):return hashlib.sha256(b).hexdigest()
def save(name,data):
    with (review/name).open('x',encoding='utf-8') as f:json.dump(data,f,ensure_ascii=False,indent=2)
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
e=read(root/'task-assets-backup-evidence.json');final=read(root/'task-assets-backup-final-cold-evidence.json');independent=read(root/'task-backup-resume-independent.json')
assert final['pass'] and independent['allOk']
save('native-summary.json',{'root':str(root),'candidate':e['candidateSha256'],'backupChecks':len(e['checks']),'backupNormalCloses':len(e['pids']),'backupFinalOriginalErrorRetained':e['error'],'finalColdChecks':len(final['checks']),'finalColdPass':True,'finalColdNormalCloses':len(final['pids']),'fullNormalRecoveryAccepted':False,'finalSqlite':final['finalSqlite'],'areas':{a:{'artifact':v['artifact'],'preflight':v['preflight'],'tasks':len(v['libraries']),'restoredStatus':final['status'][a]} for a,v in e['areas'].items()},'independentResume':independent})
sources=review/'sources';sources.mkdir()
names=['create-task-backup-driver.py','check-task-assets-backup.py','verify-windows-task-assets-backup-native.mjs','inspect-task-backup-sensitive.py','create-task-backup-cold-driver.py','verify-windows-task-backup-resume-cold-native.mjs','create-task-backup-final-driver.py','verify-windows-task-backup-final-cold-native.mjs','check-task-backup-resume-final.py','record-task-backup-review.py']
for name in names:
    data=(repo/'desktop/scripts'/name).read_bytes()
    with (sources/name).open('xb') as f:f.write(data)
    if name.endswith('.py'):ast.parse(data.decode('utf-8'))
    else:subprocess.run(['node','--check',str(repo/'desktop/scripts'/name)],check=True,capture_output=True)
products=['desktop/src/app.mjs','desktop/src/search-result-cache.mjs','desktop/src-tauri/src/lib.rs','desktop/src-tauri/src/desktop_local_backup_v1.rs']
prior=read(repo/'docs/review/20261001-w15-real-switch/source-identities.json')
hashes={p:digest((repo/p).read_bytes()).upper() for p in products}
assert all(hashes[p]==prior['productHashes'][p] for p in products[:-1])
save('source-identities.json',{'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),'fixedMac':'dafc64d9b33661dc4f792fd1434ff1ddca9cdfa3','candidate':read(repo/'desktop/output/windows-acceptance/current.json'),'productHashes':hashes,'priorProductHashes':prior['productHashes'],'changedProductOnly':products[-1],'helperHashes':{n:digest((sources/n).read_bytes()) for n in names},'nodeSyntaxAndPythonAstPassed':True,'macRuntimeVerified':False})
parents=[Path('D:/CodexTemp/nfa-w15-task-assets'+s+'-01a0f7ab') for s in ('','-v2','-v3','-v4','-v5','-v6','-fixed')]
processes=json.loads(subprocess.check_output(['pwsh','-NoProfile','-Command',"@(Get-CimInstance Win32_Process | Where-Object {$_.Name -in @('nanfeng-ai-desktop-spike.exe','msedgewebview2.exe') } | Select-Object ProcessId,ExecutablePath,CommandLine) | ConvertTo-Json -Compress"],encoding='utf-8'))
receipts=[]
for parent in parents:
    for path in parent.glob('nanfeng-ai-launch.*.json'):
        launch=read(path);profile=launch['webviewProfile'];matches=[p['ProcessId'] for p in processes if p.get('CommandLine') and profile.lower() in p['CommandLine'].lower()]
        exists=any(p['ProcessId']==launch['pid'] for p in processes)
        assert not exists and not matches
        receipts.append({'receipt':str(path),'root':launch['root'],'pid':launch['pid'],'pidExists':exists,'profileProcesses':len(matches),'rootPreserved':Path(launch['root']).is_dir()})
save('root-receipts-final.json',receipts)
status=subprocess.check_output(['git','status','--porcelain=v1'],cwd=repo).decode('utf-8')
old=(repo/'docs/review/20261001-w15-real-switch/wip-current.txt').read_text(encoding='utf-8-sig')
missing=sorted(set(old.splitlines())-set(status.splitlines()));assert not missing,missing
with (review/'wip-current.txt').open('x',encoding='utf-8') as f:f.write(status)
save('wip-preservation.json',{'priorStatusLines':len(old.splitlines()),'currentStatusLines':len(status.splitlines()),'missingPriorLines':missing})
monitors={}
for name,path in [('12h',Path('D:/CodexTemp/nfa-real-clock-01a0f5c1-0d3d7a1a-f6ff-4d69-ab4d-ae1f12ee3f68/nanfeng-ai-windows-acceptance.1c87a769-98e9-4c50-a717-4537384d42e5/account-real-clock-status.json')),('24h',Path('D:/CodexTemp/nfa-w20-24h-running-utf8-01a0f700/nanfeng-ai-windows-acceptance.f9f70c67-ee2e-4cbb-83f7-8f3d110b266d/local-stability-status.json'))]:monitors[name]=read(path)
save('monitor-snapshot-final.json',monitors)
report='''# W15 非空导入任务、回执及四受控资产根备份

2026-10-01。固定 Mac `dafc64d9b33661dc4f792fd1434ff1ddca9cdfa3`，HEAD `2873f051cbea91c0122f14a08f807f374b28da75`。完整 W15/BG23/总控1.3未完成，NORMAL收尾、Mac同源码及真实服务未验。无子代理/分支/提交/发布/右侧面板；所有WIP、旧候选、成功和失败根保留。

## 产品实红与修复

旧9BD295E2对真实非空任务备份拒绝：`looks_sensitive` 将 `chatgpt-task-...`/storage_key 中单词task的sk-子串误判密钥。新合成根71132d93实际四类任务已生成，真实export拒绝；只读定位485个原库文本命中，全是词内sk-，无真实凭据。敏感值定位只存列名/长度/hash，不存cell正文。独立快照回归旧代码红1，原始来源任务未修改。现有backup owner仅让sk-从token边界匹配，原长度门及其它凭据门不变；明确sk-、大写、引号、中文邻接、Bearer/API key/private key仍拒绝。

`desktop_local_backup_v1::tests`39/0/0通过。固定D缓存、target Junction、preserve-output构建新4F7769D0CCEA43C50998F9DBAD0696A1B3A406754DEABFCAB4EF18E6C2DC2F0F成功/12既有warnings；三其它产品hash与来源完全相同，无schema/权限/Provider/按钮改动。Mac及产品全套未跑。

## 实际负载与备份链

成功根 `D:/CodexTemp/nfa-w15-task-assets-fixed-01a0f7ab/nanfeng-ai-windows-acceptance.267f83c7-b307-445f-9573-8dd11c9da2a1`。真实历史导入owner在每区创建ChatGPT/Claude/知识库各completed12、partial7/12、awaiting0/12三个任务；另有CHATGPT/CLAUDE ZIP各18完成会话/任务，CHATGPT恢复一个独立非空TXT及36实际消息引用。各区11非空任务、93导入会话、186导入消息，另11协议seed会话/22消息；v2独立私有owner另列。未SQL制造业务/Attempt，不联网真实Provider。

四目录均经owner写入且非空：assets1文件、packages11、p6k-zip-import-assets2、exchange-v2/archives2，共16文件/区；v2包本体与资产完整。真实export/preflight/逐区明确replaceLocal/完整read/正常Close/diagnostic cold通过。两包manifest18/17项，逐项CRC/hash、完整目录与全部90表核验；18个设备表排除/保留按owner合同核，另72业务表含所有非空任务/items/messages/receipt/provenance。每类三个JSON任务的AWAITING任务在备份副本转FAILED/INTERRUPTED，其余COMPLETED/PARTIALLY_COMPLETED不变；两ZIP任务均COMPLETED。另区/共享配置/账本守恒。

原生备份链46断言、4正常Close/本根WebView退出；13/关闭26SQLite完整ok/FK0。原final严格raw表检查实红保留：CHAT附件缓存reference_count0→36，两个时间列更新；独立确认真实36消息引用，sha/字节/created_at及其它全部字段完全相同。补充检查明确记录差异，不称raw表全等，不冒充NORMAL。

## 恢复任务实际可用与终检

同根六个FAILED任务显式retry，原私有package读取/hash身份保持、retry_count各+1，72项实际confirm全部完成；老receipt/provenance整行保留，各格式每区新增12准确会话/receipt/provenance。另有每区3原completed/3partial/2ZIP任务及原ID全文/草稿守恒，无自动确认/自动重发。

全部22工作区真实v1导出逐字段按既有portable合同（内部importedFrom/importedAtEpochMs等非wire字段排除，缺ordinal补序/缺asset classification继承sensitivity），全部正文/ID/消息/附件字节保真；v2实际重导出完整包一致。13补验断言/两正常Close/26SQLite全ok/FK0；独立完整90表比较（CHAT73/WORK74未变，其余明确owner增量）、旧receipt/输入全文/六retry/22导出/所有16私有文件、共享及账本通过。原导出助手错误全等断言及所有失败收据保留。first冷助手错误读取projection不存在failure字段也保留；不扩大CDP30s/Node2048MiB，不放宽格式。

## 新发现与仍开

补验一次启动真实失败：OS新进程147992恰复用CHAT旧恢复processId147992。state仍RESTORED_RESTART_REQUIRED，`requires_restart`只看PID，setup错误判定未重启；stderr panic及launch收据完整保留。下一独立高价值为backup恢复门的进程实例身份修复/兼容/同进程阻写与实际cold，先绑定来源和定向红绿，不改旧成功根/持久状态来掩盖；现阶段未修，不把后续不同PID的成功称该故障通过。

12h153544/145716与24h15856/84408继续保留。最早北京时间10月2日00:45:29.183/截止00:55:29.183、18:39:25.507；双真实及单调钟满时并终断言才通过。禁止probe/改production/钟SQL/并行NORMAL；新旧W15根NORMAL收尾及checkpoint缺失/损坏NORMAL边界须等身份释放。真实资料/真实服务暂跳过；标准用户调度/通知真实冷激活/DPI多屏/签名净机升级/NORMAL生产流式/严格trusted IME/Mac同源码仍开，不宣布完整开发完成。
'''
with (review/'REPORT.md').open('x',encoding='utf-8') as f:f.write(report)
summary='''## 2026-10-01：W15非空任务/四资产根备份实红修复与指定原生链通过，PID复用故障新开

- [报告](review/20261001-w15-task-assets-backup/REPORT.md)：旧9BD295E2敏感扫描误将真实chatgpt-task/storage_key内sk-当密钥，真实export拒绝和Rust红1；backup owner修token边界，39定向绿/固定D缓存preserve-output新4F7769D0构建成功。三个其它产品hash不变，全部WIP/旧候选/成功失败根保留。
- 新marked267f83c7每区11实际非空任务（JSON各completed/partial/awaiting＋两ZIP）、93导入会话/186消息/36真实附件引用，四受控根16私有文件。真实双区export/preflight/replace/4正常Close/cold46断言；72业务表及18设备表合同、每包全hash/CRC核验。AWAITING在备份转FAILED/INTERRUPTED，终态和receipt保持；CHAT缓存0→36及两时间列精确单列，不虚称全raw表不变。
- 六FAILED任务显式retry/72项confirm/22真实v1导出/v2重导出/两正常Close/cold13断言，独立每区90表/旧receipt/来源全文/16文件/共享账本通过，26SQLite ok/FK0。所有助手失败收据保留；无Provider Attempt造数/真实服务/产品全套/Mac，不冒充NORMAL。
- 新实红：OS冷启147992复用CHAT恢复旧PID147992，PID-only requires_restart误拒setup。日志/收据保留，下一独立阶段修复process实例门/兼容/实际cold；当前未修。两monitor保12h153544/145716、24h15856/84408，最早10月2日00:45:29.183/截止00:55:29.183及18:39:25.507；禁probe/改production/钟SQL/并行NORMAL。
- 完整W15/BG23/1.3继续OPEN_NOT_ACCEPTED；NORMAL回滚/收尾/checkpoint损坏缺失、Mac/系统/安装签名/DPI/严格IME/真实（暂跳过）仍开。实际阶段唯一默认模型/推理自动接续已授权，无子代理/分支/提交/发布/右面板。

'''
preservation=[]
for relative in ('docs/CURRENT_HANDOFF.md','docs/next-codex-prompt.md','docs/WINDOWS_MASTER_PLAN.md','docs/review/20260919-upstream-plan-reconciliation/BUG_AND_GAP_REGISTER.md','docs/C14_LOCAL_DATA_PARITY_CONTRACT.md'):
    path=repo/relative;old=path.read_bytes();prepend=relative in ('docs/CURRENT_HANDOFF.md','docs/next-codex-prompt.md','docs/WINDOWS_MASTER_PLAN.md');addition=summary.encode('utf-8')
    if not prepend:addition=summary.replace('(review/','(../../review/' if 'BUG_AND_GAP' in relative else '(review/').encode('utf-8')
    new=addition+old if prepend else old+b'\n'+addition
    path.write_bytes(new);assert new.endswith(old) if prepend else new.startswith(old)
    preservation.append({'path':relative,'oldBytes':len(old),'oldSha256':digest(old),'oldSuffixPreserved':prepend,'oldPrefixPreserved':not prepend})
save('document-preservation.json',preservation)
print(json.dumps({'report':str(review/'REPORT.md'),'receipts':len(receipts),'allRootsPreserved':True,'missingWip':len(missing),'candidate':e['candidateSha256'],'monitors':{'12h':monitors['12h']['phase'],'24h':monitors['24h']['state']}}))
