import copy
import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE = Path(__file__).resolve().parents[1] / "scripts/verify-contract-governance.py"
SPEC = importlib.util.spec_from_file_location("contract_gate", MODULE)
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class ContractGovernanceTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name);(self.root/'docs').mkdir()
        for name in ['FIRST_CONTRACT.md','SECOND_CONTRACT.md','GOVERNANCE.md']:
            (self.root/'docs'/name).write_text('# '+name+'\n\nOriginal independent owner and evidence.\n')
        self.registry={'schemaVersion':1,'governance':'docs/GOVERNANCE.md','index':'docs/INDEX.md','domains':[{'id':'one','title':'One domain','authority':'docs/FIRST_CONTRACT.md'}],'contracts':[{'path':'docs/FIRST_CONTRACT.md','domain':'one','status':'CURRENT','scope':'First'},{'path':'docs/SECOND_CONTRACT.md','domain':'one','status':'SCOPED','scope':'Second'}]}
        self.render()

    def render(self):
        for row in self.registry['contracts']:
            path=self.root/row['path'];path.write_text(GATE.with_guard(path.read_text(),GATE.guard(row,self.registry['domains'][0],self.registry)))
        (self.root/self.registry['index']).write_text(GATE.render_index(self.registry))

    def errors(self,registry=None):
        return GATE.validate(self.root,registry or self.registry)

    def test_valid_current_and_scoped_contracts(self):
        self.assertEqual([],self.errors())

    def test_unregistered_contract_fails(self):
        (self.root/'docs/THIRD_CONTRACT.md').write_text('# New\n')
        self.assertTrue(any('coverage' in x for x in self.errors()))

    def test_duplicate_contract_and_domain_fail(self):
        for field in ['contracts','domains']:
            r=copy.deepcopy(self.registry);r[field].append(copy.deepcopy(r[field][0]))
            self.assertTrue(any('duplicate' in x for x in self.errors(r)))

    def test_two_current_contracts_cannot_own_one_domain(self):
        r=copy.deepcopy(self.registry);r['contracts'][1]['status']='CURRENT'
        self.assertTrue(any('duplicate CURRENT' in x for x in self.errors(r)))

    def test_historical_primary_cannot_reactivate_old_rules(self):
        r=copy.deepcopy(self.registry);r['contracts'][0]['status']='HISTORICAL'
        self.assertTrue(any('historical' in x for x in self.errors(r)))

    def test_orphan_or_reassigned_domain_fails(self):
        r=copy.deepcopy(self.registry);r['domains'][0]['authority']='docs/THIRD_CONTRACT.md'
        self.assertTrue(any('orphaned' in x for x in self.errors(r)))

    def test_scope_header_and_index_drift_fail(self):
        p=self.root/'docs/SECOND_CONTRACT.md';p.write_text(p.read_text().replace('领域／模块限定合同','stale'))
        self.assertTrue(any('guard stale' in x for x in self.errors()))
        self.render();p=self.root/self.registry['index'];p.write_text(p.read_text()+'stale')
        self.assertTrue(any('index stale' in x for x in self.errors()))

    def test_broken_local_reference_fails(self):
        p=self.root/'docs/FIRST_CONTRACT.md';p.write_text(p.read_text()+'\n[missing](missing.md)\n')
        self.assertTrue(any('broken local' in x for x in self.errors()))

    def test_url_encoded_existing_reference_passes(self):
        (self.root/'docs/with space.md').write_text('# ref\n')
        p=self.root/'docs/FIRST_CONTRACT.md';p.write_text(p.read_text()+'\n[reference](with%20space.md)\n')
        self.assertEqual([],self.errors())

    def test_write_scope_preserves_body_and_is_idempotent(self):
        text='# Test\n\nOriginal rules and historical evidence.\n'
        block=GATE.guard(self.registry['contracts'][0],self.registry['domains'][0],self.registry)
        changed=GATE.with_guard(text,block)
        self.assertEqual(text,GATE.without_guard(changed))
        self.assertEqual(changed,GATE.with_guard(changed,block))

    def test_unsafe_paths_and_unknown_status_fail(self):
        for field,value in [('index','../outside.md'),('index','/tmp/outside.md')]:
            r=copy.deepcopy(self.registry);r[field]=value
            self.assertTrue(self.errors(r))
        r=copy.deepcopy(self.registry);r['contracts'][1]['status']='UNKNOWN'
        self.assertTrue(self.errors(r))

    def test_index_cannot_overwrite_an_existing_contract(self):
        r=copy.deepcopy(self.registry);r['index']='docs/SECOND_CONTRACT.md'
        self.assertTrue(any('overwrite' in x for x in self.errors(r)))

    def test_reversed_scope_markers_fail_without_rewriting_body(self):
        p=self.root/'docs/SECOND_CONTRACT.md';p.write_text('# Bad\n'+GATE.END+'\n'+GATE.START+'\nOriginal\n')
        self.assertTrue(any('malformed' in x for x in self.errors()))

    def test_new_registered_contract_has_no_fixed_count_limit(self):
        (self.root/'docs/THIRD_CONTRACT.md').write_text('# New\n\nOriginal third scope.\n')
        self.registry['contracts'].append({'path':'docs/THIRD_CONTRACT.md','domain':'one','status':'SCOPED','scope':'New'})
        self.render();self.assertEqual([],self.errors())

    def test_superseded_temporary_generation_wording_cannot_return(self):
        old=self.root/'docs/FIRST_CONTRACT.md';new=self.root/'docs/ANSWER_LANGUAGE_CURRENT_CONTRACT.md';old.rename(new)
        self.registry['contracts'][0]['path']='docs/ANSWER_LANGUAGE_CURRENT_CONTRACT.md'
        self.registry['domains'][0]['authority']='docs/ANSWER_LANGUAGE_CURRENT_CONTRACT.md'
        self.render();new.write_text(new.read_text()+'\n包括其工作区、临时会话、联网／离线\n')
        self.assertTrue(any('superseded current rule' in x for x in self.errors()))


if __name__=='__main__':
    unittest.main()
