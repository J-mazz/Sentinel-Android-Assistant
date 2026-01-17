#pragma once

#include <cstdint>
#include <shared_mutex>
#include <string>

#include "llama.h"

namespace sentinel_native {

struct ModelState {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    llama_sampler* sampler = nullptr;
    std::string chat_template;
    std::string grammar_text;

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

extern std::shared_mutex g_model_mutex;
extern ModelState g_state;

} // namespace sentinel_native
