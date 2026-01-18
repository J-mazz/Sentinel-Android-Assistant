#include "native_inference.hpp"

#include <algorithm>
#include <cstring>
#include <exception>
#include <memory>

#include "native_logging.hpp"
#include "native_utils.hpp"

namespace sentinel_native {

[[nodiscard]] llama_sampler* create_sampler(const std::string& grammar_text) {
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);

    llama_sampler_chain_add(sampler, llama_sampler_init_temp(g_state.temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(g_state.top_p, 1));

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

    llama_sampler_chain_add(sampler, llama_sampler_init_dist(42));
    return sampler;
}

[[nodiscard]] InferenceResult run_inference(const std::string& prompt, const std::string& grammar_text) {
    if (!g_state.is_ready()) {
        return std::unexpected("Model not loaded");
    }

    auto tokens = tokenize(prompt, true);
    if (tokens.empty()) {
        return std::unexpected("Failed to tokenize prompt");
    }

    LOGD("Prompt tokens: %zu", tokens.size());

    if (tokens.size() > static_cast<size_t>(g_state.n_ctx - g_state.max_tokens)) {
        return std::unexpected("Prompt too long for context window");
    }

    auto mem = llama_get_memory(g_state.ctx);
    if (mem) {
        llama_memory_clear(mem, false);
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int>(tokens.size()));

    if (llama_decode(g_state.ctx, batch) != 0) {
        return std::unexpected("Failed to process prompt");
    }

    const size_t buf_capacity = static_cast<size_t>(g_state.max_tokens) * 8 + 1;
    auto response_buf = std::unique_ptr<char[]>(new char[buf_capacity]);
    size_t response_len = 0;

    llama_sampler* sampler = create_sampler(grammar_text);
    if (!sampler) {
        return std::unexpected("Failed to create sampler");
    }

    for (int i = 0; i < g_state.max_tokens; ++i) {
        llama_token new_token;
        try {
            new_token = llama_sampler_sample(sampler, g_state.ctx, -1);
        } catch (const std::exception& e) {
            LOGE("Sampler error during sample: %s", e.what());
            llama_sampler_free(sampler);
            return std::unexpected(std::string("Sampler error: ") + e.what());
        }

        if (llama_vocab_is_eog(g_state.vocab, new_token)) {
            LOGD("EOS token at position %d", i);
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(g_state.vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            const size_t copy_len = std::min(static_cast<size_t>(n), buf_capacity - response_len - 1);
            if (copy_len > 0) {
                std::memcpy(response_buf.get() + response_len, buf, copy_len);
                response_len += copy_len;
            }
        }

        try {
            llama_sampler_accept(sampler, new_token);
        } catch (const std::exception& e) {
            LOGE("Sampler error during accept: %s", e.what());
            llama_sampler_free(sampler);
            return std::unexpected(std::string("Sampler error: ") + e.what());
        }

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

} // namespace sentinel_native
