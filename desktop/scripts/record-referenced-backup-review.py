"""Record bounded receipts and prepend current facts without removing old bytes."""
from pathlib import Path
import hashlib,json
repo=Path(__file__).resolve().parents[2]
r=repo/'docs/review/20261001-w15-referenced-backup'
root=Path('D:/CodexTemp/nfa-w15-referenced-backup-verified-01a0f78b/nanfeng-ai-windows-acceptance.e2c65e69-8c70-4d54-af0d-1883a685fb37')
e=json.loads((root/'referenced-backup-evidence.json').read_text(encoding='utf-8'))
assert e['preparationPass'] and not e.get('forcedCleanup')
def save(name,value):
 with (r/name).open('x',encoding='utf-8') as f:json.dump(value,f,ensure_ascii=False,indent=2)
summary={k:e[k] for k in ['root','candidateSha256','startupMode','realService','performanceBudget','checks','launches','measurements','beforeSqlite','afterRestoreClosedSqlite','finalSqlite','pids','restores','coldStatus','normalRecoveryBoundary']}
summary['areas']={a:{k:v for k,v in b.items() if k not in ['libraries','assets']} for a,b in e['areas'].items()}
summary['pass']=True
save('native-summary.json',summary)
before=json.loads((r/'product-hashes-before.json').read_text(encoding='utf-8-sig'))
after={p:hashlib.sha256((repo/p).read_bytes()).hexdigest().upper() for p in before}
assert before==after
save('source-identities-final.json',{'head':'2873f051cbea91c0122f14a08f807f374b28da75','fixedMac':'dafc64d9b33661dc4f792fd1434ff1ddca9cdfa3','productHashesUnchanged':True,'productHashes':after,'candidate':json.loads((repo/'desktop/output/windows-acceptance/current.json').read_text(encoding='utf-8-sig')),'sources':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (r/'sources').iterdir()},'macRuntimeVerified':False,'productBuildOrFullTestsRun':False})
prefix='''## 2026-10-01：W15大量独立非空且有消息引用附件库备份指定层通过，NORMAL仍开

- [报告](review/20261001-w15-referenced-backup/REPORT.md)：复用9BD295E2，新marked e2c65e69双区各200/400/600三库，共1200会话/2400消息/1200独立非空附件与1200消息引用、3带附件草稿；每区29536470附件字节，真实协议owner导入，无SQL注入。补旧1200文件引用0负载缺口。
- 六份真实语义export和双区备份/preflight/逐区明确replace/五正常Close/profile退出/diagnostic cold完整全文、附件身份字节与草稿、另区守恒30断言全过，Node exit0。独立两包2409manifest条目逐项hash/CRC/快照表计数/来源正文/资产1200/引用1200/草稿附件/各1212 object_provenance与全部receipt/task/Attempt完整守恒；Attempt和import_task为0，不冒充非空任务验收。应用13/关闭26SQLite全ok/FK0。
- 五PID退出/无forcedCleanup，三助手参数/预检返回值/条目数失败根全保；3新助手及源码快照，语法/AST通过，产品四hash与来源一致，无产品改动或构建。备份约33MB，各导出13.21/13.92秒、预检8.29/8.43秒、替换12.45/12.35秒；预算null不称性能达标。
- diagnostic状态RESTORED_RESTART_REQUIRED/fullNormalRecoveryAccepted=false，不替代NORMAL收尾。保12h153544/145716 WAITING request5及24h15856/84408 RUNNING/false；北京时间10月2日00:45:29.183与18:39:25.507才到最早终验点，不probe/改production/钟SQL/并行NORMAL。
- 完整W15/BG23/1.3仍OPEN_NOT_ACCEPTED；真实暂跳过，Mac同源码/系统/签名安装升级/高DPI多屏/NORMAL生产流式/严格IME仍开。下一独立高价值为W15真实备份替换写入中断与真实checkpoint边界，先去重旧小文件锁和占位checkpoint证据，在新有引用大库根观察真实切换阶段再精确停止；NORMAL恢复仍等释放，不制造缩时证据。无子代理/分支/提交/发布/右面板，保WIP888及全部旧候选/根，唯一阶段自动接续沿默认模型推理已授权。

'''.encode('utf-8')
preserved=[]
for rel in ['docs/CURRENT_HANDOFF.md','docs/next-codex-prompt.md','docs/WINDOWS_MASTER_PLAN.md']:
 p=repo/rel;old=p.read_bytes();p.write_bytes(prefix+old);assert p.read_bytes()[len(prefix):]==old
 preserved.append({'path':rel,'oldBytes':len(old),'oldSha256':hashlib.sha256(old).hexdigest(),'oldSuffixPreserved':True})
for rel in ['docs/review/20260919-upstream-plan-reconciliation/BUG_AND_GAP_REGISTER.md','docs/C14_LOCAL_DATA_PARITY_CONTRACT.md']:
 p=repo/rel;old=p.read_bytes()
 note='\n\n2026-10-01 W15独立非空附件大库备份增量见 '+('[报告](../../20261001-w15-referenced-backup/REPORT.md)' if 'BUG_AND' in rel else '[报告](review/20261001-w15-referenced-backup/REPORT.md)')+'：双区各1200独立非空资产/1200消息引用/29536470字节、3附件草稿，真实语义导出和备份/逐区替换/正常Close/diagnostic cold与独立Manifest、全文、私有hash、provenance守恒通过；diagnostic仍RESTORED_RESTART_REQUIRED，NORMAL收尾/真实切换中断、Mac同源码和完整W15/BG23/1.3继续打开。仅补证据，不改现行产品格式或按钮决定。\n'
 # BUG register is in a sibling review directory.
 note=note.replace('../../20261001-w15','../20261001-w15')
 p.write_bytes(old+note.encode('utf-8'));assert p.read_bytes()[:len(old)]==old
 preserved.append({'path':rel,'oldBytes':len(old),'oldSha256':hashlib.sha256(old).hexdigest(),'oldPrefixPreserved':True})
save('document-preservation.json',preserved)
