#pragma once

#include <expected>
#include <string>

#include "llama.h"
#include "native_state.hpp"

namespace sentinel_native {

using InferenceResult = std::expected<std::string, std::string>;

[[nodiscard]] llama_sampler* create_sampler(const std::string& grammar_text);
[[nodiscard]] InferenceResult run_inference(const std::string& prompt, const std::string& grammar_text);

} // namespace sentinel_native
