package com.nanzhufeng.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ManageProjectUseCase
import com.nanzhufeng.ai.domain.Project
import com.nanzhufeng.ai.domain.ProjectId
import com.nanzhufeng.ai.domain.ProjectIntent
import com.nanzhufeng.ai.domain.ProjectIntentAction
import com.nanzhufeng.ai.domain.ProjectIntentId
import com.nanzhufeng.ai.domain.ProjectListScope
import com.nanzhufeng.ai.domain.ProjectMutationResult
import com.nanzhufeng.ai.domain.ProjectRepository
import com.nanzhufeng.ai.domain.ProjectSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProjectUiState(
    val projects: List<ProjectSnapshot> = emptyList(),
    val activeProjects: List<ProjectSnapshot> = emptyList(),
    val scope: ProjectListScope = ProjectListScope.ACTIVE,
    val dialogVisible: Boolean = false,
    val createDialogVisible: Boolean = false,
    val selectedProjectId: ProjectId? = null,
    val notice: String? = null,
)

/** P4-A UI adapter. It only calls Project Domain use cases; it never builds a prompt. */
class ProjectViewModel(private val repository: ProjectRepository, private val manage: ManageProjectUseCase) : ViewModel() {
    var state by mutableStateOf(ProjectUiState())
        private set
    init { reload() }
    fun showDialog(projectId: ProjectId? = null) { state = state.copy(dialogVisible = true, selectedProjectId = projectId ?: state.selectedProjectId); reload() }
    fun showCreateDialog() { state = state.copy(dialogVisible = true, createDialogVisible = true); reload() }
    fun dismissDialog() { state = state.copy(dialogVisible = false, createDialogVisible = false) }
    fun dismissCreateDialog() { state = state.copy(createDialogVisible = false) }
    fun setScope(scope: ProjectListScope) { state = state.copy(scope = scope); reload() }
    fun select(id: ProjectId) { state = state.copy(selectedProjectId = id) }
    fun reload(notice: String? = null) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.list(state.scope) to repository.list(ProjectListScope.ACTIVE) }
            val projects = loaded.first
            val selected = state.selectedProjectId?.takeIf { id -> projects.any { it.project.id == id } } ?: projects.firstOrNull()?.project?.id
            state = state.copy(projects = projects, activeProjects = loaded.second, selectedProjectId = selected, notice = notice ?: state.notice)
        }
    }
    fun create(title: String, description: String = "") = execute(ProjectIntent(ProjectIntentId.new(), ProjectIntentAction.CREATE, ProjectId.new(), title = title, description = description), "项目已在本机创建。")
    fun rename(projectId: ProjectId, title: String, description: String) = execute(ProjectIntent(ProjectIntentId.new(), ProjectIntentAction.UPDATE_METADATA, projectId, title, description), "项目资料已更新。")
    fun pin(projectId: ProjectId, pinned: Boolean) = execute(ProjectIntent(ProjectIntentId.new(), if (pinned) ProjectIntentAction.PIN else ProjectIntentAction.UNPIN, projectId), if (pinned) "项目已置顶。" else "项目已取消置顶。")
    fun archive(projectId: ProjectId, archived: Boolean) = execute(ProjectIntent(ProjectIntentId.new(), if (archived) ProjectIntentAction.ARCHIVE else ProjectIntentAction.RESTORE, projectId), if (archived) "项目已归档；会话和知识资产未改变。" else "项目已恢复。")
    fun updateInstruction(projectId: ProjectId, content: String) = execute(ProjectIntent(ProjectIntentId.new(), ProjectIntentAction.UPDATE_INSTRUCTION, projectId, instruction = content), "项目指令已生成新的本地修订。")
    fun assignConversation(projectId: ProjectId, conversationId: ConversationId, assigned: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { manage.assignConversation(ProjectIntent(ProjectIntentId.new(), if (assigned) ProjectIntentAction.ASSIGN_CONVERSATION else ProjectIntentAction.REMOVE_CONVERSATION, projectId, conversationId = conversationId)) }
            state = state.copy(notice = when (result) { is ProjectMutationResult.Applied -> if (assigned) "会话已归入项目；消息、调用和附件未改变。" else "会话已移出项目；原有资产未改变。"; is ProjectMutationResult.Replayed -> "已回读相同项目操作。"; is ProjectMutationResult.Rejected -> result.reason })
            reload()
        }
    }
    fun projectForConversation(conversationId: ConversationId): ProjectId? = repository.projectForConversation(conversationId)
    private fun execute(intent: ProjectIntent, applied: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { manage.execute(intent) }
            state = state.copy(notice = when (result) { is ProjectMutationResult.Applied -> applied; is ProjectMutationResult.Replayed -> "已回读相同项目操作。"; is ProjectMutationResult.Rejected -> result.reason })
            reload()
        }
    }
    class Factory(private val repository: ProjectRepository, private val manage: ManageProjectUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ProjectViewModel(repository, manage) as T
    }
}
