package com.mazzlabs.sentinel.tools.modules

import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.tools.framework.ParameterType
import org.junit.Test

class CalendarModuleTest {

    private val calendarModule = CalendarModule()

    @Test
    fun `moduleId is calendar`() {
        assertThat(calendarModule.moduleId).isEqualTo("calendar")
    }

    @Test
    fun `description mentions calendar`() {
        assertThat(calendarModule.description).containsMatch("(?i)calendar")
    }

    @Test
    fun `requiredPermissions includes calendar permissions`() {
        assertThat(calendarModule.requiredPermissions).containsAtLeast(
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR"
        )
    }

    @Test
    fun `operations contains expected operations`() {
        val opIds = calendarModule.operations.map { it.operationId }
        assertThat(opIds).containsAtLeast(
            "read_events",
            "create_event",
            "delete_event",
            "get_calendars"
        )
    }

    @Test
    fun `read_events operation has correct parameters`() {
        val readOp = calendarModule.operations.find { it.operationId == "read_events" }!!

        val startDateParam = readOp.parameters.find { it.name == "start_date" }
        assertThat(startDateParam?.type).isEqualTo(ParameterType.DATE)
        assertThat(startDateParam?.required).isTrue()

        val endDateParam = readOp.parameters.find { it.name == "end_date" }
        assertThat(endDateParam?.required).isFalse()
    }

    @Test
    fun `create_event operation has correct parameters`() {
        val createOp = calendarModule.operations.find { it.operationId == "create_event" }!!

        val titleParam = createOp.parameters.find { it.name == "title" }
        assertThat(titleParam?.type).isEqualTo(ParameterType.STRING)
        assertThat(titleParam?.required).isTrue()

        val startParam = createOp.parameters.find { it.name == "start_time" }
        assertThat(startParam?.type).isEqualTo(ParameterType.DATETIME)
        assertThat(startParam?.required).isTrue()
    }
}