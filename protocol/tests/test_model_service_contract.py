"""Mutation tests for the offline maintenance gate; never send a Provider request."""
import copy
import importlib.util
import json
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("model_gate", ROOT / "protocol/scripts/verify-model-service-contract.py")
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class ModelServiceContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((ROOT / GATE.MANIFEST).read_text())
        cls.sources = GATE.read_sources(ROOT)

    def rejected(self, edit_manifest=None, edit_sources=None, contains=None):
        manifest, sources = copy.deepcopy(self.manifest), copy.deepcopy(self.sources)
        if edit_manifest:
            edit_manifest(manifest)
        if edit_sources:
            edit_sources(sources)
        errors = GATE.check(ROOT, manifest, sources, check_matrix=False)
        self.assertTrue(errors, "a conflicting model configuration must not pass")
        if contains:
            self.assertTrue(any(contains in error for error in errors), errors)

    def test_current_source_contract_and_generated_matrix(self):
        self.assertEqual([], GATE.check(ROOT, self.manifest, self.sources))

    def test_duplicate_provider_and_deployment_are_rejected(self):
        self.rejected(lambda m: m["providers"].append(copy.deepcopy(m["providers"][0])), contains="duplicate")
        self.rejected(lambda m: m["models"].append(copy.deepcopy(m["models"][0])), contains="duplicate")

    def test_missing_platform_model_is_rejected(self):
        self.rejected(lambda m: m["models"].pop(), contains="model set")
        self.rejected(edit_sources=lambda s: s["android_catalog"].pop(self.manifest["models"][0]["presetId"]), contains="Android")

    def test_api_id_typo_and_provider_mismatch_are_rejected(self):
        self.rejected(lambda m: m["models"][0].update(modelId="wrong-model-id"), contains="model ID")
        self.rejected(lambda m: m["models"][0].update(providerId="QWEN"), contains="provider mismatch")

    def test_profile_and_relay_ui_drift_are_rejected(self):
        self.rejected(edit_sources=lambda s: s["profiles"]["RELAYCORE_OPUS_5_5"].update(modelId="old-unverified-id"), contains="profile mismatch")
        self.rejected(edit_sources=lambda s: s["relay_esm"]["RELAYCORE_OPUS_5_5"].update(channel="wrong-group"), contains="parity")

    def test_independent_keys_and_search_interface_are_required(self):
        self.rejected(lambda m: m["providers"][-1].update(credentialGroup="RELAYCORE_GPT"), contains="namespace")
        self.rejected(lambda m: m["providers"][1].update(webRoute="QWEN_RESPONSES"), contains="binding")
        self.rejected(lambda m: m["providers"][1].update(webEndpoint="https://api.deepseek.com/v1/responses"), contains="endpoint")

    def test_media_claim_cannot_override_text_search_wire(self):
        self.rejected(lambda m: m["providers"][1].update(nativeMediaWithWeb=True), contains="media capability")

    def test_auto_order_and_duplicates_are_rejected(self):
        def reorder(s):
            s["auto_android"]["DEFAULT_TEXT"].reverse()
        self.rejected(edit_sources=reorder, contains="Auto ordering")
        def duplicate(s):
            s["auto"]["plans"]["DEFAULT_TEXT"].append(s["auto"]["plans"]["DEFAULT_TEXT"][0])
        self.rejected(edit_sources=duplicate, contains="duplicates")

    def test_ocr_cannot_be_promoted_to_chat_or_auto(self):
        def promote(m):
            row=copy.deepcopy(m["models"][0])
            row.update(presetId="GLM_OCR",providerId="ZHIPU",modelId="glm-ocr")
            m["models"].append(row)
        self.rejected(promote, contains="special model")
        self.rejected(edit_sources=lambda s: s["auto"]["plans"]["DEFAULT_TEXT"].append("GLM_OCR"), contains="special or retired")

    def test_exception_binding_and_authority_cannot_disappear(self):
        self.rejected(lambda m: m["models"][0]["exceptionIds"].clear(), contains="exception binding")
        self.rejected(lambda m: m["authorities"][0]["androidOwners"].append("app/nonexistent.kt"), contains="path missing")

    def test_wrong_schema_and_unknown_fields_fail_closed(self):
        self.rejected(lambda m: m.update(schemaVersion=2), contains="version")
        self.rejected(lambda m: m.update(schemaVersion=True), contains="version")
        self.rejected(lambda m: m["models"][0].update(secret="must-not-be-stored"), contains="schema")
        self.rejected(lambda m: m.update(models=None), contains="nonempty list")

    def test_duplicate_json_fields_and_non_json_numbers_are_rejected(self):
        for text in ['{"modelId":"first","modelId":"second"}', '{"schemaVersion":NaN}']:
            with self.assertRaises(ValueError):
                GATE.parse_json(text)

    def test_stale_matrix_is_detected_without_editing_repository(self):
        with patch.object(GATE, "render_matrix", return_value="stale generated output"):
            self.assertTrue(any("matrix stale" in e for e in GATE.check(ROOT, self.manifest, self.sources)))

    def test_required_tool_request_regression_is_rejected(self):
        read_text = Path.read_text
        def altered(path, *args, **kwargs):
            text=read_text(path,*args,**kwargs)
            if path.name == "ChatProviderAdapters.kt":
                text=text.replace('\\\"tool_choice\\\":\\\"required\\\"','\\\"tool_choice\\\":\\\"auto\\\"')
            return text
        with patch.object(Path,"read_text",altered):
            errors=GATE.check(ROOT,self.manifest,self.sources,check_matrix=False)
        self.assertTrue(any("required tool selection" in e for e in errors),errors)

    def test_explicit_live_web_access_regression_is_rejected(self):
        read_text = Path.read_text
        for owner in ["ChatProviderAdapters.kt", "desktop_ordinary_chat_v1.rs"]:
            def altered(path, *args, **kwargs):
                text = read_text(path, *args, **kwargs)
                if path.name == owner:
                    text = text.replace("external_web_access", "removed_live_access")
                return text
            with patch.object(Path, "read_text", altered):
                errors = GATE.check(ROOT, self.manifest, self.sources, check_matrix=False)
            self.assertTrue(any("Relay Responses" in e for e in errors), errors)

    def test_new_model_has_no_hardcoded_nineteen_model_ceiling(self):
        manifest, sources = copy.deepcopy(self.manifest), copy.deepcopy(self.sources)
        row = {"presetId":"NEW_MODEL_TEST","providerId":"OPENROUTER","modelId":"fixture/new-model","displayName":"New model fixture","selection":"Auto候选专用","exceptionIds":[]}
        manifest["models"].append(row)
        sources["presets"][row["presetId"]] = {"id":row["presetId"],"provider_id":row["providerId"],"model_id":row["modelId"],"display_name":row["displayName"],"chat_selectable":True}
        sources["android_catalog"][row["presetId"]] = "CHAT"
        sources["fallback_ids"][row["presetId"]] = row["modelId"]
        for task in sources["auto"]["plans"]:
            for plans in [sources["auto"]["plans"],sources["auto_android"],sources["auto_esm"]]:
                plans[task].append(row["presetId"])
        self.assertEqual([],GATE.check(ROOT,manifest,sources,check_matrix=False))
        self.assertIn("NEW_MODEL_TEST",GATE.render_matrix(manifest))


if __name__ == "__main__":
    unittest.main()
