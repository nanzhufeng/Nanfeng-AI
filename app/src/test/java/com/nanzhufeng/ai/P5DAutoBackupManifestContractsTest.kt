package com.nanzhufeng.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class P5DAutoBackupManifestContractsTest {
    @Test fun `android system backup and device transfer are fail closed`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val extraction = File("src/main/res/xml/data_extraction_rules.xml").readText()
        val legacy = File("src/main/res/xml/backup_rules.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        listOf("cloud-backup", "device-transfer", "domain=\"database\" path=\".\"", "domain=\"file\" path=\".\"", "domain=\"sharedpref\" path=\".\"").forEach { assertTrue(extraction.contains(it)) }
        assertTrue(legacy.contains("<exclude domain=\"root\" path=\".\""))
    }
}
