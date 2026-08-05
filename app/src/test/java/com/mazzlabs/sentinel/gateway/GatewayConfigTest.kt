package com.mazzlabs.sentinel.gateway

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GatewayConfigTest {

    @Test
    fun `all agent roles use the local Gemma model`() {
        assertThat(GatewayConfig.Models.ARCHITECT)
            .isEqualTo(GatewayConfig.Models.LOCAL_DEFAULT)
        assertThat(GatewayConfig.Models.ENGINEER)
            .isEqualTo(GatewayConfig.Models.LOCAL_DEFAULT)
        assertThat(GatewayConfig.Models.FIXER)
            .isEqualTo(GatewayConfig.Models.LOCAL_DEFAULT)
        assertThat(GatewayConfig.Models.LOCAL_DEFAULT)
            .isEqualTo("gemma-local/gemma-4-e2b-it")
    }
}
