const aggregate = (id, count, byteCount) => ({ id, count, byteCount });

export function createC14LocalDataPreview() {
  return {
    privacyInventory: {
      totalBytes: 31_744,
      aggregates: [
        aggregate('search_text', 3, 1_536),
        aggregate('search_attachments', 2, 12_288),
        aggregate('memory', 2, 1_024),
        aggregate('knowledge', 1, 2_048),
        aggregate('projects', 1, 512),
        aggregate('attachment_images', 1, 8_192),
        aggregate('attachment_files', 1, 4_096),
        aggregate('import_source_assets', 1, 2_048),
        aggregate('chatgpt_json_imported_conversations', 2, 0),
        aggregate('claude_json_imported_conversations', 1, 0),
        aggregate('zip_imported_conversations', 2, 0),
        aggregate('chatgpt_json_import_batches', 1, 0),
        aggregate('claude_json_import_batches', 1, 0),
        aggregate('zip_import_batches', 1, 0),
        aggregate('zip_imported_attachments', 2, 12_288),
        aggregate('zip_imported_profile_fields', 4, 0),
        aggregate('glm_ocr_tasks', 1, 0),
        aggregate('glm_ocr_attachments', 2, 6_144),
      ],
    },
    chatgptTask: {
      id: 'c14-chatgpt-json',
      provider: 'CHATGPT',
      status: 'COMPLETED',
      importedCount: 2,
      failedCount: 0,
      skippedCount: 1,
    },
    claudeTask: {
      id: 'c14-claude-json',
      provider: 'CLAUDE',
      status: 'FAILED',
      importedCount: 1,
      failedCount: 1,
      skippedCount: 0,
    },
    p6kTask: {
      id: 'c14-chatgpt-zip',
      provider: 'ChatGPT',
      status: 'COMPLETED',
      importedCount: 2,
      failedCount: 0,
      skippedCount: 1,
      restoredAssetCount: 2,
      missingSourceCount: 0,
      unmappedAssetCount: 0,
    },
  };
}
