#include "native_utils.hpp"

#include <cstring>

#include "native_logging.hpp"

namespace sentinel_native {

[[nodiscard]] std::string jstring_to_string(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";

    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (!chars) return "";

    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

[[nodiscard]] jstring string_to_jstring(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

[[nodiscard]] std::vector<llama_token> tokenize(const std::string& text, bool add_bos) {
    std::vector<llama_token> tokens(text.length() + 64);

    int n_tokens = llama_tokenize(
        g_state.vocab,
        text.c_str(),
        static_cast<int>(text.length()),
        tokens.data(),
        static_cast<int>(tokens.size()),
        add_bos,
        true
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

[[nodiscard]] std::string apply_chat_template(
    const std::string& system_prompt,
    const std::string& user_message
) {
    std::vector<llama_chat_message> messages;

    if (!system_prompt.empty()) {
        messages.push_back({"system", system_prompt.c_str()});
    }
    messages.push_back({"user", user_message.c_str()});

    const char* tmpl = g_state.chat_template.empty() ? nullptr : g_state.chat_template.c_str();

    int32_t buf_size = llama_chat_apply_template(
        tmpl,
        messages.data(),
        messages.size(),
        true,
        nullptr,
        0
    );

    if (buf_size <= 0) {
        LOGW("Chat template failed, falling back to simple format");
        if (system_prompt.empty()) {
            return user_message;
        }
        return system_prompt + "\n\n" + user_message;
    }

    std::string result(static_cast<size_t>(buf_size) + 1, '\0');
    llama_chat_apply_template(
        tmpl,
        messages.data(),
        messages.size(),
        true,
        result.data(),
        result.size()
    );

    result.resize(strlen(result.c_str()));
    return result;
}

} // namespace sentinel_native
