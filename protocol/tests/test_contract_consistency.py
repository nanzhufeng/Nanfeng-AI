"""Maintenance-tool tests; no application behavior, Provider, device or fixture mutation."""
import importlib.util
import json
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
SPEC=importlib.util.spec_from_file_location('contract_consistency',ROOT/'protocol/scripts/audit-contract-consistency.py')
AUDIT=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(AUDIT)

class ContractConsistencyTests(unittest.TestCase):
    def test_current_contract_owner_coverage(self):
        self.assertEqual([],AUDIT.audit(ROOT)['errors'])
    def test_history_includes_children_but_current_sibling_reopens_rules(self):
        text='## 历史旧阶段\nold\n### 子项\nold child\n## 当前规则\nnew\n'
        self.assertEqual(['## 当前规则','new'],[l for _,l in AUDIT.active_lines(text)])
    def test_numbered_history_contains_children_but_keeps_current_siblings(self):
        for title in ['6. 历史旧阶段','5. 2026-09-03 历史边界']:
            with self.subTest(title=title):
                text='## '+title+'\nold\n### 验收\nold child\n## 当前规则\nnew\n'
                self.assertEqual(['## 当前规则','new'],[l for _,l in AUDIT.active_lines(text)])

    def test_history_product_topics_and_current_gap_order_are_active(self):
        for title in ['历史模式入口矩阵','历史资料库管理','3. 历史缺口与当前顺序']:
            with self.subTest(title=title):
                text='## '+title+'\ncurrent obligation\n'
                self.assertIn('current obligation',[l for _,l in AUDIT.active_lines(text)])

    def test_date_alone_does_not_hide_current_obligation(self):
        self.assertIn('still current',[l for _,l in AUDIT.active_lines('## 2026-10-10 当前规则\nstill current')])
    def test_old_helper_in_a_current_section_is_rejected(self):
        original=Path.read_text
        def altered(path,*args,**kwargs):
            text=original(path,*args,**kwargs)
            if path.name=='P6G_MODEL_SELECTION_AUTO_ROUTER_CONTRACT.md':text+='\n## 当前路由\n开启联网须同时具备Sol检索配置。\n'
            return text
        with patch.object(Path,'read_text',altered):
            self.assertTrue(any('known active semantic drift' in e for e in AUDIT.audit(ROOT)['errors']))
    def test_post_delivery_stale_current_obligations_are_rejected(self):
        cases = [
            ('RELAYCORE_MODEL_CURRENT_CONTRACT.md', '模型选择与发送入口简洁披露实际Luna接收方及费用。'),
            ('C12_MODEL_NETWORK_PARITY_CONTRACT.md', 'RC辅助检索才归属GPT分组。'),
            ('P6D2_CROSS_PLATFORM_COMPOSER_ATTACHMENT_ADAPTER_CONTRACT.md', '180个Unicode码点是单段拆分粒度。'),
            ('P6D_DESKTOP_SIDEBAR_INFORMATION_ARCHITECTURE_CONTRACT.md', 'Desktop 当前只存在交换包系统选择器。'),
            ('P6_WORKSPACE_EXCHANGE_V2_FIELD_FIDELITY_CONTRACT.md', 'Android 的未注册 `NfaiExchangeV2PackageWriter`。'),
            ('P3_PROVIDER_TRANSPORT_BOUNDARY_CONTRACT.md', '当前唯一 production transport 是 `DisabledNoNetworkProviderTransport`。'),
            ('P6F_CONVERSATION_TRANSCRIPT_PRESENTATION_AND_MESSAGE_ACTIONS_CONTRACT.md', '消息下方常驻信息只允许用户友好本地时间与真实模型。'),
            ('ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md', '扫描 PDF／图片可经 GLM-OCR。'),
            ('ANDROID_CONVERSATION_UI_CURRENT_CONTRACT.md', '该预设的每次文本请求必须发送reasoning_effort=max。'),
        ]
        original = Path.read_text
        for filename, obligation in cases:
            with self.subTest(contract=filename):
                def altered(path, *args, **kwargs):
                    text = original(path, *args, **kwargs)
                    return text + '\n## 当前规则\n' + obligation + '\n' if path.name == filename else text
                with patch.object(Path, 'read_text', altered):
                    self.assertTrue(any('known active semantic drift' in e for e in AUDIT.audit(ROOT)['errors']))

    def test_old_disclosure_remains_readable_inside_history(self):
        original = Path.read_text
        def altered(path, *args, **kwargs):
            text = original(path, *args, **kwargs)
            if path.name == 'RELAYCORE_MODEL_CURRENT_CONTRACT.md':
                text += '\n## 历史旧包\n### 发送入口\n模型选择与发送入口简洁披露实际Luna接收方及费用。\n'
            return text
        with patch.object(Path, 'read_text', altered):
            self.assertEqual([], AUDIT.audit(ROOT)['errors'])

    def test_scoped_default_transport_does_not_claim_global_production(self):
        original = Path.read_text
        def altered(path, *args, **kwargs):
            text = original(path, *args, **kwargs)
            if path.name == 'P3_PROVIDER_TRANSPORT_BOUNDARY_CONTRACT.md':
                text += '\n## 当前模块默认\n本P3类型边界默认采用 `DisabledNoNetworkProviderTransport`，普通生产发送由另一已注册owner持有。\n'
            return text
        with patch.object(Path, 'read_text', altered):
            self.assertEqual([], AUDIT.audit(ROOT)['errors'])

    def test_missing_source_owner_is_rejected(self):
        original=Path.read_text
        def altered(path,*args,**kwargs):
            text=original(path,*args,**kwargs)
            if path.name=='contract-owner-map-v1.json':
                value=json.loads(text);value['domains'][0]['owners'][0]='app/nonexistent-owner.kt';return json.dumps(value)
            return text
        with patch.object(Path,'read_text',altered):
            self.assertTrue(any('missing/unsafe source owner' in e for e in AUDIT.audit(ROOT)['errors']))
    def test_multiline_scope_cannot_corrupt_the_generated_index(self):
        registry=AUDIT.GATE.load_registry(ROOT);registry['contracts'][0]['scope']='first\nsecond'
        self.assertTrue(any('table-safe' in e for e in AUDIT.GATE.validate(ROOT,registry)))
    def test_malformed_owner_rows_return_diagnostics(self):
        original=Path.read_text
        for invalid in [None, {'schemaVersion':1,'domains':None},
                        {'schemaVersion':1,'domains':[None]},
                        {'schemaVersion':1,'domains':[{'domain':[], 'authority':'x',
                         'owners':[], 'reviewScope':'x'}]}]:
            with self.subTest(invalid=invalid):
                def altered(path,*args,**kwargs):
                    if path.name=='contract-owner-map-v1.json':return json.dumps(invalid)
                    return original(path,*args,**kwargs)
                with patch.object(Path,'read_text',altered):
                    self.assertEqual('FAILED',AUDIT.audit(ROOT)['state'])
if __name__=='__main__':unittest.main()
