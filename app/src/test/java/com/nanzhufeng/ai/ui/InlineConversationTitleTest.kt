package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InlineConversationTitleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `double tap opens a rename dialog selects all and Done saves once`() {
        val saved = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    InlineConversationTitle("原来的标题", Modifier.width(240.dp), MaterialTheme.typography.bodyLarge, onRename = { saved.add(it) })
                }
            }
        }
        compose.onNodeWithText("原来的标题").performTouchInput { doubleClick() }
        compose.onNodeWithText("重命名").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText("确认").assertIsDisplayed()
        val editor = compose.onNode(hasSetTextAction())
        editor.assertIsFocused()
        assertEquals(TextRange(0, 5), editor.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        editor.performTextReplacement("新标题")
        editor.performImeAction()
        compose.onNodeWithText("原来的标题").assertExists()
        assertEquals(listOf("新标题"), saved)
    }

    @Test fun `menu rename provides a cancellable input and prevents blank titles`() {
        val saved = mutableListOf<String>()
        var finished = 0
        compose.setContent {
            MaterialTheme {
                InlineConversationTitle("菜单改名", Modifier.width(240.dp), MaterialTheme.typography.bodyLarge,
                    renameRequested = true, onRename = { saved.add(it) }, onRenameFinished = { finished++ })
            }
        }
        val editor = compose.onNode(hasSetTextAction())
        editor.assertIsFocused()
        editor.performTextReplacement("   ")
        compose.onNodeWithText("确认").assertIsNotEnabled()
        editor.performTextReplacement("不应保存的标题")
        compose.onNodeWithText("取消").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.runOnIdle { assertTrue(saved.isEmpty()); assertEquals(1, finished) }
    }

    @Test fun `confirm button saves the edited title once`() {
        val saved = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                InlineConversationTitle("原标题", Modifier.width(240.dp), MaterialTheme.typography.bodyLarge,
                    renameRequested = true, onRename = { saved.add(it) })
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("  已编辑标题  ")
        compose.onNodeWithText("确认").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.runOnIdle { assertEquals(listOf("已编辑标题"), saved) }
    }

    @Test fun `title still opens row on one tap and retains long press actions`() {
        var selected = 0
        var menu = 0
        compose.setContent {
            MaterialTheme {
                InlineConversationTitle("标题", Modifier.width(240.dp), MaterialTheme.typography.bodyLarge,
                    onRename = {}, onClick = { selected++ }, onLongClick = { menu++ })
            }
        }
        compose.onNodeWithText("标题").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1, selected) }
        compose.onNodeWithText("标题").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, menu) }
    }
}
