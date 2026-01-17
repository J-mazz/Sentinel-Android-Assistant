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
#include <android/log.h>

// Standard library headers (C++23)
#include <string>
#include <string_view>
#include <memory>
#include <mutex>
#include <optional>
#include <expected>
#include <format>
#include <vector>
#include <cstdio>
#include <fstream>
#include <sstream>
#include <cstring>

// llama.cpp headers
#include "llama.h"

// Sentinel module shim (header-based until NDK supports modules)
#include "sentinel.hpp"

// ============================================================================
// Logging Macros
// ============================================================================
#define LOG_TAG "SentinelNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ============================================================================
// Global State (Thread-Safe)
// ============================================================================
namespace {
    std::mutex g_model_mutex;
    
    struct ModelState {
        llama_model* model = nullptr;
        llama_context* ctx = nullptr;
        const llama_vocab* vocab = nullptr;
        llama_sampler* sampler = nullptr;
        std::string chat_template;  // Jinja template from model or custom
        std::string grammar_text;
        
        // Inference parameters - lower temp for more consistent JSON
        float temperature = 0.3f;
        float top_p = 0.9f;
        int32_t max_tokens = 256;
        int32_t n_ctx = 4096;
        
        [[nodiscard]] constexpr bool is_ready() const noexcept {
            return model != nullptr && ctx != nullptr && vocab != nullptr;
        }
        
        void reset() noexcept {
            if (sampler) {
                llama_sampler_free(sampler);
                sampler = nullptr;
            }
            if (ctx) {
                llama_free(ctx);
                ctx = nullptr;
            }
            if (model) {
                llama_model_free(model);
                model = nullptr;
            }
            vocab = nullptr;
            chat_template.clear();
            grammar_text.clear();
        }
    };
    
    ModelState g_state;
}

// ============================================================================
// Helper Functions
// ============================================================================
namespace {
    /**
     * Convert JNI string to std::string safely
     */
    [[nodiscard]] std::string jstring_to_string(JNIEnv* env, jstring jstr) {
        if (!jstr) return "";
        
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        if (!chars) return "";
        
        std::string result(chars);
        env->ReleaseStringUTFChars(jstr, chars);
        return result;
    }
    
    /**
     * Create JNI string from std::string
     */
    [[nodiscard]] jstring string_to_jstring(JNIEnv* env, const std::string& str) {
        return env->NewStringUTF(str.c_str());
    }
    
    /**
     * Tokenize input text using new API
     */
    [[nodiscard]] std::vector<llama_token> tokenize(const std::string& text, bool add_bos = true) {
        std::vector<llama_token> tokens(text.length() + 64);
        
        int n_tokens = llama_tokenize(
            g_state.vocab,
            text.c_str(),
            static_cast<int>(text.length()),
            tokens.data(),
            static_cast<int>(tokens.size()),
            add_bos,
            true  // special tokens
        );
        
        if (n_tokens < 0) {
            tokens.resize(-n_tokens);
            n_tokens = llama_tokenize(
                g_state.vocab,
                text.c_str(),
                static_cast<int>(text.length()),
                tokens.data(),
                static_cast<int>(tokens.size()),
                add_bos,
                true
            );
        }
        
        tokens.resize(std::max(0, n_tokens));
        return tokens;
    }
    
    /**
     * Apply chat template to format messages for the model
     * Uses model's built-in template or falls back to ChatML format
     */
    [[nodiscard]] std::string apply_chat_template(
        const std::string& system_prompt,
        const std::string& user_message
    ) {
        // Build messages array
        std::vector<llama_chat_message> messages;
        
        if (!system_prompt.empty()) {
            messages.push_back({"system", system_prompt.c_str()});
        }
        messages.push_back({"user", user_message.c_str()});
        
        // Get template to use (model's template or nullptr for default)
        const char* tmpl = g_state.chat_template.empty() ? nullptr : g_state.chat_template.c_str();
        
        // First call to get required buffer size
        int32_t buf_size = llama_chat_apply_template(
            tmpl,
            messages.data(),
            messages.size(),
            true,  // add_ass (add generation prompt)
            nullptr,
            0
        );
        
        if (buf_size <= 0) {
            LOGW("Chat template failed, falling back to simple format");
            // Fallback to simple format
            if (system_prompt.empty()) {
                return user_message;
            }
            return system_prompt + "\n\n" + user_message;
        }
        
        // Allocate buffer and apply template
        std::string result(buf_size + 1, '\0');
        llama_chat_apply_template(
            tmpl,
            messages.data(),
            messages.size(),
            true,
            result.data(),
            result.size()
        );
        
        // Trim to actual size
        result.resize(strlen(result.c_str()));
        return result;
    }
    
    /**
     * Run inference with optional grammar constraint
     * Returns std::expected (C++23) for clean error handling
     */
    using InferenceResult = std::expected<std::string, std::string>;

    [[nodiscard]] llama_sampler* create_sampler(const std::string& grammar_text) {
        llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        llama_sampler* sampler = llama_sampler_chain_init(sparams);

        llama_sampler_chain_add(sampler,
            llama_sampler_init_temp(g_state.temperature));
        llama_sampler_chain_add(sampler,
            llama_sampler_init_top_p(g_state.top_p, 1));

        if (!grammar_text.empty()) {
            auto grammar_sampler = llama_sampler_init_grammar(
                g_state.vocab,
                grammar_text.c_str(),
                "root"
            );
            if (grammar_sampler) {
                llama_sampler_chain_add(sampler, grammar_sampler);
            } else {
                LOGW("Failed to create grammar sampler");
            }
        }

        llama_sampler_chain_add(sampler,
            llama_sampler_init_dist(42));

        return sampler;
    }

    [[nodiscard]] InferenceResult run_inference(const std::string& prompt, const std::string& grammar_text) {
        if (!g_state.is_ready()) {
            return std::unexpected("Model not loaded");
        }
        
        // Tokenize prompt
        auto tokens = tokenize(prompt, true);
        if (tokens.empty()) {
            return std::unexpected("Failed to tokenize prompt");
        }
        
        LOGD("Prompt tokens: %zu", tokens.size());
        
        // Check context size
        if (tokens.size() > static_cast<size_t>(g_state.n_ctx - g_state.max_tokens)) {
            return std::unexpected("Prompt too long for context window");
        }
        
        // Clear KV cache using new API
        auto mem = llama_get_memory(g_state.ctx);
        if (mem) {
            llama_memory_clear(mem, false);
        }
        
        // Create batch for prompt processing
        llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int>(tokens.size()));
        
        // Process prompt
        if (llama_decode(g_state.ctx, batch) != 0) {
            return std::unexpected("Failed to process prompt");
        }
        
        // Generate response using preallocated C buffer
        const size_t buf_capacity = static_cast<size_t>(g_state.max_tokens) * 8 + 1;
        auto response_buf = std::unique_ptr<char[]>(new char[buf_capacity]);
        size_t response_len = 0;

        llama_sampler* sampler = create_sampler(grammar_text);
        if (!sampler) {
            return std::unexpected("Failed to create sampler");
        }
        
        for (int i = 0; i < g_state.max_tokens; ++i) {
            llama_token new_token = llama_sampler_sample(sampler, g_state.ctx, -1);
            
            // Check for end of generation using new API
            if (llama_vocab_is_eog(g_state.vocab, new_token)) {
                LOGD("EOS token at position %d", i);
                break;
            }
            
            // Decode token to text using new API
            char buf[128];
            int n = llama_token_to_piece(g_state.vocab, new_token, buf, sizeof(buf), 0, true);
            if (n > 0) {
                const size_t copy_len = std::min(static_cast<size_t>(n), buf_capacity - response_len - 1);
                if (copy_len > 0) {
                    std::memcpy(response_buf.get() + response_len, buf, copy_len);
                    response_len += copy_len;
                }
            }
            
            // Accept token into sampler
            llama_sampler_accept(sampler, new_token);
            
            // Create single-token batch for next iteration
            batch = llama_batch_get_one(&new_token, 1);
            
            if (llama_decode(g_state.ctx, batch) != 0) {
                LOGW("Decode failed at token %d", i);
                break;
            }
        }

        response_buf.get()[response_len] = '\0';
        llama_sampler_free(sampler);

        LOGD("Generated %zu characters", response_len);
        return std::string(response_buf.get(), response_len);
    }
}

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
    std::lock_guard lock(g_model_mutex);  // C++17 CTAD
    
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
    std::lock_guard lock(g_model_mutex);
    
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
    std::lock_guard lock(g_model_mutex);

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
 * Release model resources
 */
JNIEXPORT void JNICALL
Java_com_mazzlabs_sentinel_core_NativeBridge_releaseModel(
    JNIEnv* /* env */,
    jobject /* this */
) {
    std::lock_guard lock(g_model_mutex);
    
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
    std::lock_guard lock(g_model_mutex);
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
    std::lock_guard lock(g_model_mutex);
    
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
    std::lock_guard lock(g_model_mutex);
    
    g_state.temperature = temperature;
    g_state.top_p = topP;
    g_state.max_tokens = maxTokens;
    
    LOGI("Inference params updated: temp=%.2f, top_p=%.2f, max_tokens=%d",
         temperature, topP, maxTokens);
}

} // extern "C"
