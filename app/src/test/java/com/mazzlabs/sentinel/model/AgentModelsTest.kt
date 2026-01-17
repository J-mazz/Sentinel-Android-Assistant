package com.mazzlabs.sentinel.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentModelsTest {

    @Test
    fun `fromJsonOrNull extracts JSON from markdown`() {
        val json = """
            ```json
            {"action":"CLICK","target":"submit"}
            ```
        """.trimIndent()

        val action = AgentAction.fromJsonOrNull(json)

        assertThat(action).isNotNull()
        assertThat(action?.action).isEqualTo(ActionType.CLICK)
        assertThat(action?.target).isEqualTo("submit")
    }

    @Test
    fun `toJson roundtrips`() {
        val action = AgentAction(
            action = ActionType.TYPE,
            text = "hello",
            target = "field"
        )

        val json = action.toJson()
        val parsed = AgentAction.fromJson(json)

        assertThat(parsed.action).isEqualTo(ActionType.TYPE)
        assertThat(parsed.text).isEqualTo("hello")
        assertThat(parsed.target).isEqualTo("field")
    }

    @Test
    fun `none action is safe by default`() {
        val action = AgentAction.none("no-op")

        assertThat(action.action).isEqualTo(ActionType.NONE)
        assertThat(action.reasoning).isEqualTo("no-op")
        assertThat(action.requiresConfirmation()).isFalse()
    }

    @Test
    fun `requiresConfirmation flags sensitive type`() {
        val action = AgentAction(
            action = ActionType.TYPE,
            text = "my password is 1234"
        )

        assertThat(action.requiresConfirmation()).isTrue()
    }

    @Test
    fun `UIElement toString includes attributes`() {
        val element = UIElement(
            type = "Button",
            text = "Save",
            contentDescription = null,
            viewId = null,
            isClickable = true,
            isEditable = false,
            bounds = null
        )

        assertThat(element.toString()).isEqualTo("Button[Save](clickable)")
    }

    @Test
    fun `ScreenContext flatten includes activity and elements`() {
        val context = ScreenContext(
            packageName = "com.example",
            activityName = "MainActivity",
            elements = listOf(
                UIElement("TextField", "Name", null, null, isClickable = false, isEditable = true, bounds = null)
            )
        )

        val flattened = context.flatten()

        assertThat(flattened).contains("App: com.example")
        assertThat(flattened).contains("Activity: MainActivity")
        assertThat(flattened).contains("TextField[Name](editable)")
    }
}