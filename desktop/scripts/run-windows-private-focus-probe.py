"""Run the fixed synthetic focus probe on a desktop never switched to the screen.

No SwitchDesktop API is loaded. A suspended child joins an owned kill-on-close
Job before resuming. This is diagnostic isolation, not user-desktop acceptance.
"""
import argparse
import ctypes as C
from ctypes import wintypes as W
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import time
import uuid

spec = importlib.util.spec_from_file_location('nfai_bounded', Path(__file__).with_name('run-windows-node-bounded.py'))
bounded = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bounded)


def run(evidence, self_test, island=False, delivery_case=None):
    desktop = Path(__file__).resolve().parents[1]
    repo = desktop.parent
    evidence = evidence.resolve()
    stages = [repo / 'docs/review/20261017-windows-hidden-focus', repo / 'docs/review/20261017-windows-protected-release', repo / 'docs/review/20261017-windows-qwen-responses', repo / 'docs/review/20261017-windows-continuation-budget', repo / 'docs/review/20261017-windows-cache-usage', repo / 'docs/review/20261017-windows-relay-responses-input', repo / 'docs/review/20261017-windows-aligned-package', repo / 'docs/review/20261017-windows-new-model-late-error', repo / 'docs/review/20261017-windows-relay-search-terminal', repo / 'docs/review/20261017-windows-search-safe-package', repo / 'docs/review/20261017-windows-service-identity-admission', repo / 'docs/review/20261017-windows-cancel-measurement', repo / 'docs/review/20261017-windows-retrieval-stop', repo / 'docs/review/20261017-windows-cancel-safe-package', repo / 'docs/review/20261017-windows-60-regression', repo / 'docs/review/20261017-windows-image-navigation', repo / 'docs/review/20261017-windows-media-close-native', repo / 'docs/review/20261017-windows-media-safe-package', repo / 'docs/review/20261017-windows-audio-window-exit', repo / 'docs/review/20261017-windows-evidence-qualifiers', repo / 'docs/review/20261017-windows-evidence-exit-package', repo / 'docs/review/20261017-windows-evidence-contradiction', repo / 'docs/review/20261017-windows-returned-model-tail', repo / 'docs/review/20261017-windows-model-keyboard-focus', repo / 'docs/review/20261017-windows-model-tab-navigation', repo / 'docs/review/20261017-windows-model-navigation-owner', repo / 'docs/review/20261017-windows-model-safe-package', repo / 'docs/review/20261017-windows-sync-partial-usage', repo / 'docs/review/20261017-windows-cross-area-exit', repo / 'docs/review/20261017-windows-call-window-snapshot', repo / 'docs/review/20261017-windows-call-window-package', repo / 'docs/review/20261017-windows-backup-call-facts', repo / 'docs/review/20261017-windows-cancel-backup-receipts', repo / 'docs/review/20261017-windows-xlsx-sparse-columns', repo / 'docs/review/20261017-windows-xlsx-package', repo / 'docs/review/20261017-windows-pptx-soft-breaks', repo / 'docs/review/20261017-windows-pptx-package', repo / 'docs/review/20261017-windows-title-double-open', repo / 'docs/review/20261017-windows-vctip-lifecycle', repo / 'docs/review/20261017-windows-title-package', repo / 'docs/review/20261018-windows-docx-expanded-breaks', repo / 'docs/review/20261018-windows-retrieval-physical-count', repo / 'docs/review/20261018-windows-vctip-instance-timing', repo / 'docs/review/20261018-windows-docx-count-package', repo / 'docs/review/20261018-windows-stream-leading-bom', repo / 'docs/review/20261018-windows-stream-format-measurement', repo / 'docs/review/20261018-windows-stream-unknown-measurement', repo / 'docs/review/20261018-windows-history-accounting-recovery', repo / 'docs/review/20261018-windows-title-unicode', repo / 'docs/review/20261018-windows-entry-route-ownership', repo / 'docs/review/20261018-windows-history-receipt-backup', repo / 'docs/review/20261018-windows-stream-line-endings', repo / 'docs/review/20261018-windows-xlsx-phonetic-values', repo / 'docs/review/20261018-windows-docx-run-hyphens', repo / 'docs/review/20261018-windows-qwen-image-capability', repo / 'docs/review/20261018-windows-responses-pdf-input', repo / 'docs/review/20261018-windows-pptx-slide-order', repo / 'docs/review/20261018-windows-xlsx-workbook-order', repo / 'docs/review/20261018-windows-pptx-attribute-entities', repo / 'docs/review/20261018-windows-auto-verified-image', repo / 'docs/review/20261019-windows-scope-deadline-measurement', repo / 'docs/review/20261019-windows-failure-model-identity', repo / 'docs/review/20261019-windows-live-call-checkpoint', repo / 'docs/review/20261019-windows-scoped-answer-checkpoint', repo / 'docs/review/20261019-windows-docx-section-parts', repo / 'docs/review/20261019-windows-docx-note-references', repo / 'docs/review/20261019-windows-docx-current-revisions', repo / 'docs/review/20261019-windows-retrieval-live-progress', repo / 'docs/review/20261019-windows-retrieval-group-summary', repo / 'docs/review/20261019-windows-retrieval-call-outcomes', repo / 'docs/review/20261019-windows-mixed-material-send', repo / 'docs/review/20261019-windows-material-read-role', repo / 'docs/review/20261019-windows-material-source-order', repo / 'docs/review/20261019-windows-material-name-metadata', repo / 'docs/review/20261019-windows-material-name-lifecycle', repo / 'docs/review/20261019-windows-sync-material-blocks', repo / 'docs/review/20261019-windows-ordinary-stop-workspace', repo / 'docs/review/20261019-windows-runtime-refresh-owner', repo / 'docs/review/20261019-windows-island-source-actions', repo / 'docs/review/20261020-windows-batch-recycle-retry', repo / 'docs/review/20261020-windows-batch-recycle-close', repo / 'docs/review/20261020-windows-island-cadence', repo / 'docs/review/20261020-windows-companion-failures', repo / 'docs/review/20261020-windows-companion-feedback', repo / 'docs/review/20261020-windows-companion-disclosure', repo / 'docs/review/20261020-windows-companion-prerequisite', repo / 'docs/review/20261020-windows-companion-material', repo / 'docs/review/20261020-windows-companion-native-material', repo / 'docs/review/20261020-windows-native-material-interrupt', repo / 'docs/review/20261020-windows-native-material-write', repo / 'docs/review/20261020-windows-qwen-deepseek-native-material', repo / 'docs/review/20261020-windows-provider-refusal', repo / 'docs/review/20261020-windows-json-messages-refusal', repo / 'docs/review/20261020-windows-title-conflict-recovery', repo / 'docs/review/20261020-windows-media-open-order', repo / 'docs/review/20261020-windows-selection-intent', repo / 'docs/review/20261020-windows-sparse-usage-cost', repo / 'docs/review/20261020-windows-docx-alternatives', repo / 'docs/review/20261020-windows-office-alternatives', repo / 'docs/review/20261020-windows-xlsx-empty-strings', repo / 'docs/review/20261020-windows-entry-visibility', repo / 'docs/review/20261020-windows-markdown-fences', repo / 'docs/review/20261020-windows-inline-code', repo / 'docs/review/20261020-windows-multiline-code', repo / 'docs/review/20261020-windows-model-probe-owner', repo / 'docs/review/20261020-windows-probe-response', repo / 'docs/review/20261020-windows-docx-table-revisions', repo / 'docs/review/20261020-windows-list-continuation', repo / 'docs/review/20261020-windows-reminder-progress', repo / 'docs/review/20261020-windows-reminder-draft-progress', repo / 'docs/review/20261020-windows-unsaved-key-acceptance', repo / 'docs/review/20261020-windows-list-blocks', repo / 'docs/review/20261020-windows-relaycore-keys', repo / 'docs/review/20261020-windows-docx-paragraph-marks', repo / 'docs/review/20261020-windows-new-chat-model-owner', repo / 'docs/review/20261020-windows-new-chat-send-choice', repo / 'docs/review/20261020-windows-sse-multiline', repo / 'docs/review/20261020-windows-pptx-text-namespaces', repo / 'docs/review/20261020-windows-xlsx-text-namespaces', repo / 'docs/review/20261020-windows-ocr-probe-selection', repo / 'docs/review/20261020-windows-continuation-stream-echo', repo / 'docs/review/20261021-windows-xlsx-xstring', repo / 'docs/review/20261021-windows-retrieval-group-acceptance', repo / 'docs/review/20261021-windows-new-chat-choice-acceptance', repo / 'docs/review/20261021-windows-title-write-recovery', repo / 'docs/review/20261021-windows-credential-save-target', repo / 'docs/review/20261021-windows-credential-delete-target', repo / 'docs/review/20261021-windows-entry-retention-acceptance', repo / 'docs/review/20261021-windows-idle-generation-acceptance', repo / 'docs/review/20261021-windows-usage-model-identity', repo / 'docs/review/20261021-windows-call-route-snapshot', repo / 'docs/review/20261021-windows-backup-call-metadata', repo / 'docs/review/20261021-windows-model-selection-acceptance', repo / 'docs/review/20261021-windows-footer-returned-model', repo / 'docs/review/20261021-windows-composer-catalog-acceptance', repo / 'docs/review/20261021-windows-auto-composer-text', repo / 'docs/review/20261021-windows-auto-routing-acceptance', repo / 'docs/review/20261021-windows-auto-ancestor-materials', repo / 'docs/review/20261021-windows-auto-eligibility-acceptance', repo / 'docs/review/20261021-windows-inline-title-ime-tail', repo / 'docs/review/20261021-windows-source-text-preview', repo / 'docs/review/20261021-windows-inline-title-acceptance', repo / 'docs/review/20261021-windows-selection-read-revision', repo / 'docs/review/20261021-windows-draft-read-acceptance', repo / 'docs/review/20261021-windows-model-feedback-acceptance', repo / 'docs/review/20261021-windows-search-source-types', repo / 'docs/review/20261022-windows-ocr-chat-isolation', repo / 'docs/review/20261022-windows-retired-catalog-isolation', repo / 'docs/review/20261022-windows-registry-mapping-isolation', repo / 'docs/review/20261022-windows-search-tool-status', repo / 'docs/review/20261022-windows-model-list-viewport', repo / 'docs/review/20261022-windows-registry-session', repo / 'docs/review/20261022-windows-responses-terminal-status', repo / 'docs/review/20261022-windows-stream-terminal-matrix', repo / 'docs/review/20261022-windows-structured-source-matrix', repo / 'docs/review/20261022-windows-failure-route-identity', repo / 'docs/review/20261022-windows-call-registration-gate', repo / 'docs/review/20261022-windows-cost-currency-projection', repo / 'docs/review/20261022-windows-cost-source-provenance', repo / 'docs/review/20261022-windows-terminal-facts-roundtrip', repo / 'docs/review/20261022-windows-single-topic-facts', repo / 'docs/review/20261022-windows-material-scope-limit', repo / 'docs/review/20261023-windows-chinese-evidence-qualifiers', repo / 'docs/review/20261023-windows-answer-context-feedback', repo / 'docs/review/20261023-windows-stop-late-evidence', repo / 'docs/review/20261023-windows-cache-question-freshness', repo / 'docs/review/20261023-windows-orphan-checkpoint-cleanup', repo / 'docs/review/20261023-windows-sync-search-lifecycle', repo / 'docs/review/20261023-windows-checkpoint-backup-lifecycle', repo / 'docs/review/20261023-windows-full-delete-search-lifecycle', repo / 'docs/review/20261023-windows-plain-material-facts', repo / 'docs/review/20261023-windows-plain-material-sources', repo / 'docs/review/20261023-windows-paragraph-source-boundaries', repo / 'docs/review/20261023-windows-portable-cost-facts', repo / 'docs/review/20261024-windows-legacy-service-minimum', repo / 'docs/review/20261024-windows-model-window-tab', repo / 'docs/review/20261024-windows-relay-native-search-minimum', repo / 'docs/review/20261024-windows-search-exceptions-minimum', repo / 'docs/review/20261024-windows-native-text-protocols', repo / 'docs/review/20261024-windows-companion-minimum', repo / 'docs/review/20261024-windows-settlement-model-identity', repo / 'docs/review/20261024-portable-model-identity-v2', repo / 'docs/review/20261024-room-platform-verification', repo / 'docs/review/20261024-windows-terminal-backup-current', repo / 'docs/review/20261024-windows-reopen-protocol', repo / 'docs/review/20261024-windows-preview-refresh-deadline']
    stage = evidence.parent
    stages.append(repo / 'docs/review/20261024-windows-continuation-minimum')
    stages.append(repo / 'docs/review/20261024-windows-continuity-minimum')
    stages.append(repo / 'docs/review/20261024-windows-island-control-minimum')
    stages.append(repo / 'docs/review/20261024-windows-island-lifecycle-minimum')
    stages.append(repo / 'docs/review/20261024-windows-cloud-unlink-minimum')
    stages.append(repo / 'docs/review/20261024-windows-reader-minimum')
    stages.append(repo / 'docs/review/20261024-windows-video-resume-minimum')
    stages.append(repo / 'docs/review/20261024-windows-reply-emphasis-minimum')
    stages.append(repo / 'docs/review/20261024-windows-product-scope-minimum')
    stages.append(repo / 'docs/review/20261024-windows-appearance-feedback-owner')
    stages.append(repo / 'docs/review/20261025-windows-about-feedback-owner')
    stages.append(repo / 'docs/review/20261025-windows-resource-build-gate')
    stages.append(repo / 'docs/review/20261025-windows-unsupported-media')
    stages.append(repo / 'docs/review/20261025-windows-positive-attachments')
    stages.append(repo / 'docs/review/20261025-windows-stop-attempt-owner')
    stages.append(repo / 'docs/review/20261025-windows-messages-usage-fidelity')
    stages.append(repo / 'docs/review/20261025-windows-current-portable-facts')
    stages.append(repo / 'docs/review/20261025-windows-unsupported-tool-accounting')
    stages.append(repo / 'docs/review/20261025-windows-attachment-draft-owner')
    stages.append(repo / 'docs/review/20261025-windows-current-mixed-materials')
    stages.append(repo / 'docs/review/20261026-windows-shell-reentry')
    stages.append(repo / 'docs/review/20261026-windows-popup-geometry')
    stages.append(repo / 'docs/review/20261026-windows-menu-font-scaling')
    stages.append(repo / 'docs/review/20261026-windows-8ae-delivery')
    stages.append(repo / 'docs/review/20261026-windows-install-upgrade')
    stages.append(repo / 'docs/review/20261026-windows-public-cloud-presence')
    stages.append(repo / 'docs/review/20261026-windows-21fa-delivery')
    stages.append(repo / 'docs/review/20261026-windows-footer-overflow')
    stages.append(repo / 'docs/review/20261026-windows-bdd-delivery')
    stages.append(repo / 'docs/review/20261026-windows-composer-font')
    stages.append(repo / 'docs/review/20261026-windows-unicode-backup')
    stages.append(repo / 'docs/review/20261027-windows-current-delivery')
    stages.append(repo / 'docs/review/20261027-windows-data-settings-font')
    stages.append(repo / 'docs/review/20261027-windows-e5dc-delivery')
    stages.append(repo / 'docs/review/20261027-windows-import-results-flow')
    stages.append(repo / 'docs/review/20261027-windows-import-failure-recovery')
    stages.append(repo / 'docs/review/20261027-windows-settings-pages-font')
    stages.append(repo / 'docs/review/20261027-windows-settings-picker-keyboard')
    stages.append(repo / 'docs/review/20261027-windows-disclosure-keyboard')
    stages.append(repo / 'docs/review/20261027-windows-settings-switch-keyboard')
    stages.append(repo / 'docs/review/20261027-windows-exit-stall')
    stages.append(repo / 'docs/review/20261027-windows-exit-real-deadline')
    stages.append(repo / 'docs/review/20261027-windows-modal-ime')
    modal_ime_case = delivery_case in ('modal-ime-red','modal-ime-current','modal-ime-cold')
    if modal_ime_case and stage != stages[228]:
        raise ValueError('Modal IME requires pinned stage')
    stages.append(repo / 'docs/review/20261027-windows-find-ime-lifecycle')
    find_ime_case = delivery_case in ('find-ime-red','find-ime-current','find-ime-navigation','find-ime-navigation-remainder')
    if find_ime_case and stage != stages[229]:
        raise ValueError('Find IME lifecycle requires pinned stage')
    stages.append(repo / 'docs/review/20261027-windows-native-file-picker')
    native_picker_case = delivery_case == 'native-picker-current'
    if native_picker_case and stage != stages[230]:
        raise ValueError('Native picker requires pinned stage')
    stages.append(repo / 'docs/review/20261028-windows-find-occurrence')
    occurrence_case = delivery_case in ('find-occurrence-red','find-occurrence-current')
    if occurrence_case and stage != stages[231]:
        raise ValueError('Find occurrence requires pinned stage')
    stages.append(repo / 'docs/review/20261028-windows-find-special-characters')
    find_entities_case = delivery_case in ('find-entities-red','find-entities-current')
    if find_entities_case and stage != stages[232]:
        raise ValueError('Find entity text fidelity requires pinned stage')
    stages.append(repo / 'docs/review/20261028-windows-native-picker')
    native_picker_args_case = delivery_case in ('native-picker-args-current','native-picker-args-remainder','native-picker-args-save-cold')
    if native_picker_args_case and stage != stages[233]:
        raise ValueError('Native picker argument fixture requires pinned stage')
    stages.append(repo / 'docs/review/20261028-windows-archive-font')
    archive_font_case = delivery_case in ('archive-font-red','archive-font-current')
    if archive_font_case and stage != stages[234]:
        raise ValueError('Archive font requires pinned stage')
    stages.append(repo / 'docs/review/20261028-windows-island-worker-exit')
    island_worker_exit_case = delivery_case == 'island-worker-current'
    if island_worker_exit_case and stage != stages[235]:
        raise ValueError('Island worker exit requires pinned stage')
    stages.append(repo / 'docs/review/20261028-windows-island-stop-queue')
    island_stop_queue_case = delivery_case == 'island-queue-current'
    if island_stop_queue_case and stage != stages[236]:
        raise ValueError('Island stop queue requires pinned stage')
    stages.append(repo / 'docs/review/20261028-windows-chat-shell-font')
    chat_shell_font_case = delivery_case in ('chat-font-red','chat-font-projected','chat-font-current')
    if chat_shell_font_case and stage != stages[237]:
        raise ValueError('Chat shell font requires pinned stage')
    stages.append(repo / 'docs/review/20261028-windows-camera-fixture')
    camera_fixture_case = delivery_case in ('camera-fixture-preflight','camera-fixture-current')
    if camera_fixture_case and stage != stages[238]:
        raise ValueError('Synthetic camera requires pinned stage')
    stages.append(repo / 'docs/review/20261028-windows-camera-ready')
    camera_ready_case = delivery_case == 'camera-ready'
    if camera_ready_case and stage != stages[239]:
        raise ValueError('Camera ready requires pinned stage')
    stages.append(repo / 'docs/review/20261028-windows-camera-focus')
    camera_focus_case = delivery_case == 'camera-focus'
    if camera_focus_case and stage != stages[240]:
        raise ValueError('Camera focus requires pinned stage')
    real_deadline_case = delivery_case == 'exit-real-deadline'
    if real_deadline_case and stage != stages[227]:
        raise ValueError('Real exit deadline requires pinned stage')
    exit_stall_case = delivery_case == 'exit-stall-preflight'
    if exit_stall_case and stage != stages[226]:
        raise ValueError('Exit stall preflight requires pinned stage')
    switch_keyboard_case = delivery_case in ('settings-switch-red','settings-switch-current')
    if switch_keyboard_case and stage != stages[225]:
        raise ValueError('Settings switch keyboard requires pinned stage')
    disclosure_keyboard_case = delivery_case in ('action-menu-red','action-menu-current')
    if disclosure_keyboard_case and stage != stages[224]:
        raise ValueError('Disclosure keyboard requires pinned stage')
    settings_keyboard_case = delivery_case in ('settings-picker-red','settings-picker-current')
    if settings_keyboard_case and stage != stages[223]:
        raise ValueError('Settings keyboard requires pinned stage')
    settings_pages_case = delivery_case in ('settings-pages-current','settings-pages-projected')
    if settings_pages_case and stage != stages[222]:
        raise ValueError('Settings pages requires pinned stage')
    import_failure_case = delivery_case == 'import-failure-current'
    if import_failure_case and stage != stages[221]:
        raise ValueError('Import failure recovery requires pinned stage')
    import_results_case = delivery_case == 'import-results-current'
    if import_results_case and stage != stages[220]:
        raise ValueError('Import results requires pinned stage')
    e5dc_delivery_case = delivery_case in ('e5dc-delivery-install','e5dc-delivery-recovery','e5dc-delivery-default')
    if e5dc_delivery_case and stage != stages[219]:
        raise ValueError('Current delivery requires pinned stage')
    data_font_case = delivery_case in ('data-font-current','data-font-baseline')
    if data_font_case and stage != stages[218]:
        raise ValueError('Data settings font requires pinned stage')
    current_delivery_case = delivery_case in ('current-delivery-install','current-delivery-recovery','current-delivery-default')
    if current_delivery_case and stage != stages[217]:
        raise ValueError('Current delivery requires pinned stage')
    unicode_backup_case = delivery_case == 'unicode-backup-current'
    if unicode_backup_case and stage != stages[216]:
        raise ValueError('Unicode backup requires pinned stage')
    composer_font_case = delivery_case in ('composer-font-current','composer-font-projected')
    if composer_font_case and stage != stages[215]:
        raise ValueError('Composer font requires pinned stage')
    bdd_delivery_case = delivery_case in ('bdd-install-current','bdd-recovery-current','bdd-default-current')
    if bdd_delivery_case and stage != stages[214]:
        raise ValueError('Public cloud delivery requires pinned stage')
    footer_overflow_case = delivery_case in ('footer-overflow-current','footer-overflow-projected','footer-overflow-recovery')
    if footer_overflow_case and stage != stages[213]:
        raise ValueError('Long footer qualification requires pinned stage')
    cloud_delivery_case = delivery_case in ('public-cloud-install-current','public-cloud-recovery-current','public-cloud-default-current')
    if cloud_delivery_case and stage != stages[212]:
        raise ValueError('Public cloud delivery requires pinned stage')
    public_cloud_case = delivery_case == 'public-cloud-current'
    if public_cloud_case and stage != stages[211]:
        raise ValueError('Public cloud presence requires pinned stage')
    install_roundtrip_case = delivery_case in ('install-roundtrip-current','install-roundtrip-recovery','install-default-current')
    if install_roundtrip_case and stage != stages[210]:
        raise ValueError('Install roundtrip requires pinned stage')
    release_entry_case = delivery_case == 'release-entry-current'
    if release_entry_case and stage != stages[209]:
        raise ValueError('Release entry requires pinned stage')
    menu_font_case = delivery_case == 'menu-font-current'
    if menu_font_case and stage != stages[208]:
        raise ValueError('Menu typography requires pinned stage')
    popup_input_case = delivery_case == 'popup-input-current'
    if popup_input_case and stage != stages[207]:
        raise ValueError('Popup input requires pinned stage')
    shell_class_case = delivery_case == 'shell-class-reentry'
    if shell_class_case and stage != stages[206]:
        raise ValueError('Shell class reentry requires pinned stage')
    current_mixed_case = delivery_case == 'current-mixed-materials'
    if current_mixed_case and stage != stages[205]:
        raise ValueError('Current mixed materials requires pinned stage')
    attachment_draft_case = delivery_case == 'attachment-draft-current'
    if attachment_draft_case and stage != stages[204]:
        raise ValueError('Attachment draft requires pinned stage')
    unsupported_tool_case = delivery_case == 'unsupported-tool-current'
    if unsupported_tool_case and stage != stages[203]:
        raise ValueError('Unsupported tool accounting requires pinned stage')
    portable_current_case = delivery_case in ('portable-current-debug','portable-current-release')
    if portable_current_case and stage != stages[202]:
        raise ValueError('Current portable facts require pinned stage')
    messages_usage_case = delivery_case == 'messages-usage-current'
    if messages_usage_case and stage != stages[201]:
        raise ValueError('Messages usage requires pinned stage')
    stop_attempt_case = delivery_case == 'stop-attempt-current'
    if stop_attempt_case and stage != stages[200]:
        raise ValueError('Stop Attempt requires pinned stage')
    supported_material_case = delivery_case == 'supported-material-current'
    if supported_material_case and stage != stages[199]:
        raise ValueError('Supported material requires pinned stage')
    unsupported_media_case = delivery_case == 'unsupported-media-current'
    if unsupported_media_case and stage != stages[198]:
        raise ValueError('Unsupported media requires pinned stage')
    resource_build_case = delivery_case == 'resource-build-current'
    if resource_build_case and stage != stages[197]:
        raise ValueError('Resource build requires pinned stage')
    about_feedback_case = delivery_case == 'about-feedback-current'
    if about_feedback_case and stage != stages[196]:
        raise ValueError('About feedback requires pinned stage')
    appearance_feedback_case = delivery_case == 'appearance-feedback-current'
    if appearance_feedback_case and stage != stages[195]:
        raise ValueError('Appearance feedback requires pinned stage')
    product_scope_minimum_case = delivery_case == 'product-scope-minimum-current'
    if product_scope_minimum_case and stage != stages[194]:
        raise ValueError('Product scope minimum requires pinned stage')
    reply_emphasis_minimum_case = delivery_case == 'reply-emphasis-minimum-current'
    if reply_emphasis_minimum_case and stage != stages[193]:
        raise ValueError('Reply emphasis minimum requires pinned stage')
    video_resume_minimum_case = delivery_case == 'video-resume-minimum-current'
    if video_resume_minimum_case and stage != stages[192]:
        raise ValueError('Video resume minimum requires pinned stage')
    reader_minimum_case = delivery_case == 'reader-minimum-current'
    if reader_minimum_case and stage != stages[191]:
        raise ValueError('Reader minimum requires pinned stage')
    cloud_unlink_case = delivery_case in ('cloud-unlink-current-ui','cloud-unlink-current-race')
    if cloud_unlink_case and stage != stages[190]:
        raise ValueError('Cloud unlink requires pinned stage')
    island_lifecycle_case = delivery_case == 'island-lifecycle-current'
    if island_lifecycle_case and stage != stages[189]:
        raise ValueError('Island lifecycle requires pinned stage')
    island_control_case = delivery_case in ('island-control-peer-red', 'island-control-peer-current', 'island-control-sources-current')
    if island_control_case and stage != stages[188]:
        raise ValueError('Island control requires pinned stage')
    continuity_minimum_case = delivery_case in ('continuity-minimum-work', 'continuity-minimum-unavailable', 'continuity-minimum-crash')
    if continuity_minimum_case and stage != stages[187]:
        raise ValueError('Continuity minimum requires pinned stage')
    continuation_minimum_case = delivery_case in ('continuation-minimum-complete', 'continuation-minimum-empty', 'continuation-minimum-interrupted', 'continuation-minimum-reverify', 'continuation-minimum-budget')
    if continuation_minimum_case and stage != stages[186]:
        raise ValueError('Continuation minimum requires pinned stage')
    preview_deadline_case = delivery_case in ('preview-deadline-current','preview-deadline-stop')
    if preview_deadline_case and stage != stages[185]:
        raise ValueError('Preview deadline requires pinned stage')
    reopen_protocol_case = delivery_case in ('reopen-protocol-current','reopen-protocol-cross')
    if reopen_protocol_case and stage != stages[184]:
        raise ValueError('Reopen protocol requires pinned stage')
    terminal_backup_case = delivery_case == 'terminal-backup-current'
    if terminal_backup_case and stage != stages[183]:
        raise ValueError('Terminal backup requires pinned stage')
    room_facts_startup_case = delivery_case == 'room-facts-startup'
    if room_facts_startup_case and stage != stages[182]:
        raise ValueError('Room facts startup requires pinned stage')
    portable_identity_case = delivery_case in ('portable-identity-debug','portable-identity-release')
    if portable_identity_case and stage != stages[181]:
        raise ValueError('Portable identity v2 requires pinned stage')
    model_identity_startup_case = delivery_case == 'model-identity-startup'
    if model_identity_startup_case and stage != stages[180]:
        raise ValueError('Model identity startup requires pinned stage')
    companion_current_case = delivery_case == 'companion-current'
    if companion_current_case and stage != stages[179]:
        raise ValueError('Companion minimum requires pinned stage')
    native_text_current_case = delivery_case == 'native-text-current'
    if native_text_current_case and stage != stages[178]:
        raise ValueError('Native text protocols require pinned stage')
    search_exceptions_case = delivery_case == 'search-exceptions-current'
    if search_exceptions_case and stage != stages[177]:
        raise ValueError('Search exceptions require pinned stage')
    relay_native_current_case = delivery_case == 'relay-native-current'
    if relay_native_current_case and stage != stages[176]:
        raise ValueError('Current Relay native search requires pinned stage')
    model_window_tab_case = delivery_case in ('model-window-tab-red','model-window-tab-fixed')
    if model_window_tab_case and stage != stages[175]:
        raise ValueError('Model window Tab requires pinned stage')
    legacy_service_current_case = delivery_case == 'legacy-service-current'
    if legacy_service_current_case and stage != stages[174]:
        raise ValueError('Current legacy service requires pinned stage')
    portable_cost_case = delivery_case in ('portable-cost-debug','portable-cost-release')
    if portable_cost_case and stage != stages[173]:
        raise ValueError('Portable cost facts requires pinned stage')
    paragraph_sources_case = delivery_case == 'paragraph-source-boundaries'
    if paragraph_sources_case and stage != stages[172]:
        raise ValueError('Paragraph sources requires pinned stage')
    plain_sources_case = delivery_case == 'plain-material-sources'
    if plain_sources_case and stage != stages[171]:
        raise ValueError('Plain material sources requires pinned stage')
    plain_material_case = delivery_case == 'plain-material-facts'
    if plain_material_case and stage != stages[170]:
        raise ValueError('Plain material facts requires pinned stage')
    full_delete_search_case = delivery_case in ('full-delete-search-lifecycle','full-delete-search-lifecycle-fresh')
    if full_delete_search_case and stage != stages[169]:
        raise ValueError('Full delete search lifecycle requires pinned stage')
    checkpoint_backup_case = delivery_case in ('checkpoint-backup','checkpoint-backup-debug-rollback','checkpoint-backup-final','checkpoint-backup-fixed')
    if checkpoint_backup_case and stage != stages[168]:
        raise ValueError('Checkpoint backup requires pinned stage')
    sync_search_case = delivery_case in ('sync-search-debug','sync-search-release')
    if sync_search_case and stage != stages[167]:
        raise ValueError('Sync search lifecycle requires pinned stage')
    orphan_checkpoint_case = delivery_case in ('orphan-checkpoint-baseline','orphan-checkpoint-fixed')
    if orphan_checkpoint_case and stage != stages[166]:
        raise ValueError('Orphan checkpoint requires pinned stage')
    cache_question_case = delivery_case in ('cache-question-red','cache-question-red-resume','cache-question-live','cache-question-static','cache-question-live-keyed','cache-question-static-keyed')
    if cache_question_case and stage != stages[165]:
        raise ValueError('Cache question freshness requires its pinned stage')
    stop_late_evidence_case = delivery_case == 'stop-late-evidence'
    if stop_late_evidence_case and stage != stages[164]:
        raise ValueError('Stop late evidence requires its pinned stage')
    answer_context_feedback_case = delivery_case in ('answer-context-red','answer-context-fixed','answer-context-old-readback')
    if answer_context_feedback_case and stage != stages[163]:
        raise ValueError('Answer context feedback requires its pinned stage')
    chinese_evidence_case = delivery_case in ('chinese-evidence-red','chinese-evidence-fixed','chinese-evidence-readback')
    if chinese_evidence_case and stage != stages[162]:
        raise ValueError('Chinese evidence requires its pinned stage')
    if stage not in stages or evidence.exists():
        raise ValueError('A new child directory of the fixed focus stage is required')
    entry_retention_case = delivery_case in ('entry-retention-acceptance','entry-retention-acceptance-entry')
    if entry_retention_case and stage != stages[127]:
        raise ValueError('Entry retention acceptance requires its pinned stage')
    idle_generation_case = delivery_case == 'idle-generation-acceptance'
    if idle_generation_case and stage != stages[128]:
        raise ValueError('Idle generation acceptance requires its pinned stage')
    material_scope_limit_case = delivery_case in ('material-limit-red','material-limit-fixed','material-limit-entry','material-limit-limited','material-limit-failure','material-limit-empty-red','material-limit-empty-fixed','material-limit-zero-fixed','material-limit-failure-current','material-limit-empty-current')
    if material_scope_limit_case and stage != stages[161]:
        raise ValueError('Material scope limit requires its pinned stage')
    single_topic_case = delivery_case in ('single-topic-facts','single-topic-entry','single-topic-resume','single-topic-mixed')
    if single_topic_case and stage != stages[160]:
        raise ValueError('Single topic facts requires its pinned stage')
    terminal_facts_case = delivery_case in ('terminal-facts-debug','terminal-facts-release','terminal-facts-entry')
    if terminal_facts_case and stage != stages[159]:
        raise ValueError('Terminal facts requires its pinned stage')
    cost_source_provenance_case = delivery_case in ('cost-source-red','cost-source-fixed','cost-source-entry')
    if cost_source_provenance_case and stage != stages[158]:
        raise ValueError('Cost source provenance requires its pinned stage')
    cost_currency_projection_case = delivery_case in ('cost-currency-projection','cost-currency-resume','cost-currency-inspect','cost-currency-fixed','cost-currency-entry')
    if cost_currency_projection_case and stage != stages[157]:
        raise ValueError('Cost currency projection requires its pinned stage')
    call_registration_gate_case = delivery_case == 'call-registration-gate'
    if call_registration_gate_case and stage != stages[156]:
        raise ValueError('Call registration gate requires its pinned stage')
    failure_route_identity_case = delivery_case in ('failure-route-identity','failure-route-identity-resume')
    if failure_route_identity_case and stage != stages[155]:
        raise ValueError('Failure route identity requires its pinned stage')
    structured_source_matrix_case = delivery_case == 'structured-source-matrix'
    if structured_source_matrix_case and stage != stages[154]:
        raise ValueError('Structured source matrix requires its pinned stage')
    stream_terminal_matrix_case = delivery_case in ('stream-terminal-matrix','stream-terminal-display')
    if stream_terminal_matrix_case and stage != stages[153]:
        raise ValueError('Stream terminal matrix requires its pinned stage')
    responses_terminal_case = delivery_case in ('responses-terminal-red','responses-terminal-fixed','responses-terminal-entry')
    if responses_terminal_case and stage != stages[152]:
        raise ValueError('Responses terminal cases require their pinned stage')
    registry_session_case = delivery_case in ('registry-session','registry-bundled')
    if registry_session_case and stage != stages[151]:
        raise ValueError('Registry session case requires its pinned stage')
    model_list_viewport_case = delivery_case in ('model-list-viewport-inspect','model-list-viewport-fixed','model-list-viewport-entry')
    if model_list_viewport_case and stage != stages[150]:
        raise ValueError('Model list viewport cases require their pinned stage')
    search_tool_status_case = delivery_case in ('search-tool-status-red','search-tool-status-fixed','search-tool-status-entry')
    if search_tool_status_case and stage != stages[149]:
        raise ValueError('Search tool status cases require their pinned stage')
    registry_mapping_case = delivery_case in ('registry-mapping-red','registry-mapping-fixed','registry-mapping-entry','registry-mapping-public-refresh','registry-mapping-public-cold')
    if registry_mapping_case and stage != stages[148]:
        raise ValueError('Registry mapping cases require their pinned stage')
    retired_catalog_case = delivery_case in ('retired-catalog','retired-catalog-entry')
    if retired_catalog_case and stage != stages[147]:
        raise ValueError('Retired catalog cases require their pinned stage')
    ocr_chat_isolation_case = delivery_case in ('ocr-chat-isolation','ocr-chat-isolation-entry')
    if ocr_chat_isolation_case and stage != stages[146]:
        raise ValueError('OCR chat isolation requires its pinned stage')
    source_annotation_case = delivery_case in ('source-annotation-red','source-annotation-fixed','source-annotation-entry')
    if source_annotation_case and stage != stages[145]:
        raise ValueError('Source annotation cases require their pinned stage')
    model_feedback_acceptance_case = delivery_case in ('model-feedback-acceptance','model-feedback-entry')
    if model_feedback_acceptance_case and stage != stages[144]:
        raise ValueError('Model feedback acceptance requires its pinned stage')
    draft_read_acceptance_case = delivery_case == 'draft-read-acceptance'
    if draft_read_acceptance_case and stage != stages[143]:
        raise ValueError('Draft read acceptance requires its pinned stage')
    selection_read_revision_case = delivery_case in ('selection-read-revision-red','selection-read-revision-fixed','selection-read-revision-entry')
    if selection_read_revision_case and stage != stages[142]:
        raise ValueError('Selection read revision requires its pinned stage')
    inline_title_acceptance_case = delivery_case in ('inline-title-acceptance','inline-title-focusout-observation','inline-title-transport-observation','inline-title-long-selection')
    if inline_title_acceptance_case and stage != stages[141]:
        raise ValueError('Inline title acceptance requires its pinned stage')
    source_text_preview_case = delivery_case in ('source-text-preview-red','source-text-preview-fixed','source-text-preview-entry')
    if source_text_preview_case and stage != stages[140]:
        raise ValueError('Source text preview requires its pinned stage')
    inline_title_ime_case = delivery_case in ('inline-title-ime-red','inline-title-ime-fixed','inline-title-ime-entry')
    if inline_title_ime_case and stage != stages[139]:
        raise ValueError('Inline title IME requires its pinned stage')
    auto_eligibility_case = delivery_case in ('auto-eligibility-acceptance','auto-eligibility-manual')
    if auto_eligibility_case and stage != stages[138]:
        raise ValueError('Auto eligibility requires its pinned stage')
    auto_ancestor_materials_case = delivery_case in ('auto-ancestor-materials','auto-ancestor-materials-entry','auto-ancestor-clang-proof')
    if auto_ancestor_materials_case and stage != stages[137]:
        raise ValueError('Auto ancestor materials requires its pinned stage')
    auto_routing_acceptance_case = delivery_case == 'auto-routing-acceptance'
    if auto_routing_acceptance_case and stage != stages[136]:
        raise ValueError('Auto routing acceptance requires its pinned stage')
    auto_composer_text_case = delivery_case in ('auto-composer-text','auto-composer-text-entry')
    if auto_composer_text_case and stage != stages[135]:
        raise ValueError('Auto composer text requires its pinned stage')
    composer_catalog_case = delivery_case in ('composer-catalog-acceptance','composer-catalog-acceptance-entry')
    if composer_catalog_case and stage != stages[134]:
        raise ValueError('Composer catalog acceptance requires its pinned stage')
    footer_returned_case = delivery_case in ('footer-returned-model','footer-returned-model-entry')
    if footer_returned_case and stage != stages[133]:
        raise ValueError('Footer returned model requires its pinned stage')
    model_selection_case = delivery_case in ('model-selection-acceptance','model-selection-secrets')
    if model_selection_case and stage != stages[132]:
        raise ValueError('Model selection acceptance requires its pinned stage')
    backup_metadata_case = delivery_case in ('backup-call-metadata','backup-call-metadata-entry')
    if backup_metadata_case and stage != stages[131]:
        raise ValueError('Backup call metadata requires its pinned stage')
    call_route_case = delivery_case in ('call-route-snapshot','call-route-snapshot-entry')
    if call_route_case and stage != stages[130]:
        raise ValueError('Call route snapshot requires its pinned stage')
    usage_identity_case = delivery_case in ('usage-model-identity','usage-model-identity-entry')
    if usage_identity_case and stage != stages[129]:
        raise ValueError('Usage model identity requires its pinned stage')
    qwen_case = delivery_case in ['qwen-text', 'qwen-material', 'qwen-entry']
    continuation_case = delivery_case == 'continuation-budget'
    cache_case = delivery_case == 'cache-usage'
    relay_case = delivery_case == 'relay-responses-input'
    aligned_case = delivery_case in ['aligned-install', 'aligned-upgrade', 'aligned-entry']
    model_late_case = delivery_case == 'new-model-late-error'
    relay_terminal_case = delivery_case == 'relay-search-terminal'
    search_package_case = delivery_case in ['search-package-install','search-package-upgrade','search-package-entry']
    service_identity_case = delivery_case == 'service-identity'
    cancel_measurement_case = delivery_case == 'cancel-measurement'
    retrieval_stop_case = delivery_case == 'retrieval-stop'
    cancel_package_case = delivery_case in ['cancel-package-install','cancel-package-upgrade','cancel-package-entry']
    node_regression_case = delivery_case == 'node-regression'
    image_navigation_case = delivery_case == 'image-navigation'
    media_close_case = delivery_case in ['media-video-close', 'media-reader-close']
    media_package_case = delivery_case in ['media-package-install','media-package-upgrade','media-package-entry']
    audio_exit_case = delivery_case == 'audio-window-exit'
    evidence_qualifier_case = delivery_case in ['evidence-partial','evidence-unqueried']
    evidence_package_case = delivery_case in ['evidence-package-install','evidence-package-upgrade','evidence-package-entry']
    evidence_contradiction_case = delivery_case == 'evidence-contradiction'
    returned_model_tail_case = delivery_case in ['returned-model-blank-tail','returned-model-null-only','returned-model-blank-error']
    model_keyboard_focus_case = delivery_case in ['model-keyboard-focus','model-keyboard-node']
    model_tab_navigation_case = delivery_case == 'model-tab-navigation'
    model_navigation_owner_case = delivery_case in ['model-navigation-input','model-navigation-provider','model-navigation-regression','model-navigation-node']
    docx_count_package_case = delivery_case in ['docx-count-package-install','docx-count-package-upgrade','docx-count-package-entry']
    if docx_count_package_case and stage != stages[43]:
        raise ValueError('DOCX/count delivery requires its pinned stage')
    stream_bom_case = delivery_case in ['stream-leading-bom','stream-bom-entry']
    if stream_bom_case and stage != stages[44]:
        raise ValueError('Stream BOM requires its pinned stage')
    stream_format_case = delivery_case in ['stream-format-measurement','stream-format-entry']
    if stream_format_case and stage != stages[45]:
        raise ValueError('Stream failure measurement requires its pinned stage')
    stream_unknown_case = delivery_case in ['stream-unknown-measurement','stream-unknown-entry']
    if stream_unknown_case and stage != stages[46]:
        raise ValueError('Unknown measurement requires its pinned stage')
    history_accounting_case = delivery_case in ['history-accounting','history-accounting-entry']
    if history_accounting_case and stage != stages[47]:
        raise ValueError('History accounting requires its pinned stage')
    title_unicode_case = delivery_case in ['title-unicode','title-unicode-entry']
    if title_unicode_case and stage != stages[48]:
        raise ValueError('Unicode title requires its pinned stage')
    entry_route_case = delivery_case in ['entry-route','entry-route-entry']
    if entry_route_case and stage != stages[49]:
        raise ValueError('Entry ownership requires its pinned stage')
    history_backup_case = delivery_case == 'history-receipt-backup'
    if history_backup_case and stage != stages[50]:
        raise ValueError('History receipt backup requires its pinned stage')
    stream_lines_case = delivery_case in ['stream-line-endings','stream-line-endings-entry']
    if stream_lines_case and stage != stages[51]:
        raise ValueError('Stream line endings require their pinned stage')
    xlsx_phonetic_case = delivery_case in ['xlsx-phonetic-values','xlsx-phonetic-values-entry']
    if xlsx_phonetic_case and stage != stages[52]:
        raise ValueError('XLSX phonetic values require their pinned stage')
    docx_hyphen_case = delivery_case in ['docx-run-hyphens','docx-run-hyphens-entry']
    if docx_hyphen_case and stage != stages[53]:
        raise ValueError('DOCX run hyphens require their pinned stage')
    qwen_image_case = delivery_case in ['qwen-image-capability','qwen-image-capability-entry']
    if qwen_image_case and stage != stages[54]:
        raise ValueError('Qwen image capability requires its pinned stage')
    responses_pdf_case = delivery_case in ['responses-pdf-input','responses-pdf-input-entry']
    if responses_pdf_case and stage != stages[55]:
        raise ValueError('Responses PDF requires its pinned stage')
    pptx_order_case = delivery_case in ['pptx-slide-order','pptx-slide-order-entry']
    if pptx_order_case and stage != stages[56]:
        raise ValueError('PPTX slide order requires its pinned stage')
    xlsx_order_case = delivery_case in ['xlsx-workbook-order','xlsx-workbook-order-entry']
    if xlsx_order_case and stage != stages[57]:
        raise ValueError('XLSX workbook order requires its pinned stage')
    pptx_entity_case = delivery_case in ['pptx-attribute-entities','pptx-attribute-entities-entry']
    if pptx_entity_case and stage != stages[58]:
        raise ValueError('PPTX attribute entities require their pinned stage')
    auto_image_case = delivery_case in ['auto-verified-image','auto-verified-image-entry']
    if auto_image_case and stage != stages[59]:
        raise ValueError('Auto image eligibility requires its pinned stage')
    scope_deadline_case = delivery_case in ['scope-deadline-measurement','scope-deadline-measurement-entry']
    if scope_deadline_case and stage != stages[60]:
        raise ValueError('Scope deadline measurement requires its pinned stage')
    failure_identity_case = delivery_case in ['failure-model-identity','failure-model-identity-entry']
    if failure_identity_case and stage != stages[61]:
        raise ValueError('Scope deadline measurement requires its pinned stage')
    live_checkpoint_case = delivery_case in ['live-call-checkpoint','live-call-checkpoint-entry']
    if live_checkpoint_case and stage != stages[62]:
        raise ValueError('Live call checkpoint requires its pinned stage')
    scoped_answer_case = delivery_case in ['scoped-answer-checkpoint','scoped-answer-checkpoint-entry']
    if scoped_answer_case and stage != stages[63]:
        raise ValueError('Scoped answer requires its pinned stage')
    docx_section_case = delivery_case in ['docx-section-parts','docx-section-parts-entry']
    if docx_section_case and stage != stages[64]:
        raise ValueError('DOCX section parts require their pinned stage')
    docx_notes_case = delivery_case in ['docx-note-references','docx-note-references-entry']
    if docx_notes_case and stage != stages[65]:
        raise ValueError('DOCX notes require their pinned stage')
    docx_revisions_case = delivery_case in ['docx-current-revisions','docx-current-revisions-entry','retrieval-live-progress','retrieval-live-progress-entry']
    if docx_revisions_case and stage != stages[67 if delivery_case in ['retrieval-live-progress','retrieval-live-progress-entry'] else 66]:
        raise ValueError('DOCX revisions require their pinned stage')
    retrieval_group_case = delivery_case in ['retrieval-group-summary','retrieval-group-summary-entry','retrieval-group-acceptance']
    if retrieval_group_case and stage != stages[122 if delivery_case == 'retrieval-group-acceptance' else 68]:
        raise ValueError('Retrieval group summary requires its pinned stage')
    retrieval_outcomes_case = delivery_case in ['retrieval-call-outcomes','retrieval-call-outcomes-entry','retrieval-call-outcomes-stop','retrieval-call-outcomes-failed','retrieval-call-outcomes-unknown']
    if retrieval_outcomes_case and stage != stages[69]:
        raise ValueError('Retrieval call outcomes require their pinned stage')
    mixed_material_case = delivery_case in ['mixed-material-send','mixed-material-reject']
    if mixed_material_case and stage != stages[70]:
        raise ValueError('Mixed material requires its pinned stage')
    material_read_case = delivery_case in ['material-read-role','material-read-role-entry','material-read-interrupted','material-read-legacy']
    if material_read_case and stage != stages[71]:
        raise ValueError('Material read role requires its pinned stage')
    material_source_case = delivery_case in ['material-source-order','material-source-entry','material-source-interrupted']
    if material_source_case and stage != stages[72]:
        raise ValueError('Original material positions require their pinned stage')
    material_name_case = delivery_case in ['material-name-metadata','material-name-entry']
    if material_name_case and stage != stages[73]:
        raise ValueError('Material metadata names require their pinned stage')
    material_lifecycle_case = delivery_case == 'material-name-lifecycle'
    if material_lifecycle_case and stage != stages[74]:
        raise ValueError('Material name lifecycle requires its pinned stage')
    sync_material_case = delivery_case in ['sync-material-blocks','sync-material-entry']
    if sync_material_case and stage != stages[75]:
        raise ValueError('Sync material block integrity requires its pinned stage')
    ordinary_scope_case = delivery_case in ['ordinary-stop-workspace','ordinary-stop-entry']
    if ordinary_scope_case and stage != stages[76]:
        raise ValueError('Ordinary Stop source identity requires its pinned stage')
    runtime_owner_case = delivery_case in ['runtime-owner-inspect','runtime-owner','runtime-owner-entry']
    if runtime_owner_case and stage != stages[77]:
        raise ValueError('Runtime refresh ownership requires its pinned stage')
    island_source_case = delivery_case in ['island-source-actions','island-source-entry']
    if island_source_case and stage != stages[78]:
        raise ValueError('Island source actions require pinned stage')
    batch_recycle_case = delivery_case in ['batch-recycle-retry','batch-recycle-entry']
    if batch_recycle_case and stage != stages[79]:
        raise ValueError('Batch recycle retry requires pinned stage')
    batch_close_case = delivery_case in ['batch-recycle-close','batch-close-entry','batch-close-read-failure']
    if batch_close_case and stage != stages[80]:
        raise ValueError('Batch recycle close requires pinned stage')
    island_cadence_case = delivery_case in ['island-cadence','island-cadence-entry']
    if island_cadence_case and stage != stages[81]:
        raise ValueError('Island cadence requires pinned stage')
    companion_failures_case = delivery_case == 'companion-failures'
    if companion_failures_case and stage != stages[82]:
        raise ValueError('Companion failures require pinned stage')
    companion_feedback_case = delivery_case in ['companion-feedback','companion-feedback-entry']
    if companion_feedback_case and stage != stages[83]:
        raise ValueError('Companion feedback requires pinned stage')
    companion_disclosure_case = delivery_case in ['companion-disclosure','companion-disclosure-entry']
    if companion_disclosure_case and stage != stages[84]:
        raise ValueError('Companion disclosure requires pinned stage')
    companion_prerequisite_case = delivery_case in ['companion-prerequisite','companion-prerequisite-entry']
    if companion_prerequisite_case and stage != stages[85]:
        raise ValueError('Companion prerequisite requires pinned stage')
    companion_material_case = delivery_case == 'companion-material'
    if companion_material_case and stage != stages[86]:
        raise ValueError('Companion material requires pinned stage')
    companion_native_material_case = delivery_case == 'companion-native-material'
    if companion_native_material_case and stage != stages[87]:
        raise ValueError('Companion native material requires pinned stage')
    native_material_interrupt_case = delivery_case == 'native-material-interrupt'
    if native_material_interrupt_case and stage != stages[88]:
        raise ValueError('Native material interrupt requires pinned stage')
    native_material_write_case = delivery_case == 'native-material-write'
    if native_material_write_case and stage != stages[89]:
        raise ValueError('Native material write requires pinned stage')
    continuation_stream_echo_case = delivery_case in ('continuation-stream-echo','continuation-stream-echo-entry')
    if continuation_stream_echo_case and stage != stages[120]:
        raise ValueError('Continuation stream echo requires pinned stage')
    ocr_probe_selection_case = delivery_case in ('ocr-probe-selection','ocr-probe-selection-entry','credential-save-target','credential-save-target-entry','credential-delete-target','credential-delete-target-entry')
    if ocr_probe_selection_case and stage != stages[126 if delivery_case.startswith('credential-delete-target') else 125 if delivery_case.startswith('credential-save-target') else 119]:
        raise ValueError('OCR probe selection requires pinned stage')
    xlsx_text_namespaces_case = delivery_case in ('xlsx-text-namespaces','xlsx-text-namespaces-entry','xlsx-xstring','xlsx-xstring-entry')
    if xlsx_text_namespaces_case and stage != stages[121 if delivery_case.startswith('xlsx-xstring') else 118]:
        raise ValueError('XLSX text namespaces require pinned stage')
    pptx_text_namespaces_case = delivery_case in ('pptx-text-namespaces','pptx-text-namespaces-entry')
    if pptx_text_namespaces_case and stage != stages[117]:
        raise ValueError('PPTX text namespaces require pinned stage')
    sse_multiline_case = delivery_case in ('sse-multiline','sse-multiline-entry')
    if sse_multiline_case and stage != stages[116]:
        raise ValueError('SSE multiline requires pinned stage')
    new_chat_send_choice_case = delivery_case in ('new-chat-send-choice','new-chat-send-choice-entry','new-chat-choice-acceptance')
    if new_chat_send_choice_case and stage != stages[123 if delivery_case == 'new-chat-choice-acceptance' else 115]:
        raise ValueError('New chat Send choice requires pinned stage')
    new_chat_model_owner_case = delivery_case in ('new-chat-model-owner','new-chat-model-owner-entry')
    if new_chat_model_owner_case and stage != stages[114]:
        raise ValueError('New chat model owner requires pinned stage')
    docx_paragraph_marks_case = delivery_case in ('docx-paragraph-marks','docx-paragraph-marks-entry')
    if docx_paragraph_marks_case and stage != stages[113]:
        raise ValueError('DOCX paragraph marks require pinned stage')
    relaycore_keys_case = delivery_case == 'relaycore-keys'
    if relaycore_keys_case and stage != stages[112]:
        raise ValueError('RelayCore keys require pinned stage')
    list_blocks_case = delivery_case in ('list-blocks','list-blocks-entry')
    if list_blocks_case and stage != stages[111]:
        raise ValueError('List blocks require pinned stage')
    unsaved_key_acceptance_case = delivery_case in ('unsaved-key-acceptance','unsaved-key-acceptance-entry','unsaved-key-missing')
    if unsaved_key_acceptance_case and stage != stages[110]:
        raise ValueError('Unsaved key acceptance requires pinned stage')
    reminder_draft_progress_case = delivery_case in ('reminder-draft-progress','reminder-draft-progress-entry')
    if reminder_draft_progress_case and stage != stages[109]:
        raise ValueError('Reminder draft progress requires pinned stage')
    reminder_progress_case = delivery_case in ('reminder-progress','reminder-progress-entry')
    if reminder_progress_case and stage != stages[108]:
        raise ValueError('Reminder progress requires pinned stage')
    list_continuation_case = delivery_case in ('list-continuation','list-continuation-entry')
    if list_continuation_case and stage != stages[107]:
        raise ValueError('List continuation requires pinned stage')
    docx_table_revision_case = delivery_case in ('docx-table-revisions','docx-table-revisions-entry')
    if docx_table_revision_case and stage != stages[106]:
        raise ValueError('DOCX table revisions require pinned stage')
    probe_response_case = delivery_case in ('probe-response','probe-response-entry')
    if probe_response_case and stage != stages[105]:
        raise SystemExit('Probe response stage mismatch')
    model_probe_owner_case = delivery_case in ('model-probe-owner','model-probe-owner-entry')
    if model_probe_owner_case and stage != stages[104]:
        raise ValueError('Model probe owner requires its pinned stage')
    multiline_code_case = delivery_case in ('multiline-code','multiline-code-entry')
    if multiline_code_case and stage != stages[103]:
        raise ValueError('Multiline code requires its pinned stage')
    inline_code_case = delivery_case in ('inline-code','inline-code-entry')
    if inline_code_case and stage != stages[102]:
        raise ValueError('Inline code requires its pinned stage')
    markdown_fences_case = delivery_case in ('markdown-fences','markdown-fences-entry')
    if markdown_fences_case and stage != stages[101]:
        raise ValueError('Markdown fences require their pinned stage')
    entry_visibility_case = delivery_case in ('entry-visibility','entry-visibility-entry')
    if entry_visibility_case and stage != stages[100]:
        raise ValueError('Entry visibility requires its pinned stage')
    xlsx_empty_strings_case = delivery_case in ('xlsx-empty-strings','xlsx-empty-strings-entry')
    if xlsx_empty_strings_case and stage != stages[99]:
        raise ValueError('XLSX empty string cases require their pinned stage')
    office_alternatives_case = delivery_case in ('office-alternatives','office-alternatives-entry')
    if office_alternatives_case and stage != stages[98]:
        raise ValueError('Office alternatives require their pinned stage')
    docx_alternatives_case = delivery_case in ('docx-alternatives','docx-alternatives-entry')
    if docx_alternatives_case and stage != stages[97]:
        raise ValueError('DOCX alternatives require their pinned stage')
    sparse_usage_cost_case = delivery_case in ('sparse-usage-cost','sparse-usage-cost-entry','sparse-usage-sync')
    if sparse_usage_cost_case and stage != stages[96]:
        raise ValueError('Sparse usage cost case requires its own evidence stage')
    selection_intent_case = delivery_case in ('selection-intent','selection-intent-entry')
    if selection_intent_case and stage != stages[95]:
        raise ValueError('Selection intent stage mismatch')
    media_open_order_case = delivery_case in ('media-open-order','media-open-order-entry')
    if media_open_order_case and stage != stages[94]:
        raise ValueError('Media open order stage mismatch')
    title_conflict_recovery_case = delivery_case in ('title-conflict-recovery','title-conflict-recovery-entry','title-write-recovery')
    if title_conflict_recovery_case and stage != stages[124 if delivery_case == 'title-write-recovery' else 93]:
        raise ValueError('Title conflict recovery stage mismatch')
    json_messages_refusal_case = delivery_case in ('json-messages-refusal','json-messages-refusal-entry')
    if json_messages_refusal_case and stage != stages[92]:
        raise ValueError('JSON Messages refusal case stage mismatch')
    provider_refusal_case = delivery_case in ('provider-refusal', 'provider-refusal-entry')
    if provider_refusal_case and stage != stages[91]:
        raise ValueError('Provider refusal case stage mismatch')
    qwen_deepseek_material_case = delivery_case == 'qwen-deepseek-native-material'
    if qwen_deepseek_material_case and stage != stages[90]:
        raise ValueError('Qwen DeepSeek native material requires pinned stage')
    owned_link_entry = delivery_case == 'owned-linker-entry'
    if owned_link_entry and stage != stages[42]:
        raise ValueError('Owned linker entry requires its pinned stage')
    retrieval_count_case = delivery_case in ['retrieval-count-red','retrieval-count-final','retrieval-count-entry']
    if retrieval_count_case and stage != stages[41]:
        raise ValueError('Retrieval accounting requires its pinned stage')
    docx_expanded_case = delivery_case in ['docx-expanded-breaks','docx-development-entry']
    if docx_expanded_case and stage != stages[40]:
        raise ValueError('DOCX separators require their pinned stage')
    title_package_case = delivery_case in ['title-package-install','title-package-upgrade','title-package-entry']
    if title_package_case and stage != stages[39]:
        raise ValueError('Title package requires its pinned stage')
    toolchain_recheck_case = delivery_case in ['title-build-recheck','title-build-entry']
    if toolchain_recheck_case and stage != stages[38]:
        raise ValueError('Observed build recheck requires its pinned stage')
    title_double_open_case = delivery_case in ['title-double-open','title-development-entry']
    if title_double_open_case and stage != stages[37]:
        raise ValueError('Title opening requires its pinned stage')
    pptx_package_case = delivery_case in ['pptx-package-install','pptx-package-upgrade','pptx-package-entry','pptx-install-recovery']
    if pptx_package_case and stage != stages[36]:
        raise ValueError('PPTX package requires its pinned stage')
    pptx_soft_break_case = delivery_case in ['pptx-soft-breaks','pptx-development-entry']
    if pptx_soft_break_case and stage != stages[35]:
        raise ValueError('PPTX soft breaks require their pinned stage')
    xlsx_package_case = delivery_case in ['xlsx-package-install','xlsx-package-upgrade','xlsx-package-entry']
    if xlsx_package_case and stage != stages[34]:
        raise ValueError('XLSX package requires its pinned stage')
    xlsx_sparse_case = delivery_case in ['xlsx-sparse-columns','xlsx-development-entry']
    if xlsx_sparse_case and stage != stages[33]:
        raise ValueError('XLSX sparse columns require their pinned stage')
    cancel_backup_case = delivery_case == 'cancel-backup-receipts'
    if cancel_backup_case and stage != stages[32]:
        raise ValueError('Cancelled receipt restore requires its pinned stage')
    backup_call_case = delivery_case in ['backup-call-window','backup-call-partial']
    if backup_call_case and stage != stages[31]:
        raise ValueError('Backup call facts require their pinned stage')
    window_package_case = delivery_case in ['window-package-install','window-package-upgrade','window-package-entry']
    if window_package_case and stage != stages[30]:
        raise ValueError('Window package requires its pinned stage')
    call_window_case = delivery_case == 'call-window-native'
    if call_window_case and stage != stages[29]:
        raise ValueError('Call window requires its pinned stage')
    cross_area_exit_case = delivery_case in ['cross-area-reopen','cross-area-drain']
    if cross_area_exit_case and stage != stages[28]:
        raise ValueError('Cross-area exit requires its pinned stage')
    sync_partial_usage_case = delivery_case in ['sync-partial-usage','sync-partial-release']
    if sync_partial_usage_case and stage != stages[27]:
        raise ValueError('Partial usage sync requires its pinned stage')
    model_package_case = delivery_case in ['model-package-install','model-package-upgrade','model-package-entry']
    if evidence_contradiction_case and stage != stages[21]:
        raise ValueError('Evidence contradiction requires its pinned stage')
    if returned_model_tail_case and stage != stages[22]:
        raise ValueError('Returned model cases require their pinned stage')
    if model_keyboard_focus_case and stage != stages[23]:
        raise ValueError('Model keyboard focus requires its pinned stage')
    if model_tab_navigation_case and stage != stages[24]:
        raise ValueError('Model Tab navigation requires its pinned stage')
    if model_navigation_owner_case and stage != stages[25]:
        raise ValueError('Model navigation owner requires its pinned stage')
    if model_package_case and stage != stages[26]:
        raise ValueError('Model package delivery requires its pinned stage')
    if qwen_case and stage != stages[2]:
        raise ValueError('Qwen cases require the pinned Qwen alignment stage')
    if continuation_case and stage != stages[3]:
        raise ValueError('Continuation budget case requires its pinned stage')
    if cache_case and stage != stages[4]:
        raise ValueError('Cache usage case requires its pinned stage')
    if relay_case and stage != stages[5]:
        raise ValueError('Relay input case requires its pinned stage')
    if aligned_case and stage != stages[6]:
        raise ValueError('Aligned package cases require their pinned stage')
    if model_late_case and stage != stages[7]:
        raise ValueError('New model late error case requires its pinned stage')
    if relay_terminal_case and stage != stages[8]:
        raise ValueError('Relay search terminal requires its pinned stage')
    if search_package_case and stage != stages[9]:
        raise ValueError('Search package cases require their pinned stage')
    if service_identity_case and stage != stages[10]:
        raise ValueError('Service identity case requires its pinned stage')
    if cancel_measurement_case and stage != stages[11]:
        raise ValueError('Cancellation measurement requires its pinned stage')
    if retrieval_stop_case and stage != stages[12]:
        raise ValueError('Retrieval stop requires its pinned stage')
    if cancel_package_case and stage != stages[13]:
        raise ValueError('Cancel package cases require their pinned stage')
    if node_regression_case and stage != stages[14]:
        raise ValueError('Node regression requires its pinned stage')
    if image_navigation_case and stage != stages[15]:
        raise ValueError('Image navigation requires its pinned stage')
    if media_close_case and stage != stages[16]:
        raise ValueError('Media close cases require their pinned stage')
    if media_package_case and stage != stages[17]:
        raise ValueError('Media package cases require their pinned stage')
    if audio_exit_case and stage != stages[18]:
        raise ValueError('Audio exit case requires its pinned stage')
    if evidence_qualifier_case and stage != stages[19]:
        raise ValueError('Evidence qualifier cases require their pinned stage')
    if evidence_package_case and stage != stages[20]:
        raise ValueError('Evidence/exit package cases require their pinned stage')
    if delivery_case and not (camera_focus_case or camera_ready_case or camera_fixture_case or chat_shell_font_case or island_stop_queue_case or island_worker_exit_case or archive_font_case or native_picker_args_case or find_entities_case or occurrence_case or native_picker_case or find_ime_case or modal_ime_case or real_deadline_case or exit_stall_case or switch_keyboard_case or disclosure_keyboard_case or settings_keyboard_case or settings_pages_case or import_failure_case or import_results_case or e5dc_delivery_case or data_font_case or current_delivery_case or unicode_backup_case or composer_font_case or bdd_delivery_case or footer_overflow_case or cloud_delivery_case or public_cloud_case or install_roundtrip_case or release_entry_case or menu_font_case or popup_input_case or shell_class_case or current_mixed_case or attachment_draft_case or unsupported_tool_case or portable_current_case or messages_usage_case or stop_attempt_case or supported_material_case or unsupported_media_case or resource_build_case or about_feedback_case or appearance_feedback_case or product_scope_minimum_case or reply_emphasis_minimum_case or video_resume_minimum_case or reader_minimum_case or cloud_unlink_case or island_lifecycle_case or island_control_case or continuity_minimum_case or continuation_minimum_case or preview_deadline_case or reopen_protocol_case or terminal_backup_case or room_facts_startup_case or portable_identity_case or model_identity_startup_case or companion_current_case or native_text_current_case or search_exceptions_case or relay_native_current_case or model_window_tab_case or legacy_service_current_case or portable_cost_case or paragraph_sources_case or plain_sources_case or plain_material_case or full_delete_search_case or checkpoint_backup_case or sync_search_case or orphan_checkpoint_case or cache_question_case or stop_late_evidence_case or answer_context_feedback_case or chinese_evidence_case or material_scope_limit_case or single_topic_case or terminal_facts_case or cost_source_provenance_case or cost_currency_projection_case or call_registration_gate_case or failure_route_identity_case or structured_source_matrix_case or stream_terminal_matrix_case or responses_terminal_case or registry_session_case or model_list_viewport_case or search_tool_status_case or registry_mapping_case or retired_catalog_case or ocr_chat_isolation_case or source_annotation_case or model_feedback_acceptance_case or draft_read_acceptance_case or selection_read_revision_case or inline_title_acceptance_case or source_text_preview_case or inline_title_ime_case or auto_eligibility_case or auto_ancestor_materials_case or auto_routing_acceptance_case or auto_composer_text_case or composer_catalog_case or footer_returned_case or model_selection_case or backup_metadata_case or call_route_case or usage_identity_case or idle_generation_case or entry_retention_case or continuation_stream_echo_case or ocr_probe_selection_case or xlsx_text_namespaces_case or pptx_text_namespaces_case or sse_multiline_case or new_chat_send_choice_case or new_chat_model_owner_case or docx_paragraph_marks_case or relaycore_keys_case or list_blocks_case or unsaved_key_acceptance_case or reminder_draft_progress_case or reminder_progress_case or list_continuation_case or docx_table_revision_case or probe_response_case or model_probe_owner_case or multiline_code_case or inline_code_case or markdown_fences_case or entry_visibility_case or xlsx_empty_strings_case or office_alternatives_case or docx_alternatives_case or sparse_usage_cost_case or selection_intent_case or media_open_order_case or title_conflict_recovery_case or json_messages_refusal_case or provider_refusal_case or qwen_deepseek_material_case or native_material_write_case or native_material_interrupt_case or companion_native_material_case or companion_material_case or companion_prerequisite_case or companion_disclosure_case or companion_feedback_case or companion_failures_case or island_cadence_case or batch_close_case or batch_recycle_case or island_source_case or runtime_owner_case or ordinary_scope_case or sync_material_case or material_lifecycle_case or material_name_case or material_source_case or material_read_case or mixed_material_case or retrieval_outcomes_case or retrieval_group_case or qwen_case or continuation_case or cache_case or relay_case or aligned_case or model_late_case or relay_terminal_case or search_package_case or service_identity_case or cancel_measurement_case or retrieval_stop_case or cancel_package_case or node_regression_case or image_navigation_case or media_close_case or media_package_case or audio_exit_case or evidence_qualifier_case or evidence_package_case or evidence_contradiction_case or returned_model_tail_case or model_keyboard_focus_case or model_tab_navigation_case or model_navigation_owner_case or model_package_case or sync_partial_usage_case or cross_area_exit_case or call_window_case or window_package_case or backup_call_case or cancel_backup_case or xlsx_sparse_case or xlsx_package_case or pptx_soft_break_case or pptx_package_case or title_double_open_case or toolchain_recheck_case or title_package_case or docx_expanded_case or retrieval_count_case or owned_link_entry or docx_count_package_case or stream_bom_case or stream_format_case or stream_unknown_case or history_accounting_case or title_unicode_case or entry_route_case or history_backup_case or stream_lines_case or xlsx_phonetic_case or docx_hyphen_case or qwen_image_case or responses_pdf_case or pptx_order_case or xlsx_order_case or pptx_entity_case or auto_image_case or scope_deadline_case or failure_identity_case or live_checkpoint_case or scoped_answer_case or docx_section_case or docx_notes_case or docx_revisions_case) and stage != stages[1]:
        raise ValueError('Delivery cases require the fixed protected release stage')
    evidence.mkdir()
    candidate = json.loads((desktop / ('output/windows-acceptance/current.json' if delivery_case in ('portable-current-debug','cloud-unlink-current-ui','cloud-unlink-current-race','portable-identity-debug','portable-cost-debug','checkpoint-backup-debug-rollback','sync-search-debug','sync-partial-usage','sparse-usage-sync','terminal-facts-debug') else 'output/windows-acceptance-release/current.json')).read_text('utf8'))
    if portable_current_case:
        expected_sha = json.loads((stage / ('debug-native-candidate.json' if delivery_case == 'portable-current-debug' else 'release-native-candidate.json')).read_text('utf8'))['sha256']
    elif camera_focus_case or camera_ready_case or camera_fixture_case or chat_shell_font_case or island_stop_queue_case or island_worker_exit_case or archive_font_case or native_picker_args_case or find_entities_case or occurrence_case or native_picker_case or find_ime_case or modal_ime_case or real_deadline_case or exit_stall_case or switch_keyboard_case or disclosure_keyboard_case or settings_keyboard_case or settings_pages_case or import_failure_case or import_results_case or e5dc_delivery_case or data_font_case or current_delivery_case or unicode_backup_case or composer_font_case or bdd_delivery_case or footer_overflow_case or install_roundtrip_case or public_cloud_case or install_roundtrip_case or release_entry_case or menu_font_case or popup_input_case or shell_class_case or current_mixed_case or attachment_draft_case or unsupported_tool_case or messages_usage_case or stop_attempt_case or supported_material_case or unsupported_media_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif resource_build_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif about_feedback_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif appearance_feedback_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif product_scope_minimum_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif reply_emphasis_minimum_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif video_resume_minimum_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif reader_minimum_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif cloud_unlink_case:
        expected_sha = json.loads((stage / 'debug-native-candidate.json').read_text('utf8'))['sha256']
    elif island_lifecycle_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif island_control_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif continuity_minimum_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif continuation_minimum_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif preview_deadline_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif reopen_protocol_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif terminal_backup_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif room_facts_startup_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif portable_identity_case:
        expected_sha = json.loads((stage / ('debug-native-candidate.json' if delivery_case == 'portable-identity-debug' else 'release-native-candidate.json')).read_text('utf8'))['sha256']
    elif model_identity_startup_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif companion_current_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif native_text_current_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif search_exceptions_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif relay_native_current_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif model_window_tab_case:
        expected_sha = json.loads((stage / ('previous-native-candidate.json' if delivery_case == 'model-window-tab-red' else 'release-native-candidate.json')).read_text('utf8'))['sha256']
    elif legacy_service_current_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif portable_cost_case:
        expected_sha = json.loads((stage / ('debug-native-candidate.json' if delivery_case == 'portable-cost-debug' else 'release-native-candidate.json')).read_text('utf8'))['sha256']
    elif paragraph_sources_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif plain_sources_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif plain_material_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif full_delete_search_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif checkpoint_backup_case:
        expected_sha = json.loads((stage / ('debug-native-candidate.json' if delivery_case == 'checkpoint-backup-debug-rollback' else 'release-native-candidate.json')).read_text('utf8'))['sha256']
    elif sync_search_case:
        expected_sha = json.loads((stage / ('debug-native-candidate.json' if delivery_case == 'sync-search-debug' else 'release-native-candidate.json')).read_text('utf8'))['sha256']
    elif orphan_checkpoint_case:
        expected_sha = json.loads((stage / ('previous-native-candidate.json' if delivery_case == 'orphan-checkpoint-baseline' else 'release-native-candidate.json')).read_text('utf8'))['sha256']
    elif cache_question_case:
        expected_sha = json.loads((stage / ('previous-native-candidate.json' if delivery_case in ('cache-question-red','cache-question-red-resume') else 'release-native-candidate.json')).read_text('utf8'))['sha256']
    elif stop_late_evidence_case:
        expected_sha = json.loads((stage / 'release-native-candidate.json').read_text('utf8'))['sha256']
    elif answer_context_feedback_case:
        expected_sha = json.loads((stage / ('previous-native-candidate.json' if delivery_case in ('answer-context-red','answer-context-old-readback') else 'release-native-candidate.json')).read_text('utf8'))['sha256']
    elif chinese_evidence_case:
        expected_sha = json.loads((stage / ('previous-native-candidate.json' if delivery_case == 'chinese-evidence-red' else 'release-native-candidate.json')).read_text('utf8'))['sha256']
    else:
        expected_sha = json.loads((stage / ('previous-native-candidate.json' if delivery_case in ('material-limit-red','material-limit-limited','material-limit-failure','material-limit-empty-red') else 'release-native-candidate.json' if delivery_case in ('material-limit-fixed','material-limit-entry','material-limit-empty-fixed','material-limit-zero-fixed','material-limit-failure-current','material-limit-empty-current') else 'debug-native-candidate.json' if delivery_case == 'terminal-facts-debug' else 'release-native-candidate.json' if delivery_case in ('terminal-facts-release','terminal-facts-entry','single-topic-facts','single-topic-entry','single-topic-resume','single-topic-mixed') else 'release-candidate.json' if delivery_case == 'sync-partial-release' else 'debug-candidate.json' if delivery_case == 'sparse-usage-sync' else 'native-candidate.json')).read_text('utf8'))['sha256'] if stage in stages[2:] else '91103C297CF37FD86353CB34D8E00059961932C792ED1B229FE2FE9DC8E0547D'
    if candidate['sha256'] != expected_sha:
        raise ValueError('Explicitly reviewed focus-protected candidate required')
    entry = stage / 'private-probe-entry.ps1'
    assert entry.is_file()
    kernel = C.WinDLL('kernel32', use_last_error=True)
    user = C.WinDLL('user32', use_last_error=True)
    signatures = {
        'CreateJobObjectW': ([C.c_void_p, W.LPCWSTR], W.HANDLE),
        'SetInformationJobObject': ([W.HANDLE, C.c_int, C.c_void_p, W.DWORD], W.BOOL),
        'QueryInformationJobObject': ([W.HANDLE, C.c_int, C.c_void_p, W.DWORD, C.c_void_p], W.BOOL),
        'AssignProcessToJobObject': ([W.HANDLE, W.HANDLE], W.BOOL),
        'CreateProcessW': ([W.LPCWSTR, W.LPWSTR, C.c_void_p, C.c_void_p, W.BOOL, W.DWORD, C.c_void_p, W.LPCWSTR, C.POINTER(bounded.Startup), C.POINTER(bounded.Process)], W.BOOL),
        'ResumeThread': ([W.HANDLE], W.DWORD),
        'WaitForSingleObject': ([W.HANDLE, W.DWORD], W.DWORD),
        'GetExitCodeProcess': ([W.HANDLE, C.POINTER(W.DWORD)], W.BOOL),
        'TerminateProcess': ([W.HANDLE, W.UINT], W.BOOL),
        'TerminateJobObject': ([W.HANDLE, W.UINT], W.BOOL),
        'CloseHandle': ([W.HANDLE], W.BOOL),
        'GlobalMemoryStatusEx': ([C.POINTER(bounded.MemoryStatus)], W.BOOL),
    }
    for name, (args, ret) in signatures.items():
        fn = getattr(kernel, name); fn.argtypes = args; fn.restype = ret
    user.CreateDesktopW.argtypes = [W.LPCWSTR, C.c_void_p, C.c_void_p, W.DWORD, W.DWORD, C.c_void_p]
    user.CreateDesktopW.restype = W.HANDLE
    user.CloseDesktop.argtypes = [W.HANDLE]; user.CloseDesktop.restype = W.BOOL

    def checked(value):
        if not value:
            raise C.WinError(C.get_last_error())
        return value

    memory = bounded.MemoryStatus(); memory.length = C.sizeof(memory)
    checked(kernel.GlobalMemoryStatusEx(C.byref(memory)))
    if memory.free_commit < 12 * 1024**3:
        raise RuntimeError('Insufficient system commit headroom')
    name = 'NanfengAIProbe_' + uuid.uuid4().hex
    receipt = {'desktopName': name, 'neverSwitched': True, 'selfTest': self_test,
               'userDesktopAcceptance': False, 'island': island, 'deliveryCase': delivery_case, 'exitCode': None}
    desk = job = None
    process = bounded.Process()
    try:
        desk = checked(user.CreateDesktopW(name, None, None, 0, 0x10000000, None))
        job = checked(kernel.CreateJobObjectW(None, 'NanfengAIPrivate-' + uuid.uuid4().hex))
        limits = bounded.Limits(); limits.basic.flags = 0x2000 | 0x100 | 0x200
        limits.process_limit = 3072 * 1024**2; limits.job_limit = 6144 * 1024**2
        checked(kernel.SetInformationJobObject(job, 9, C.byref(limits), C.sizeof(limits)))
        actual = bounded.Limits()
        checked(kernel.QueryInformationJobObject(job, 9, C.byref(actual), C.sizeof(actual), None))
        assert actual.basic.flags == limits.basic.flags and actual.process_limit == limits.process_limit and actual.job_limit == limits.job_limit
        pwsh = shutil.which('pwsh'); assert pwsh
        command = [pwsh, '-NoProfile', '-NonInteractive', '-File', str(entry), '-ExpectedDesktop', name,
                   '-EvidenceDirectory', str(evidence), '-ExpectedSha256', candidate['sha256']]
        if self_test:
            command.append('-SelfTest')
        if island:
            command.append('-Island')
        if delivery_case:
            command += ['-Case', delivery_case]
        startup = bounded.Startup(); startup.cb = C.sizeof(startup); startup.desktop = 'WinSta0\\' + name
        checked(kernel.CreateProcessW(pwsh, C.create_unicode_buffer(subprocess.list2cmdline(command)), None, None,
                                      False, 0x08000000 | 0x4, None, str(repo), C.byref(startup), C.byref(process)))
        checked(kernel.AssignProcessToJobObject(job, process.process))
        receipt.update(pid=process.pid, jobAssignedBeforeResume=True, jobLimitsReadBackExact=True)
        (evidence / 'startup-receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf8')
        if kernel.ResumeThread(process.thread) == 0xFFFFFFFF:
            raise C.WinError(C.get_last_error())
        deadline = time.monotonic() + (1980 if real_deadline_case else 180)
        while kernel.WaitForSingleObject(process.process, 200) == 258:
            if time.monotonic() >= deadline:
                raise TimeoutError('Private focus probe timed out')
        exit_code = W.DWORD(); checked(kernel.GetExitCodeProcess(process.process, C.byref(exit_code)))
        receipt['exitCode'] = exit_code.value
    except Exception as error:
        receipt['failure'] = str(error)
        raise
    finally:
        if job:
            account = bounded.Accounting()
            checked(kernel.QueryInformationJobObject(job, 1, C.byref(account), C.sizeof(account), None))
            receipt['activeBeforeJobCleanup'] = account.active
            checked(kernel.TerminateJobObject(job, 1))
            for _ in range(50):
                checked(kernel.QueryInformationJobObject(job, 1, C.byref(account), C.sizeof(account), None))
                if not account.active:
                    break
                time.sleep(.1)
            receipt['ownedProcessesRemaining'] = account.active
            kernel.CloseHandle(job)
        if process.process:
            if kernel.WaitForSingleObject(process.process, 0) == 258:
                kernel.TerminateProcess(process.process, 1)
            kernel.CloseHandle(process.process)
        if process.thread:
            kernel.CloseHandle(process.thread)
        if desk:
            receipt['desktopHandleClosed'] = bool(user.CloseDesktop(desk))
        receipt['controllerExitCode'] = 0 if (receipt['exitCode'] == 0 and
            receipt.get('activeBeforeJobCleanup') == 0 and receipt.get('ownedProcessesRemaining') == 0 and
            receipt.get('desktopHandleClosed')) else 1
        (evidence / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf8')
    print(json.dumps(receipt))
    return receipt['controllerExitCode']


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True, type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--self-test', action='store_true')
    mode.add_argument('--island', action='store_true')
    mode.add_argument('--delivery-case', '--case', dest='delivery_case', choices=['camera-focus','camera-ready','camera-fixture-preflight','camera-fixture-current','chat-font-red','chat-font-projected','chat-font-current','island-queue-current','island-worker-current','archive-font-red','archive-font-current','native-picker-args-current','native-picker-args-remainder','native-picker-args-save-cold','find-entities-red','find-entities-current','find-occurrence-red','find-occurrence-current','native-picker-current','find-ime-red','find-ime-current','find-ime-navigation','find-ime-navigation-remainder','modal-ime-red','modal-ime-current','modal-ime-cold','exit-real-deadline','exit-stall-preflight','settings-switch-red','settings-switch-current','action-menu-red','action-menu-current','settings-picker-red','settings-picker-current','settings-pages-current','settings-pages-projected','import-failure-current','import-results-current','e5dc-delivery-install','e5dc-delivery-recovery','e5dc-delivery-default','data-font-current','data-font-baseline','current-delivery-install','current-delivery-recovery','current-delivery-default','unicode-backup-current','composer-font-current','composer-font-projected','bdd-install-current','bdd-recovery-current','bdd-default-current','footer-overflow-recovery','footer-overflow-projected','footer-overflow-current','public-cloud-install-current','public-cloud-recovery-current','public-cloud-default-current','public-cloud-current','install-default-current','install-roundtrip-current','install-roundtrip-recovery','release-entry-current','menu-font-current','popup-input-current','shell-class-reentry','current-mixed-materials','attachment-draft-current','portable-current-debug','portable-current-release','unsupported-tool-current', 'messages-usage-current','stop-attempt-current','supported-material-current','unsupported-media-current','resource-build-current','about-feedback-current','appearance-feedback-current','product-scope-minimum-current','reply-emphasis-minimum-current','video-resume-minimum-current','reader-minimum-current','cloud-unlink-current-ui','cloud-unlink-current-race','island-lifecycle-current','island-control-peer-red','island-control-peer-current','island-control-sources-current','continuity-minimum-work','continuity-minimum-unavailable','continuity-minimum-crash','continuation-minimum-complete','continuation-minimum-empty','continuation-minimum-interrupted','continuation-minimum-reverify','continuation-minimum-budget','preview-deadline-current','preview-deadline-stop','reopen-protocol-current','reopen-protocol-cross','terminal-backup-current','room-facts-startup','portable-identity-debug','portable-identity-release','model-identity-startup','companion-current','native-text-current','search-exceptions-current','relay-native-current','model-window-tab-red','model-window-tab-fixed','legacy-service-current','portable-cost-debug','portable-cost-release','paragraph-source-boundaries','plain-material-sources','plain-material-facts','full-delete-search-lifecycle-fresh','full-delete-search-lifecycle','checkpoint-backup-fixed','checkpoint-backup-debug-rollback','checkpoint-backup-final','checkpoint-backup','sync-search-debug','sync-search-release','orphan-checkpoint-baseline','orphan-checkpoint-fixed','cache-question-live-keyed','cache-question-static-keyed','cache-question-red','cache-question-red-resume','cache-question-live','cache-question-static','stop-late-evidence','answer-context-red','answer-context-fixed','answer-context-old-readback','chinese-evidence-red','chinese-evidence-fixed','chinese-evidence-readback','install', 'upgrade', 'default-entry', 'qwen-text', 'qwen-material', 'qwen-entry', 'continuation-budget', 'cache-usage', 'relay-responses-input', 'aligned-install', 'aligned-upgrade', 'aligned-entry', 'new-model-late-error', 'relay-search-terminal', 'search-package-install', 'search-package-upgrade', 'search-package-entry', 'service-identity', 'cancel-measurement', 'retrieval-stop', 'cancel-package-install', 'cancel-package-upgrade', 'cancel-package-entry', 'node-regression', 'image-navigation', 'media-video-close', 'media-reader-close', 'media-package-install', 'media-package-upgrade', 'media-package-entry', 'audio-window-exit', 'evidence-partial', 'evidence-unqueried', 'evidence-package-install', 'evidence-package-upgrade', 'evidence-package-entry', 'evidence-contradiction', 'returned-model-blank-tail','returned-model-null-only','returned-model-blank-error', 'model-keyboard-focus','model-keyboard-node','model-tab-navigation','model-navigation-input','model-navigation-provider','model-navigation-regression','model-navigation-node','model-package-install','model-package-upgrade','model-package-entry','sync-partial-usage','sync-partial-release','cross-area-reopen','cross-area-drain','call-window-native','window-package-install','window-package-upgrade','window-package-entry','backup-call-window','backup-call-partial','cancel-backup-receipts','xlsx-sparse-columns','xlsx-development-entry','xlsx-package-install','xlsx-package-upgrade','xlsx-package-entry','pptx-soft-breaks','pptx-development-entry','pptx-package-install','pptx-package-upgrade','pptx-package-entry','pptx-install-recovery','title-double-open','title-development-entry','title-build-recheck','title-build-entry','title-package-install','title-package-upgrade','title-package-entry','docx-expanded-breaks','docx-development-entry','retrieval-count-red','retrieval-count-final','retrieval-count-entry','owned-linker-entry','docx-count-package-install','docx-count-package-upgrade','docx-count-package-entry','stream-leading-bom','stream-bom-entry','stream-format-measurement','stream-format-entry','stream-unknown-measurement','stream-unknown-entry','history-accounting','history-accounting-entry','title-unicode','title-unicode-entry','entry-route','entry-route-entry','history-receipt-backup','stream-line-endings','stream-line-endings-entry','xlsx-phonetic-values','xlsx-phonetic-values-entry','docx-run-hyphens','docx-run-hyphens-entry','qwen-image-capability','qwen-image-capability-entry','responses-pdf-input','responses-pdf-input-entry','pptx-slide-order','pptx-slide-order-entry','xlsx-workbook-order','xlsx-workbook-order-entry','pptx-attribute-entities','pptx-attribute-entities-entry','auto-verified-image','auto-verified-image-entry','scope-deadline-measurement','scope-deadline-measurement-entry','failure-model-identity','failure-model-identity-entry','live-call-checkpoint','live-call-checkpoint-entry','scoped-answer-checkpoint','scoped-answer-checkpoint-entry','docx-section-parts','docx-section-parts-entry','docx-note-references','docx-note-references-entry','docx-current-revisions','docx-current-revisions-entry','retrieval-live-progress','retrieval-live-progress-entry','retrieval-group-summary','retrieval-group-summary-entry','retrieval-call-outcomes','retrieval-call-outcomes-entry','retrieval-call-outcomes-stop','retrieval-call-outcomes-failed','retrieval-call-outcomes-unknown','mixed-material-send','mixed-material-reject','material-read-role','material-read-role-entry','material-read-interrupted','material-read-legacy','material-source-order','material-source-entry','material-source-interrupted','material-name-metadata','material-name-entry','material-name-lifecycle','sync-material-blocks','sync-material-entry','ordinary-stop-workspace','ordinary-stop-entry','runtime-owner-inspect','runtime-owner','runtime-owner-entry','island-source-actions','island-source-entry','batch-recycle-retry','batch-recycle-entry','batch-recycle-close','batch-close-entry','batch-close-read-failure','island-cadence','island-cadence-entry','companion-failures','companion-feedback','companion-feedback-entry','companion-disclosure','companion-disclosure-entry','companion-prerequisite','companion-prerequisite-entry','companion-material','companion-native-material','native-material-interrupt','native-material-write','qwen-deepseek-native-material','provider-refusal','provider-refusal-entry','json-messages-refusal','json-messages-refusal-entry','title-conflict-recovery','title-conflict-recovery-entry','media-open-order','media-open-order-entry','selection-intent','selection-intent-entry','sparse-usage-cost','sparse-usage-cost-entry','sparse-usage-sync','docx-alternatives','docx-alternatives-entry','office-alternatives','office-alternatives-entry','xlsx-empty-strings','xlsx-empty-strings-entry','entry-visibility','entry-visibility-entry','markdown-fences','markdown-fences-entry','inline-code','inline-code-entry','multiline-code','multiline-code-entry','model-probe-owner','model-probe-owner-entry','probe-response','probe-response-entry','docx-table-revisions','docx-table-revisions-entry','list-continuation','list-continuation-entry','reminder-progress','reminder-progress-entry','reminder-draft-progress','reminder-draft-progress-entry','unsaved-key-acceptance','unsaved-key-acceptance-entry','unsaved-key-missing','list-blocks','list-blocks-entry','relaycore-keys','docx-paragraph-marks','docx-paragraph-marks-entry','new-chat-model-owner','new-chat-model-owner-entry','new-chat-send-choice','new-chat-send-choice-entry','sse-multiline','sse-multiline-entry','pptx-text-namespaces','pptx-text-namespaces-entry','xlsx-text-namespaces','xlsx-text-namespaces-entry','ocr-probe-selection','ocr-probe-selection-entry','continuation-stream-echo','continuation-stream-echo-entry','xlsx-xstring','xlsx-xstring-entry','retrieval-group-acceptance','new-chat-choice-acceptance','title-write-recovery','credential-save-target','credential-save-target-entry','credential-delete-target','credential-delete-target-entry','entry-retention-acceptance','entry-retention-acceptance-entry','idle-generation-acceptance','usage-model-identity','usage-model-identity-entry','call-route-snapshot','call-route-snapshot-entry','backup-call-metadata','backup-call-metadata-entry','model-selection-acceptance','model-selection-secrets','footer-returned-model','footer-returned-model-entry','composer-catalog-acceptance','composer-catalog-acceptance-entry','auto-composer-text','auto-composer-text-entry','auto-routing-acceptance','auto-ancestor-materials','auto-ancestor-materials-entry','auto-ancestor-clang-proof','auto-eligibility-acceptance','auto-eligibility-manual','inline-title-ime-red','inline-title-ime-fixed','inline-title-ime-entry','source-text-preview-red','source-text-preview-fixed','source-text-preview-entry','inline-title-acceptance','inline-title-focusout-observation','inline-title-transport-observation','inline-title-long-selection','selection-read-revision-red','selection-read-revision-fixed','selection-read-revision-entry','draft-read-acceptance','model-feedback-acceptance','model-feedback-entry','source-annotation-red','source-annotation-fixed','source-annotation-entry','ocr-chat-isolation','ocr-chat-isolation-entry','retired-catalog','retired-catalog-entry','registry-mapping-red','registry-mapping-fixed','registry-mapping-entry','registry-mapping-public-refresh','registry-mapping-public-cold','search-tool-status-red','search-tool-status-fixed','search-tool-status-entry','model-list-viewport-inspect','model-list-viewport-fixed','model-list-viewport-entry','registry-session','registry-bundled','responses-terminal-red','responses-terminal-fixed','responses-terminal-entry','stream-terminal-matrix','stream-terminal-display','structured-source-matrix', 'failure-route-identity','failure-route-identity-resume','call-registration-gate','cost-currency-projection','cost-currency-resume','cost-currency-inspect','cost-currency-fixed','cost-currency-entry','cost-source-red','cost-source-fixed','cost-source-entry','terminal-facts-debug','terminal-facts-release','terminal-facts-entry','single-topic-facts','single-topic-entry','single-topic-resume','single-topic-mixed','material-limit-red','material-limit-fixed','material-limit-entry','material-limit-limited','material-limit-failure','material-limit-empty-red','material-limit-empty-fixed','material-limit-zero-fixed','material-limit-failure-current','material-limit-empty-current'])
    args = parser.parse_args()
    raise SystemExit(run(args.output, args.self_test, args.island, args.delivery_case))
