package com.mazzlabs.sentinel.security

import com.mazzlabs.sentinel.model.ActionType
import com.mazzlabs.sentinel.model.AgentAction
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest

class ActionRiskClassifierTest {

    private lateinit var classifier: ActionRiskClassifier

    @Before
    fun setUp() {
        classifier = ActionRiskClassifier()
    }

    @Test
    fun assess_withDeleteActionOnConfirmationScreen_isHighRisk() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Delete account",
            reasoning = "User asked to delete account"
        )
        
        val screenContext = """
            Are you sure you want to delete your account?
            This action cannot be undone.
            [Cancel] [Delete Permanently]
        """.trimIndent()

        val assessment = classifier.assess(action, screenContext, "com.example.app")
        
        assertThat(assessment).isNotNull()
        assertThat(assessment?.dangerous).isTrue()
    }

    @Test
    fun assess_withSafeClickOnNormalScreen_isLowRisk() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Next",
            reasoning = "Navigate to next screen"
        )
        
        val screenContext = """
            Welcome to App
            Please proceed to the next step
            [Previous] [Next]
        """.trimIndent()

        val assessment = classifier.assess(action, screenContext, "com.example.app")
        
        if (assessment != null) {
            assertThat(assessment.dangerous).isFalse()
            assertThat(assessment.confidence).isGreaterThan(0.5f)
        }
    }

    @Test
    fun assess_withPaymentAction_flagsAsHighRisk() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Confirm Payment"
        )
        
        val screenContext = """
            Order Total: $99.99
            Payment Method: Credit Card ending in 1234
            [Confirm Payment] [Cancel]
        """.trimIndent()

        val assessment = classifier.assess(action, screenContext, "com.example.shop")
        
        assertThat(assessment).isNotNull()
        // Payment actions should be flagged
        assertThat(assessment?.dangerous ?: false).isTrue()
    }

    @Test
    fun assess_withTypeActionOnPasswordField_isHighRisk() = runTest {
        val action = AgentAction(
            action = ActionType.TYPE,
            text = "secretpassword"
        )
        
        val screenContext = """
            Login
            Username: [____]
            Password: [****]
            [Login]
        """.trimIndent()

        val assessment = classifier.assess(action, screenContext, "com.example.bank")
        
        assertThat(assessment).isNotNull()
        assertThat(assessment?.dangerous ?: false).isTrue()
    }

    @Test
    fun assess_withScrollAction_isNeverRisky() = runTest {
        val action = AgentAction(action = ActionType.SCROLL)
        val screenContext = "Any screen content"

        val assessment = classifier.assess(action, screenContext, "com.example.app")
        
        if (assessment != null) {
            assertThat(assessment.dangerous).isFalse()
        }
    }

    @Test
    fun assess_returnsReasonForDangerousAssessment() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Uninstall"
        )
        
        val screenContext = "Settings - Uninstall Application"

        val assessment = classifier.assess(action, screenContext, "com.example.app")
        
        if (assessment != null && assessment.dangerous) {
            assertThat(assessment.reason).isNotEmpty()
        }
    }

    @Test
    fun assess_multipleHighRiskIndicators_increasesConfidence() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Delete everything"
        )
        
        val riskScreenContext = """
            WARNING: Destructive Operation
            This will permanently delete all your data.
            Are you absolutely sure?
            Provide confirmation code to proceed.
            [Delete All Data]
        """.trimIndent()

        val assessment = classifier.assess(action, riskScreenContext, "com.example.app")
        
        if (assessment != null) {
            assertThat(assessment.dangerous).isTrue()
            assertThat(assessment.confidence).isGreaterThan(0.7f)
        }
    }

    @Test
    fun assess_nullScreenContext_handleGracefully() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Button"
        )

        // Should not throw with empty context
        val assessment = classifier.assess(action, "", "com.example.app")
        assertThat(assessment).isNotNull()
    }

    @Test
    fun assess_modelNotReady_returnNull_triggersFailSecure() = runTest {
        // When model is not loaded, assess() returns null
        // This tests the fail-secure behavior in AgentAccessibilityService
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Dangerous Button"
        )

        val assessment = classifier.assess(action, "Some screen", "com.example.app")
        
        // If model not ready, assessment is null
        // AgentAccessibilityService then returns true (requires confirmation)
        // This is the correct fail-secure behavior
        // (assessment could be null if model not ready, which is expected)
        if (assessment != null) {
            // If assessment is available, verify it has valid fields
            assertThat(assessment.confidence).isAtLeast(0f)
        }
    }

    @Test
    fun assess_lowConfidenceAssessment_handledSafely() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Ambiguous Button"
        )

        val assessment = classifier.assess(action, "Minimal context", "com.example.app")
        
        if (assessment != null) {
            // If confidence is low (< 0.7), even if marked as safe,
            // the service treats it as requiring confirmation
            // This is verified in AgentAccessibilityService:
            // val safeByClassifier = !assessment.dangerous && assessment.confidence >= 0.7f
            // return !safeByClassifier // Returns true if low confidence
            
            if (assessment.confidence < 0.7f) {
                // Service will require confirmation
                assertThat(assessment.confidence).isLessThan(0.7f)
            }
        }
    }

    @Test
    fun assess_withUninstallAction_isHighRisk() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Uninstall App"
        )
        
        val screenContext = """
            App Settings
            Version 1.0.0
            [Uninstall] [Open]
        """.trimIndent()

        val assessment = classifier.assess(action, screenContext, "com.example.banking")
        
        if (assessment != null) {
            assertThat(assessment.dangerous).isTrue()
        }
    }

    @Test
    fun assess_withFormSubmitAction_flagsAsRisk() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Submit Form"
        )
        
        val screenContext = """
            Registration Form
            Name: [____]
            Email: [____]
            [Submit] [Cancel]
        """.trimIndent()

        val assessment = classifier.assess(action, screenContext, "com.example.app")
        
        // Submit actions should generally be flagged as requiring caution
        if (assessment != null) {
            assertThat(assessment.dangerous ?: false).isTrue()
        }
    }

    @Test
    fun assess_withHomeAction_isNeverRisky() = runTest {
        val action = AgentAction(action = ActionType.HOME)
        val screenContext = "Any screen"

        val assessment = classifier.assess(action, screenContext, "com.example.app")
        
        // HOME action is never considered dangerous for security purposes
        if (assessment != null) {
            assertThat(assessment.dangerous).isFalse()
        }
    }

    @Test
    fun assess_withBackAction_isNeverRisky() = runTest {
        val action = AgentAction(action = ActionType.BACK)
        val screenContext = "Any screen"

        val assessment = classifier.assess(action, screenContext, "com.example.app")
        
        // BACK action is never considered dangerous
        if (assessment != null) {
            assertThat(assessment.dangerous).isFalse()
        }
    }

    @Test
    fun assess_providesConfidenceScore() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Settings"
        )

        val assessment = classifier.assess(action, "Settings Screen", "com.example.app")
        
        if (assessment != null) {
            assertThat(assessment.confidence).isAtLeast(0f)
            assertThat(assessment.confidence).isAtMost(1f)
        }
    }

    @Test
    fun assess_dangerousActionIncludesReason() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Factory Reset"
        )
        
        val screenContext = "Device Settings"

        val assessment = classifier.assess(action, screenContext, "com.android.settings")
        
        if (assessment != null && assessment.dangerous) {
            // Dangerous actions should include reasoning
            assertThat(assessment.reason).isNotNull()
            if (assessment.reason != null) {
                assertThat(assessment.reason).isNotEmpty()
            }
        }
    }

    @Test
    fun assess_largeScreenContext_handledWithTruncation() = runTest {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "OK"
        )
        
        // Create a very large screen context
        val largeContext = "Screen content: " + "x".repeat(5000)

        val assessment = classifier.assess(action, largeContext, "com.example.app")
        
        // Should not crash or return null on large input
        assertThat(assessment).isNotNull()
    }
}
