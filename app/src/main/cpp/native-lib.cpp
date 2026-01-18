/**
 * native-lib.cpp - The Cortex (Privileged Sanctum)
 * 
 * JNI bridge hosting llama.cpp for Jamba-Reasoning-3B inference.
 * Uses Jinja chat templates for prompt formatting.
 * 
 * Security Architecture:
 *   1. Input Sanitization (regex-based control token stripping)
 *   2. Context Wrapping (chat template formatting)
 *   3. Greedy/Top-p sampling (no grammar constraint)
 * 
 * C++23 Standard - Updated for llama.cpp latest API
 */

// JNI and Android headers
#include <jni.h>

// Standard library headers (C++23)
#include <expected>
#include <format>
#include <fstream>
#include <shared_mutex>
#include <sstream>
#include <string>

// llama.cpp headers
#include "llama.h"

// Sentinel module shim (header-based until NDK supports modules)
#include "sentinel.hpp"

#include "native_inference.hpp"
#include "native_logging.hpp"
#include "native_state.hpp"
#include "native_utils.hpp"

using namespace sentinel_native;

// ============================================================================
// JNI Exports
// ============================================================================
extern "C" {

/**
 * Initialize the Jamba model
 */
JNIEXPORT jboolean JNICALL
Java_com_mazzlabs_sentinel_core_NativeBridge_initModel(
    JNIEnv* env,
    jobject /* this */,
    jstring jModelPath,
    jstring jGrammarPath
) {
    std::unique_lock lock(g_model_mutex);
    
    g_state.reset();
    
    auto model_path = jstring_to_string(env, jModelPath);
    auto grammar_path = jstring_to_string(env, jGrammarPath);
    
    LOGI("Initializing model: %s", model_path.c_str());
    
    // Initialize llama backend
    llama_backend_init();
    
    // Load model using new API
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 99;  // Offload as many layers as possible to GPU
    
    g_state.model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!g_state.model) {
        LOGE("Failed to load model from: %s", model_path.c_str());
        return JNI_FALSE;
    }
    
    // Get vocab from model
    g_state.vocab = llama_model_get_vocab(g_state.model);
    if (!g_state.vocab) {
        LOGE("Failed to get vocab from model");
        g_state.reset();
        return JNI_FALSE;
    }
    
    LOGI("Model loaded successfully");

    // Load grammar file if provided
    if (!grammar_path.empty()) {
        std::ifstream grammar_file(grammar_path);
        if (grammar_file.is_open()) {
            std::stringstream buffer;
            buffer << grammar_file.rdbuf();
            g_state.grammar_text = buffer.str();
            LOGI("Grammar loaded: %zu bytes", g_state.grammar_text.size());
        } else {
            LOGW("Grammar file not found: %s", grammar_path.c_str());
        }
    }
    
    // Try to get the model's chat template
    const char* tmpl = llama_model_chat_template(g_state.model, nullptr);
    if (tmpl) {
        g_state.chat_template = tmpl;
        LOGI("Using model's chat template");
    } else {
        LOGI("Model has no chat template, will use fallback");
    }
    
    // Create context using new API
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = g_state.n_ctx;
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 512;
    
    g_state.ctx = llama_init_from_model(g_state.model, ctx_params);
    if (!g_state.ctx) {
        LOGE("Failed to create context");
        g_state.reset();
        return JNI_FALSE;
    }
    
    LOGI("Context created successfully");
    
    // Create sampler chain (no grammar - just temp + top-p + dist)
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_state.sampler = llama_sampler_chain_init(sparams);
    
    // Add temperature and top-p sampling
    llama_sampler_chain_add(g_state.sampler, 
        llama_sampler_init_temp(g_state.temperature));
    llama_sampler_chain_add(g_state.sampler, 
        llama_sampler_init_top_p(g_state.top_p, 1));

    // Add grammar constraint if available
    if (!g_state.grammar_text.empty()) {
        auto grammar_sampler = llama_sampler_init_grammar(
            g_state.vocab,
            g_state.grammar_text.c_str(),
            "root"
        );
        if (grammar_sampler) {
            llama_sampler_chain_add(g_state.sampler, grammar_sampler);
            LOGI("Grammar sampler added to chain");
        } else {
            LOGW("Failed to create grammar sampler");
        }
    }
    llama_sampler_chain_add(g_state.sampler, 
        llama_sampler_init_dist(42));  // Random seed
    
    LOGI("Model initialization complete (chat template mode)");
    return JNI_TRUE;
}

/**
 * Run inference with chat template formatting
 */
JNIEXPORT jstring JNICALL
Java_com_mazzlabs_sentinel_core_NativeBridge_infer(
    JNIEnv* env,
    jobject /* this */,
    jstring jUserQuery,
    jstring jScreenContext
) {
    std::unique_lock lock(g_model_mutex);
    
    if (!g_state.is_ready()) {
        LOGE("Model not ready for inference");
        return string_to_jstring(env, R"({"action":"NONE","reasoning":"Model not loaded"})");
    }
    
    auto user_query = jstring_to_string(env, jUserQuery);
    auto screen_context = jstring_to_string(env, jScreenContext);
    
    LOGD("User query: %s", user_query.c_str());
    LOGD("Screen context length: %zu", screen_context.size());
    
    // Check for injection attempts
    if (sentinel::contains_injection(user_query)) {
        return string_to_jstring(env, R"({"action":"none","reasoning":"blocked"})");
    }
    
    // Sanitize inputs
    auto safe_query = sentinel::sanitize(user_query, 2048);
    auto safe_context = sentinel::sanitize(screen_context, 32000);
    
    // Build system prompt with context
    std::string system_prompt = R"(You are an Android accessibility agent. Analyze the screen and respond with a JSON action.

Available actions:
- CLICK: {"action":"CLICK","target":"element_id","reasoning":"why"}
- TYPE: {"action":"TYPE","target":"element_id","text":"what to type","reasoning":"why"}
- SCROLL: {"action":"SCROLL","direction":"up|down|left|right","reasoning":"why"}
- BACK: {"action":"BACK","reasoning":"why"}
- NONE: {"action":"NONE","reasoning":"why nothing needed"}

Current screen context:
)" + safe_context + R"(

Respond ONLY with valid JSON. No markdown, no explanation outside JSON.)";

    // Apply chat template
    auto prompt = apply_chat_template(system_prompt, safe_query);
    
    LOGD("Final prompt length: %zu", prompt.size());
    
    // Run inference
    auto result = run_inference(prompt, g_state.grammar_text);
    
    if (result) {
        LOGI("Inference result: %s", result.value().c_str());
        return string_to_jstring(env, *result);
    } else {
        LOGE("Inference failed: %s", result.error().c_str());
        return string_to_jstring(env, 
            std::format(R"({{"action":"NONE","reasoning":"{}"}})", result.error()));
    }
}

/**
 * Run inference with a per-call grammar path
 */
JNIEXPORT jstring JNICALL
Java_com_mazzlabs_sentinel_core_NativeBridge_inferWithGrammar(
    JNIEnv* env,
    jobject /* this */,
    jstring jUserQuery,
    jstring jScreenContext,
    jstring jGrammarPath
) {
    std::unique_lock lock(g_model_mutex);

    if (!g_state.is_ready()) {
        LOGE("Model not ready for inference");
        return string_to_jstring(env, R"({"action":"NONE","reasoning":"Model not loaded"})");
    }

    auto user_query = jstring_to_string(env, jUserQuery);
    auto screen_context = jstring_to_string(env, jScreenContext);
    auto grammar_path = jstring_to_string(env, jGrammarPath);

    LOGD("User query: %s", user_query.c_str());
    LOGD("Screen context length: %zu", screen_context.size());

    if (sentinel::contains_injection(user_query)) {
        return string_to_jstring(env, R"({"action":"none","reasoning":"blocked"})");
    }

    auto safe_query = sentinel::sanitize(user_query, 2048);
    auto safe_context = sentinel::sanitize(screen_context, 32000);

    std::string system_prompt = safe_context;

    auto prompt = apply_chat_template(system_prompt, safe_query);

    std::string grammar_text;
    if (!grammar_path.empty()) {
        std::ifstream grammar_file(grammar_path);
        if (grammar_file.is_open()) {
            std::stringstream buffer;
            buffer << grammar_file.rdbuf();
            grammar_text = buffer.str();
        } else {
            LOGW("Grammar file not found: %s", grammar_path.c_str());
        }
    }

    auto result = run_inference(prompt, grammar_text);

    if (result) {
        return string_to_jstring(env, *result);
    } else {
        LOGE("Inference failed: %s", result.error().c_str());
        return string_to_jstring(env,
            std::format(R"({{"action":"NONE","reasoning":"{}"}})", result.error()));
    }
}

/**
 * Run inference WITHOUT grammar constraint (free-form generation)
 * Use as fallback when grammar-constrained inference fails
 */
JNIEXPORT jstring JNICALL
Java_com_mazzlabs_sentinel_core_NativeBridge_inferWithoutGrammar(
    JNIEnv* env,
    jobject /* this */,
    jstring jUserQuery,
    jstring jScreenContext
) {
    std::unique_lock lock(g_model_mutex);

    if (!g_state.is_ready()) {
        LOGE("Model not ready for inference");
        return string_to_jstring(env, R"({"action":"NONE","reasoning":"Model not loaded"})");
    }

    auto user_query = jstring_to_string(env, jUserQuery);
    auto screen_context = jstring_to_string(env, jScreenContext);

    LOGD("User query (no grammar): %s", user_query.c_str());
    LOGD("Screen context length: %zu", screen_context.size());

    if (sentinel::contains_injection(user_query)) {
        return string_to_jstring(env, R"({"action":"none","reasoning":"blocked"})");
    }

    auto safe_query = sentinel::sanitize(user_query, 2048);
    auto safe_context = sentinel::sanitize(screen_context, 32000);

    std::string system_prompt = R"(You are an Android accessibility agent. Analyze the screen and respond with a JSON action.

Available actions:
- CLICK: {"action":"CLICK","target":"element_id","reasoning":"why"}
- TYPE: {"action":"TYPE","target":"element_id","text":"what to type","reasoning":"why"}
- SCROLL: {"action":"SCROLL","direction":"up|down|left|right","reasoning":"why"}
- BACK: {"action":"BACK","reasoning":"why"}
- NONE: {"action":"NONE","reasoning":"why nothing needed"}

Current screen context:
)" + safe_context + R"(

Respond ONLY with valid JSON. No markdown, no explanation outside JSON.)";

    auto prompt = apply_chat_template(system_prompt, safe_query);

    LOGD("Final prompt length: %zu", prompt.size());

    // Run inference WITHOUT grammar (empty string = no grammar constraint)
    auto result = run_inference(prompt, "");

    if (result) {
        LOGI("Inference result (no grammar): %s", result.value().c_str());
        return string_to_jstring(env, *result);
    } else {
        LOGE("Inference failed: %s", result.error().c_str());
        return string_to_jstring(env,
            std::format(R"({{"action":"NONE","reasoning":"{}"}})", result.error()));
    }
}

/**
 * Release model resources
 */
JNIEXPORT void JNICALL
Java_com_mazzlabs_sentinel_core_NativeBridge_releaseModel(
    JNIEnv* /* env */,
    jobject /* this */
) {
    std::unique_lock lock(g_model_mutex);

    LOGI("Releasing model resources");
    g_state.reset();
    llama_backend_free();
}

/**
 * Check if model is ready
 */
JNIEXPORT jboolean JNICALL
Java_com_mazzlabs_sentinel_core_NativeBridge_isModelReady(
    JNIEnv* /* env */,
    jobject /* this */
) {
    std::shared_lock lock(g_model_mutex);
    return g_state.is_ready() ? JNI_TRUE : JNI_FALSE;
}

/**
 * Get model metadata
 */
JNIEXPORT jstring JNICALL
Java_com_mazzlabs_sentinel_core_NativeBridge_getModelInfo(
    JNIEnv* env,
    jobject /* this */
) {
    std::shared_lock lock(g_model_mutex);
    
    if (!g_state.model || !g_state.vocab) {
        return string_to_jstring(env, R"({"loaded":false})");
    }
    
    auto n_vocab = llama_vocab_n_tokens(g_state.vocab);
    auto n_ctx_train = llama_model_n_ctx_train(g_state.model);
    
    auto info = std::format(
        R"({{"loaded":true,"n_vocab":{},"n_ctx_train":{},"n_ctx":{}}})",
        n_vocab, n_ctx_train, g_state.n_ctx
    );
    
    return string_to_jstring(env, info);
}

/**
 * Set inference parameters
 */
JNIEXPORT void JNICALL
Java_com_mazzlabs_sentinel_core_NativeBridge_setInferenceParams(
    JNIEnv* /* env */,
    jobject /* this */,
    jfloat temperature,
    jfloat topP,
    jint maxTokens
) {
    std::unique_lock lock(g_model_mutex);
    
    g_state.temperature = temperature;
    g_state.top_p = topP;
    g_state.max_tokens = maxTokens;
    
    LOGI("Inference params updated: temp=%.2f, top_p=%.2f, max_tokens=%d",
         temperature, topP, maxTokens);
}

} // extern "C"
