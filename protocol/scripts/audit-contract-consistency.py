#!/usr/bin/env python3
"""Offline authority/owner and known semantic-drift audit; never runs app code or network."""
import argparse
import importlib.util
import json
import re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
MAP='protocol/contracts/contract-owner-map-v1.json'
OUTPUT='docs/contracts/DOMAIN_OWNER_MAP.md'
SPEC=importlib.util.spec_from_file_location('contract_governance',ROOT/'protocol/scripts/verify-contract-governance.py')
GATE=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(GATE)
# Narrow reviewed regressions. Dates alone never turn a rule into history.
OBSOLETE={
 'docs/P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md':[r'须同时具备\s*Sol\s*检索配置',r'当前深度为Claude'],
 'docs/ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md':[r'普通文本顺序为.*Claude Fable',r'RelayCore 两项日常、四项深度追加',r'扫描 PDF／图片可经 GLM-OCR',r'该预设的每次文本请求必须.*reasoning_effort=max'],
 'docs/C12_MODEL_NETWORK_PARITY_CONTRACT.md':[r'Responses的普通显式联网及Sol附件辅助检索',r'19预设逐个验证',r'OpenRouter六款',r'RC辅助检索才归属GPT分组'],
 'docs/RELAYCORE_MODEL_CURRENT_CONTRACT.md':[r'普通聊天的联网检索继续走已批准的Sol辅助路线',r'模型选择与发送入口简洁披露.*Luna'],
 'docs/P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md':[r'180个Unicode码点是单段拆分粒度'],
 'docs/P6D_DESKTOP_SIDEBAR_INFORMATION_ARCHITECTURE_CONTRACT.md':[r'Desktop 当前只存在交换包系统选择器'],
 'docs/P6_WORKSPACE_EXCHANGE_V2_FIELD_FIDELITY_CONTRACT.md':[r'未注册\s*`NfaiExchangeV2PackageWriter`'],
 'docs/P3_PROVIDER_TRANSPORT_BOUNDARY_CONTRACT.md':[r'当前唯一 production transport 是\s*`DisabledNoNetworkProviderTransport`'],
 'docs/P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_CONTRACT.md':[r'消息下方常驻信息只允许用户友好本地时间与真实模型'],
}

def active_lines(text):
    excluded_level=None;in_fence=False
    for number,line in enumerate(text.splitlines(),1):
        if line.startswith('```'):in_fence=not in_fence
        heading=None if in_fence else re.match(r'^(#{1,6})\s+(.+)',line)
        if heading:
            level=len(heading[1])
            if excluded_level is not None and level<=excluded_level:excluded_level=None
            title=re.sub(r'^(?:[0-9]+[.)、]\s*)?(?:[0-9]{4}-[0-9]{2}-[0-9]{2}\s+)?','',heading[2])
            # Product history views and the current gap/order section remain active.
            current_history_topic=(re.match(r'历史(?:模式|资料库|对话|消息|内容|整理)',title) is not None and not re.search(r'旧阶段|历史阶段|当次验收|验收记录|阶段证据',title)) or re.search(r'历史缺口与当前(?:顺序|规则|实现)',title) is not None
            if excluded_level is None and not current_history_topic and re.match(r'(?:历史|未来|.*实验.*|.*规划.*)',title):excluded_level=level
        if excluded_level is None:yield number,line

def read_map(root,registry):
    errors=[];mapping=json.loads((root/MAP).read_text());expected={d['id']:d for d in registry['domains']};seen=set()
    if not isinstance(mapping,dict) or set(mapping)!={'schemaVersion','domains'} or type(mapping['schemaVersion']) is not int or mapping['schemaVersion']!=1:
        return {'domains':[]},['owner map schema/version mismatch']
    if not isinstance(mapping['domains'],list):
        return {'domains':[]},['owner domains must be a list']
    for row in mapping['domains']:
        if not isinstance(row,dict) or set(row)!={'domain','authority','owners','reviewScope'}:
            errors.append('invalid owner row');continue
        if not isinstance(row['domain'],str) or not isinstance(row['authority'],str):
            errors.append('invalid owner identity');continue
        domain=row['domain']
        if domain in seen or domain not in expected or row['authority']!=expected.get(domain,{}).get('authority'):errors.append('owner authority differs: '+domain)
        seen.add(domain)
        if not isinstance(row['owners'],list) or not all(isinstance(p,str) for p in row['owners']):
            errors.append('owners must be source paths: '+domain);continue
        if not row['owners'] or len(set(row['owners']))!=len(row['owners']):errors.append('empty/duplicate owners: '+domain)
        for path in row['owners']:
            if not GATE.safe_file(root,path):errors.append('missing/unsafe source owner: '+path)
        if not isinstance(row['reviewScope'],str) or not row['reviewScope'].strip():errors.append('missing review scope: '+domain)
    if seen!=set(expected):errors.append('owner domain coverage differs')
    return mapping,errors

def render(mapping,registry):
    domains={d['id']:d for d in registry['domains']}
    lines=['# 领域合同与实现定位','', '> 自动生成自 `protocol/contracts/contract-owner-map-v1.json`；这是代码定位与检查边界，不是另一份业务合同。', '', '业务规则只看[合同索引](CONTRACT_INDEX.md)指定的领域入口；源码是事实核对点。路径存在不等于全部语义、平台、UI或真实服务验收。独立/历史模块的原owner仍须按其合同读取，不能只看组合根推断接线。', '', '| 领域 | 唯一合同入口 | 当前实现定位 |', '|---|---|---|']
    for row in mapping['domains']:
        paths='、'.join(f'[{Path(p).name}](../../{p})' for p in row['owners'])
        lines.append(f"| {domains[row['domain']]['title']} | [{Path(row['authority']).name}](../../{row['authority']}) | {paths} |")
    lines+=['', '维护：修改领域入口或移动owner时更新两份登记表，运行 `python3 protocol/scripts/verify-contract-governance.py --write` 与 `python3 protocol/scripts/audit-contract-consistency.py --write`；源码行为变化仍需对应领域测试和分层验收。已知旧规则扫描只是回归哨兵，不会自动裁决新的语义冲突。']
    return '\n'.join(lines)+'\n'

def audit(root,write=False):
    registry=GATE.load_registry(root);errors=GATE.validate(root,registry)
    if errors:
        return {'state':'FAILED','contracts':0,'domains':0,'errors':errors,'review':[],
                'scope':'registry validation failed; owner/semantic audit not run'}
    mapping,map_errors=read_map(root,registry);errors+=map_errors;rows=[]
    for row in registry['contracts']:
        text=(root/row['path']).read_text();hits=[]
        for number,line in active_lines(text):
            for pattern in OBSOLETE.get(row['path'],[]):
                if re.search(pattern,line):hits.append({'line':number,'pattern':pattern})
        if hits:errors.append('known active semantic drift: '+row['path'])
        rows.append({'path':row['path'],'domain':row['domain'],'status':row['status'],'scope':row['scope'],'knownDrift':hits,'coverage':'full structure/scope; domain owners and reviewed conflict hotspots; no runtime acceptance'})
    if not map_errors:
        target=root/OUTPUT;generated=render(mapping,registry)
        if write and not errors:
            if target.exists() and '> 自动生成自 `protocol/contracts/contract-owner-map-v1.json`' not in target.read_text():errors.append('refusing to overwrite non-generated owner map')
            else:target.write_text(generated)
        elif not target.is_file() or target.read_text()!=generated:errors.append('generated owner map stale; run --write')
    return {'state':'FAILED' if errors else 'PASS','contracts':len(rows),'domains':len(mapping['domains']),'errors':errors,'review':rows,'scope':'offline metadata/owner links and explicitly reviewed obsolete clauses; not complete semantic or runtime proof'}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--repo',type=Path,default=ROOT);parser.add_argument('--write',action='store_true');parser.add_argument('--output',type=Path);args=parser.parse_args()
    try:
        result=audit(args.repo,args.write)
        if args.output:args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
        print(json.dumps({k:v for k,v in result.items() if k!='review'},ensure_ascii=False));return result['state']!='PASS'
    except (OSError,ValueError,KeyError,TypeError) as error:
        print(json.dumps({'state':'FAILED','errors':[str(error)]},ensure_ascii=False));return 1
if __name__=='__main__':raise SystemExit(main())
