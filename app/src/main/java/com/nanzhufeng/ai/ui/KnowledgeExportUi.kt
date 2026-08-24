package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.ExportKnowledgePackageUseCase
import com.nanzhufeng.ai.domain.KnowledgeExportFailure
import com.nanzhufeng.ai.domain.KnowledgeExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KnowledgeExportUiState(
    val dialogVisible: Boolean = false,
    val isWorking: Boolean = false,
    val result: KnowledgeExportResult? = null,
)

class KnowledgeExportViewModel(
    private val exportKnowledgePackage: ExportKnowledgePackageUseCase,
) : ViewModel() {
    var state by mutableStateOf(KnowledgeExportUiState())
        private set

    fun showDialog() {
        state = state.copy(dialogVisible = true, isWorking = true)
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { exportKnowledgePackage.latestVerified() }
            state = KnowledgeExportUiState(dialogVisible = true, result = latest)
        }
    }

    fun export() {
        if (state.isWorking) return
        state = state.copy(isWorking = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { exportKnowledgePackage.execute() }
            state = state.copy(isWorking = false, result = result)
        }
    }

    fun dismissDialog() {
        if (!state.isWorking) state = state.copy(dialogVisible = false)
    }

    class Factory(private val exportKnowledgePackage: ExportKnowledgePackageUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(KnowledgeExportViewModel::class.java))
            return KnowledgeExportViewModel(exportKnowledgePackage) as T
        }
    }
}

@Composable
fun KnowledgeExportCard(onOpen: () -> Unit) {
    WhiteCard {
        Text("本地导出", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("仅导出已确认知识及安全溯源；不含候选、账本敏感字段、密钥、原图或附件正文。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
        ) {
            androidx.compose.material3.Icon(Icons.Outlined.FileDownload, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("导出本地知识包")
        }
    }
}

@Composable
fun KnowledgeExportDialog(
    state: KnowledgeExportUiState,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = { Text("本地知识导出", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("导出为版本化 .nfai 包，保存到应用私有目录。不会上传、分享或访问系统目录。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                if (state.isWorking) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("正在写入并回读校验…", color = SecondaryText)
                    }
                } else {
                    ExportResultContent(state.result)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isWorking) { Text("关闭") } },
        confirmButton = {
            Button(onClick = onExport, enabled = !state.isWorking) { Text("创建导出文件") }
        },
    )
}

@Composable
private fun ExportResultContent(result: KnowledgeExportResult?) {
    when (result) {
        null -> Text("尚未创建导出文件。", color = SecondaryText)
        KnowledgeExportResult.EmptyKnowledge -> Text("当前没有已确认保存的知识，因此没有创建文件。候选、草稿和调用记录不会被导出。", color = SecondaryText)
        KnowledgeExportResult.Cancelled -> Text("导出已取消，未留下成功文件。", color = SecondaryText)
        is KnowledgeExportResult.Failed -> Text("导出失败：${result.reason.toChineseLabel()}。未将失败文件标记为成功。", color = ErrorRed)
        is KnowledgeExportResult.Success -> {
            val export = result.export
            Text("已完成同文件回读与 SHA-256 校验。", color = BrandGreen, fontWeight = FontWeight.Medium)
            Text("文件：${export.fileName}", style = MaterialTheme.typography.bodySmall)
            Text("位置：${export.relativeLocation}", style = MaterialTheme.typography.bodySmall)
            Text("大小：${formatExportBytes(export.byteCount)}", style = MaterialTheme.typography.bodySmall)
            Text("SHA-256：${export.sha256}", style = MaterialTheme.typography.bodySmall)
            Text("内容：${export.manifest.knowledgeCount} 条已确认知识；附件仅保留 MIME、大小与哈希引用。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun KnowledgeExportFailure.toChineseLabel(): String = when (this) {
    KnowledgeExportFailure.OUTPUT_UNAVAILABLE -> "应用私有导出目录不可用"
    KnowledgeExportFailure.WRITE_FAILED -> "文件写入失败"
    KnowledgeExportFailure.READ_BACK_FAILED -> "文件回读失败"
    KnowledgeExportFailure.INTEGRITY_MISMATCH -> "文件完整性校验不一致"
    KnowledgeExportFailure.INVALID_PACKAGE -> "导出包格式无效"
}

private fun formatExportBytes(byteCount: Long): String = when {
    byteCount < 1024 -> "$byteCount B"
    byteCount < 1024 * 1024 -> "%.1f KB".format(byteCount / 1024.0)
    else -> "%.1f MB".format(byteCount / (1024.0 * 1024.0))
}
