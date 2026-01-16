package com.mazzlabs.sentinel.tools.modules

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.tools.framework.ErrorCode
import com.mazzlabs.sentinel.tools.framework.ParameterType
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClockModuleTest {

    private val clockModule = ClockModule()

    @Test
    fun `moduleId is clock`() {
        assertThat(clockModule.moduleId).isEqualTo("clock")
    }

    @Test
    fun `description is meaningful`() {
        assertThat(clockModule.description).contains("alarm")
        assertThat(clockModule.description).contains("timer")
    }

    @Test
    fun `requiredPermissions is empty`() {
        assertThat(clockModule.requiredPermissions).isEmpty()
    }

    @Test
    fun `operations contains create_alarm`() {
        val opIds = clockModule.operations.map { it.operationId }
        assertThat(opIds).contains("create_alarm")
    }

    @Test
    fun `operations contains create_timer`() {
        val opIds = clockModule.operations.map { it.operationId }
        assertThat(opIds).contains("create_timer")
    }

    @Test
    fun `operations contains show_alarms`() {
        val opIds = clockModule.operations.map { it.operationId }
        assertThat(opIds).contains("show_alarms")
    }

    @Test
    fun `operations contains show_timers`() {
        val opIds = clockModule.operations.map { it.operationId }
        assertThat(opIds).contains("show_timers")
    }

    @Test
    fun `operations contains dismiss_alarm`() {
        val opIds = clockModule.operations.map { it.operationId }
        assertThat(opIds).contains("dismiss_alarm")
    }

    @Test
    fun `create_alarm operation has required parameters`() {
        val createAlarmOp = clockModule.operations.find { it.operationId == "create_alarm" }
        assertThat(createAlarmOp).isNotNull()

        val paramNames = createAlarmOp!!.parameters.map { it.name }
        assertThat(paramNames).contains("hour")
        assertThat(paramNames).contains("minute")

        val hourParam = createAlarmOp.parameters.find { it.name == "hour" }
        assertThat(hourParam?.required).isTrue()
        assertThat(hourParam?.type).isEqualTo(ParameterType.INTEGER)
    }

    @Test
    fun `create_alarm operation has optional parameters`() {
        val createAlarmOp = clockModule.operations.find { it.operationId == "create_alarm" }!!

        val labelParam = createAlarmOp.parameters.find { it.name == "label" }
        assertThat(labelParam?.required).isFalse()

        val daysParam = createAlarmOp.parameters.find { it.name == "days" }
        assertThat(daysParam?.required).isFalse()
    }

    @Test
    fun `execute returns error for unknown operation`() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val result = clockModule.execute("unknown_operation", emptyMap(), mockContext)

        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `create_alarm has examples`() {
        val createAlarmOp = clockModule.operations.find { it.operationId == "create_alarm" }
        assertThat(createAlarmOp?.examples).isNotEmpty()
    }

    @Test
    fun `create_timer has examples`() {
        val createTimerOp = clockModule.operations.find { it.operationId == "create_timer" }
        assertThat(createTimerOp?.examples).isNotEmpty()
    }
}
