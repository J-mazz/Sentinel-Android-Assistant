package com.mazzlabs.sentinel.tools.framework

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ToolRouterTest {

    private lateinit var mockContext: Context
    private lateinit var router: ToolRouter

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        every { mockContext.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
        router = ToolRouter(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `register adds module to router`() {
        val mockModule = createMockModule("test_module")
        
        router.register(mockModule)
        
        assertThat(router.getModules()).contains(mockModule)
    }

    @Test
    fun `getModules returns all registered modules`() {
        val module1 = createMockModule("module1")
        val module2 = createMockModule("module2")
        
        router.register(module1)
        router.register(module2)
        
        assertThat(router.getModules()).hasSize(2)
        assertThat(router.getModules()).containsExactly(module1, module2)
    }

    @Test
    fun `getAvailableModules filters by permissions`() {
        val grantedModule = createMockModule("granted", permissions = listOf("android.permission.GRANTED"))
        val deniedModule = createMockModule("denied", permissions = listOf("android.permission.DENIED"))
        
        every { mockContext.checkPermission("android.permission.GRANTED", any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { mockContext.checkPermission("android.permission.DENIED", any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        router.register(grantedModule)
        router.register(deniedModule)
        
        val available = router.getAvailableModules()
        
        assertThat(available).contains(grantedModule)
        assertThat(available).doesNotContain(deniedModule)
    }

    @Test
    fun `generateSchema includes module descriptions`() {
        val module = createMockModule(
            "test",
            description = "Test module description",
            operations = listOf(
                ToolOperation(
                    operationId = "test_op",
                    description = "Test operation",
                    parameters = listOf(
                        ToolParameter("param1", ParameterType.STRING, "A parameter", required = true)
                    )
                )
            )
        )
        
        router.register(module)
        
        val schema = router.generateSchema()
        
        assertThat(schema).contains("test")
        assertThat(schema).contains("Test module description")
        assertThat(schema).contains("test_op")
    }

    @Test
    fun `generateCompactSchema produces shorter output`() {
        val module = createMockModule("test", operations = listOf(
            ToolOperation("op1", "Operation 1", listOf(ToolParameter("p", ParameterType.STRING, "param", true))),
            ToolOperation("op2", "Operation 2", emptyList())
        ))
        
        router.register(module)
        
        val compact = router.generateCompactSchema()
        val full = router.generateSchema()
        
        assertThat(compact.length).isLessThan(full.length)
        assertThat(compact).contains("test")
    }

    @Test
    fun `execute routes to correct module`() = runTest {
        val mockModule = createMockModule("clock")
        coEvery { mockModule.execute(any(), any(), any()) } returns ToolResponse.Success(
            moduleId = "clock",
            operationId = "create_alarm",
            message = "Alarm set"
        )
        
        router.register(mockModule)
        
        val result = router.execute("clock.create_alarm", mapOf("hour" to 6))
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        coVerify { mockModule.execute("create_alarm", any(), any()) }
    }

    @Test
    fun `execute returns error for unknown module`() = runTest {
        val result = router.execute("unknown.operation", emptyMap())
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `execute returns permission required when missing`() = runTest {
        val module = createMockModule("secure", permissions = listOf("android.permission.SECURE"))
        every { mockContext.checkPermission("android.permission.SECURE", any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        router.register(module)
        
        val result = router.execute("secure.operation", emptyMap())
        
        assertThat(result).isInstanceOf(ToolResponse.PermissionRequired::class.java)
    }

    @Test
    fun `execute handles operation without module prefix`() = runTest {
        val module = createMockModule("clock", operations = listOf(
            ToolOperation("create_alarm", "Create alarm", emptyList())
        ))
        coEvery { module.execute(any(), any(), any()) } returns ToolResponse.Success(
            "clock", "create_alarm", "Done"
        )
        
        router.register(module)
        
        val result = router.execute("create_alarm", emptyMap())
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
    }

    @Test
    fun `getModulesNeedingPermissions identifies missing permissions`() {
        val module = createMockModule("test", permissions = listOf("android.permission.MISSING"))
        every { mockContext.checkPermission("android.permission.MISSING", any(), any()) } returns PackageManager.PERMISSION_DENIED
        
        router.register(module)
        
        val needingPerms = router.getModulesNeedingPermissions()
        
        assertThat(needingPerms).containsKey(module)
        assertThat(needingPerms[module]).contains("android.permission.MISSING")
    }

    private fun createMockModule(
        id: String,
        description: String = "Mock module",
        permissions: List<String> = emptyList(),
        operations: List<ToolOperation> = listOf(ToolOperation("default_op", "Default", emptyList()))
    ): ToolModule {
        return mockk {
            every { moduleId } returns id
            every { this@mockk.description } returns description
            every { requiredPermissions } returns permissions
            every { this@mockk.operations } returns operations
            every { isAvailable(any()) } returns true
        }
    }
}
