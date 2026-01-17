package com.mazzlabs.sentinel.tools.framework

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToolRouterTest {

    @Test
    fun `execute routes to module by full tool call`() = runTest {
        val context = mockContext()
        val router = ToolRouter(context)
        router.register(FakeModule("alpha", listOf(ToolOperation("ping", "Ping", listOf()))))

        val result = router.execute("alpha.ping", mapOf("x" to 1))

        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
    }

    @Test
    fun `execute routes by operation when unambiguous`() = runTest {
        val context = mockContext()
        val router = ToolRouter(context)
        router.register(FakeModule("alpha", listOf(ToolOperation("ping", "Ping", listOf()))))

        val result = router.execute("ping", emptyMap())

        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
    }

    @Test
    fun `execute returns error on invalid tool call`() = runTest {
        val context = mockContext()
        val router = ToolRouter(context)

        val result = router.execute("bogus", emptyMap())

        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        val error = result as ToolResponse.Error
        assertThat(error.errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
    }

    @Test
    fun `generateSchema lists unavailable tools`() {
        val context = mockContext()
        val router = ToolRouter(context)
        router.register(FakeModule("alpha", listOf(ToolOperation("ping", "Ping", listOf()))))
        router.register(FakeModule("beta", listOf(ToolOperation("pong", "Pong", listOf())), available = false))

        val schema = router.generateSchema()

        assertThat(schema).contains("# Available Tools")
        assertThat(schema).contains("Unavailable Tools")
        assertThat(schema).contains("beta")
    }

    @Test
    fun `getAvailableModules filters by permissions`() {
        val context = mockContext(denyAll = true)
        val router = ToolRouter(context)
        router.register(
            FakeModule(
                "secure",
                listOf(ToolOperation("ping", "Ping", listOf())),
                requiredPermissions = listOf("perm.secure")
            )
        )

        val available = router.getAvailableModules()

        assertThat(available).isEmpty()
    }

    private fun mockContext(denyAll: Boolean = false): Context {
        val context = mockk<Context>()
        val perm = if (denyAll) PackageManager.PERMISSION_DENIED else PackageManager.PERMISSION_GRANTED
        every { context.checkSelfPermission(any()) } returns perm
        every { context.checkPermission(any(), any(), any()) } returns perm
        return context
    }

    private class FakeModule(
        override val moduleId: String,
        override val operations: List<ToolOperation>,
        private val available: Boolean = true,
        override val requiredPermissions: List<String> = emptyList()
    ) : ToolModule {
        override val description: String = "Fake module"

        override fun isAvailable(context: Context): Boolean = available

        override suspend fun execute(
            operationId: String,
            params: Map<String, Any?>,
            context: Context
        ): ToolResponse {
            return ToolResponse.Success(moduleId, operationId, "ok", params)
        }
    }
}
