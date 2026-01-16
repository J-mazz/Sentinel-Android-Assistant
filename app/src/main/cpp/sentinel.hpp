// sentinel.hpp - Input sanitization and prompt building
#pragma once

#include <string>
#include <string_view>
#include <algorithm>
#include <cctype>

namespace sentinel {

// Dangerous patterns for injection detection
inline constexpr std::string_view INJECTION_PATTERNS[] = {
    "ignore previous", "ignore all", "disregard", "forget everything",
    "new instructions", "system prompt", "you are now", "act as",
    "pretend to be", "jailbreak", "DAN mode", "developer mode"
};

// Input sanitizer - inline for simplicity
inline std::string sanitize(std::string_view input, std::size_t max_len = 4096) {
    std::string result;
    result.reserve(std::min(input.size(), max_len));
    
    bool last_space = false;
    for (std::size_t i = 0; i < std::min(input.size(), max_len); ++i) {
        char c = input[i];
        if (c >= 32 || c == '\n' || c == '\t' || static_cast<unsigned char>(c) >= 128) {
            bool is_space = (c == ' ' || c == '\t');
            if (!is_space || !last_space) {
                result.push_back(is_space ? ' ' : c);
            }
            last_space = is_space;
        }
    }
    
    // Trim
    auto start = result.find_first_not_of(" \n\t");
    auto end = result.find_last_not_of(" \n\t");
    if (start == std::string::npos) return "";
    return result.substr(start, end - start + 1);
}

inline bool contains_injection(std::string_view input) {
    std::string lower;
    lower.reserve(input.size());
    for (char c : input) {
        lower.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
    }
    for (auto pattern : INJECTION_PATTERNS) {
        if (lower.find(pattern) != std::string::npos) return true;
    }
    return false;
}

// Prompt builder
inline constexpr std::string_view SYSTEM_PROMPT = R"(You are Sentinel, an Android accessibility agent. Output ONLY valid JSON.

RULES:
1. Output ONLY JSON, nothing else
2. Actions: tap, scroll, type, back, home, wait, none
3. Target must match exact text from screen
4. If unsure: {"action":"none","reasoning":"unclear"})";

inline std::string build_prompt(std::string_view query, std::string_view screen) {
    std::string prompt;
    prompt.reserve(screen.size() + query.size() + 512);
    
    prompt += "<|system|>\n";
    prompt += SYSTEM_PROMPT;
    prompt += "\n</|system|>\n\n<|screen|>\n";
    prompt += screen.substr(0, 16000);  // Truncate context
    prompt += "\n</|screen|>\n\n<|user|>\n";
    prompt += query;
    prompt += "\n</|user|>\n\n<|assistant|>\n";
    
    return prompt;
}

} // namespace sentinel
