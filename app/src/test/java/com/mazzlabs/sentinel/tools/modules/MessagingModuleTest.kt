package com.mazzlabs.sentinel.tools.modules

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.tools.framework.ErrorCode
import com.mazzlabs.sentinel.tools.framework.ParameterType
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * MessagingModuleTest - Tests module metadata and parameter validation
 * 
 * Note: Execution tests for send_sms, read_messages, etc. require actual
 * Android runtime (SmsManager, ContentResolver) and should be moved to
 * instrumented tests for proper coverage.
 */
class MessagingModuleTest {

    private lateinit var mockContext: Context
    private lateinit var messagingModule: MessagingModule

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        messagingModule = MessagingModule()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `moduleId is messaging`() {
        assertThat(messagingModule.moduleId).isEqualTo("messaging")
    }

    @Test
    fun `description mentions SMS or messaging`() {
        assertThat(messagingModule.description).containsMatch("(?i)sms|messag")
    }

    @Test
    fun `requiredPermissions includes SMS permissions`() {
        assertThat(messagingModule.requiredPermissions).containsAtLeast(
            "android.permission.SEND_SMS",
            "android.permission.READ_SMS"
        )
    }

    @Test
    fun `operations contains expected operations`() {
        val opIds = messagingModule.operations.map { it.operationId }
        assertThat(opIds).containsAtLeast(
            "send_sms",
            "read_messages",
            "get_conversations",
            "open_conversation"
        )
    }

    @Test
    fun `send_sms operation has phone parameter`() {
        val sendOp = messagingModule.operations.find { it.operationId == "send_sms" }!!
        
        val phoneParam = sendOp.parameters.find { it.name == "phone" }
        assertThat(phoneParam).isNotNull()
        assertThat(phoneParam?.type).isEqualTo(ParameterType.STRING)
        assertThat(phoneParam?.required).isTrue()
    }

    @Test
    fun `send_sms operation has message parameter`() {
        val sendOp = messagingModule.operations.find { it.operationId == "send_sms" }!!
        
        val messageParam = sendOp.parameters.find { it.name == "message" }
        assertThat(messageParam).isNotNull()
        assertThat(messageParam?.type).isEqualTo(ParameterType.STRING)
        assertThat(messageParam?.required).isTrue()
    }

    @Test
    fun `read_messages has optional phone filter`() {
        val readOp = messagingModule.operations.find { it.operationId == "read_messages" }!!
        
        val phoneParam = readOp.parameters.find { it.name == "phone" }
        assertThat(phoneParam?.required).isFalse()
    }

    @Test
    fun `read_messages has max_results parameter`() {
        val readOp = messagingModule.operations.find { it.operationId == "read_messages" }!!
        
        val maxParam = readOp.parameters.find { it.name == "max_results" }
        assertThat(maxParam).isNotNull()
        assertThat(maxParam?.type).isEqualTo(ParameterType.INTEGER)
    }

    @Test
    fun `open_conversation requires phone`() {
        val openOp = messagingModule.operations.find { it.operationId == "open_conversation" }!!
        
        val phoneParam = openOp.parameters.find { it.name == "phone" }
        assertThat(phoneParam?.required).isTrue()
    }

    @Test
    fun `send_sms has examples`() {
        val sendOp = messagingModule.operations.find { it.operationId == "send_sms" }
        assertThat(sendOp?.examples).isNotEmpty()
    }

    @Test
    fun `read_messages has examples`() {
        val readOp = messagingModule.operations.find { it.operationId == "read_messages" }
        assertThat(readOp?.examples).isNotEmpty()
    }

    @Test
    fun `execute returns error for unknown operation`() = runTest {
        val result = messagingModule.execute("unknown_op", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `get_conversations has max_results parameter`() {
        val getConvOp = messagingModule.operations.find { it.operationId == "get_conversations" }!!
        
        val maxParam = getConvOp.parameters.find { it.name == "max_results" }
        assertThat(maxParam).isNotNull()
        assertThat(maxParam?.required).isFalse()
    }

    @Test
    fun `open_conversation has optional message parameter`() {
        val openOp = messagingModule.operations.find { it.operationId == "open_conversation" }!!
        
        val msgParam = openOp.parameters.find { it.name == "message" }
        assertThat(msgParam).isNotNull()
        assertThat(msgParam?.required).isFalse()
    }
}

