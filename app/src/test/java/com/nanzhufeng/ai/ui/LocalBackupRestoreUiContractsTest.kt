package com.nanzhufeng.ai.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBackupRestoreUiContractsTest {
    @Test fun `backup and restore expose progress independently while both remain locked`() {
        val export = LocalBackupUiState(workingOperation = LocalBackupOperation.EXPORT)
        assertTrue(export.working)
        assertTrue(export.workingOperation == LocalBackupOperation.EXPORT)
        assertFalse(export.workingOperation == LocalBackupOperation.RESTORE)

        val restore = LocalBackupUiState(workingOperation = LocalBackupOperation.RESTORE)
        assertTrue(restore.working)
        assertTrue(restore.workingOperation == LocalBackupOperation.RESTORE)
        assertFalse(restore.workingOperation == LocalBackupOperation.EXPORT)
    }

    @Test fun `backup status is anchored to the operation that produced it`() {
        val backup = LocalBackupUiState(statusOperation = LocalBackupOperation.EXPORT, notice = "备份完成")
        val restore = LocalBackupUiState(statusOperation = LocalBackupOperation.RESTORE, notice = "恢复完成")
        assertTrue(backup.statusOperation == LocalBackupOperation.EXPORT)
        assertTrue(restore.statusOperation == LocalBackupOperation.RESTORE)
    }
}
