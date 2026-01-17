package com.mazzlabs.sentinel.tools.framework

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolModuleTest {

    @Test
    fun `ParameterType enum has expected values`() {
        val types = ParameterType.values()
        assertThat(types).hasLength(10)
        assertThat(types).asList().containsExactly(
            ParameterType.STRING,
            ParameterType.INTEGER,
            ParameterType.FLOAT,
            ParameterType.BOOLEAN,
            ParameterType.DATE,
            ParameterType.TIME,
            ParameterType.DATETIME,
            ParameterType.DURATION,
            ParameterType.ARRAY,
            ParameterType.OBJECT
        )
    }

    @Test
    fun `ErrorCode enum has expected values`() {
        val codes = ErrorCode.values()
        assertThat(codes).asList().containsExactly(
            ErrorCode.INVALID_PARAMS,
            ErrorCode.NOT_FOUND,
            ErrorCode.PERMISSION_DENIED,
            ErrorCode.NOT_AVAILABLE,
            ErrorCode.SYSTEM_ERROR,
            ErrorCode.TIMEOUT,
            ErrorCode.CANCELLED
        )
    }

    @Test
    fun `ToolParameter creation with required field`() {
        val param = ToolParameter(
            name = "test",
            type = ParameterType.STRING,
            description = "A test parameter",
            required = true
        )
        
        assertThat(param.name).isEqualTo("test")
        assertThat(param.type).isEqualTo(ParameterType.STRING)
        assertThat(param.description).isEqualTo("A test parameter")
        assertThat(param.required).isTrue()
        assertThat(param.enumValues).isNull()
        assertThat(param.default).isNull()
    }

    @Test
    fun `ToolParameter creation with optional field and default`() {
        val param = ToolParameter(
            name = "count",
            type = ParameterType.INTEGER,
            description = "Number of items",
            required = false,
            default = 10
        )
        
        assertThat(param.required).isFalse()
        assertThat(param.default).isEqualTo(10)
    }

    @Test
    fun `ToolParameter creation with enum values`() {
        val param = ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "Action type",
            required = true,
            enumValues = listOf("start", "stop", "restart")
        )
        
        assertThat(param.enumValues).containsExactly("start", "stop", "restart")
    }

    @Test
    fun `ToolOperation creation`() {
        val params = listOf(
            ToolParameter("input", ParameterType.STRING, "Input value", required = true)
        )
        val examples = listOf(
            ToolExample("Do something", "do_something", mapOf("input" to "test"))
        )
        
        val operation = ToolOperation(
            operationId = "test_op",
            description = "A test operation",
            parameters = params,
            examples = examples
        )
        
        assertThat(operation.operationId).isEqualTo("test_op")
        assertThat(operation.description).isEqualTo("A test operation")
        assertThat(operation.parameters).hasSize(1)
        assertThat(operation.examples).hasSize(1)
    }

    @Test
    fun `ToolExample creation`() {
        val example = ToolExample(
            userQuery = "Set alarm for 6am",
            operationId = "create_alarm",
            params = mapOf("hour" to 6, "minute" to 0)
        )
        
        assertThat(example.userQuery).isEqualTo("Set alarm for 6am")
        assertThat(example.operationId).isEqualTo("create_alarm")
        assertThat(example.params).containsEntry("hour", 6)
        assertThat(example.params).containsEntry("minute", 0)
    }

    @Test
    fun `ToolResponse Success creation`() {
        val response = ToolResponse.Success(
            moduleId = "clock",
            operationId = "create_alarm",
            message = "Alarm created",
            data = mapOf("time" to "06:00")
        )
        
        assertThat(response.moduleId).isEqualTo("clock")
        assertThat(response.operationId).isEqualTo("create_alarm")
        assertThat(response.message).isEqualTo("Alarm created")
        assertThat(response.data).containsEntry("time", "06:00")
    }

    @Test
    fun `ToolResponse Error creation`() {
        val response = ToolResponse.Error(
            moduleId = "calendar",
            operationId = "read_events",
            errorCode = ErrorCode.PERMISSION_DENIED,
            message = "Calendar permission not granted"
        )
        
        assertThat(response.moduleId).isEqualTo("calendar")
        assertThat(response.errorCode).isEqualTo(ErrorCode.PERMISSION_DENIED)
        assertThat(response.message).isEqualTo("Calendar permission not granted")
    }

    @Test
    fun `ToolResponse PermissionRequired creation`() {
        val response = ToolResponse.PermissionRequired(
            moduleId = "contacts",
            operationId = "search",
            permissions = listOf("android.permission.READ_CONTACTS")
        )
        
        assertThat(response.permissions).containsExactly("android.permission.READ_CONTACTS")
    }

    @Test
    fun `ToolResponse Confirmation creation`() {
        val response = ToolResponse.Confirmation(
            moduleId = "messaging",
            operationId = "send_sms",
            message = "Send message to 555-1234?",
            pendingAction = mapOf("phone" to "555-1234", "message" to "Hello")
        )
        
        assertThat(response.message).contains("555-1234")
        assertThat(response.pendingAction).containsEntry("phone", "555-1234")
    }

    @Test
    fun `toSchemaString includes operations and examples`() {
        val module = object : ToolModule {
            override val moduleId: String = "demo"
            override val description: String = "Demo module"
            override val requiredPermissions: List<String> = emptyList()
            override val operations: List<ToolOperation> = listOf(
                ToolOperation(
                    operationId = "run",
                    description = "Run demo",
                    parameters = listOf(
                        ToolParameter("arg", ParameterType.STRING, "Argument", required = true)
                    ),
                    examples = listOf(
                        ToolExample("Run demo", "run", mapOf("arg" to "x"))
                    )
                )
            )

            override suspend fun execute(
                operationId: String,
                params: Map<String, Any?>,
                context: android.content.Context
            ): ToolResponse {
                return ToolResponse.Success(moduleId, operationId, "ok")
            }
        }

        val schema = module.toSchemaString()

        assertThat(schema).contains("## demo")
        assertThat(schema).contains("Run demo")
        assertThat(schema).contains("arg")
        assertThat(schema).contains("Examples")
    }
}
