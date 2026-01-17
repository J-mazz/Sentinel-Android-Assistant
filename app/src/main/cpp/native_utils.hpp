#pragma once

#include <jni.h>
#include <string>
#include <vector>

#include "llama.h"
#include "native_state.hpp"

namespace sentinel_native {

[[nodiscard]] std::string jstring_to_string(JNIEnv* env, jstring jstr);
[[nodiscard]] jstring string_to_jstring(JNIEnv* env, const std::string& str);

[[nodiscard]] std::vector<llama_token> tokenize(const std::string& text, bool add_bos = true);

[[nodiscard]] std::string apply_chat_template(
    const std::string& system_prompt,
    const std::string& user_message
);

} // namespace sentinel_native
