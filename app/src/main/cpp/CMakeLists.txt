# CMakeLists.txt for Sentinel Agent Native Library
# C++23 with Module Shims (.ixx) for Android ARM64

cmake_minimum_required(VERSION 3.28)
project(sentinel_native LANGUAGES C CXX)

# ============================================================================
# C++23 Standard
# ============================================================================
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)

# ============================================================================
# Build Configuration
# ============================================================================
set(CMAKE_BUILD_TYPE Release)

set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")

# ARM64 optimizations
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.4-a+dotprod+fp16")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.4-a+dotprod+fp16")
endif()

# ============================================================================
# llama.cpp Integration
# ============================================================================
set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libs/llama.cpp)

if(NOT EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    message(FATAL_ERROR 
        "llama.cpp not found at ${LLAMA_CPP_DIR}\n"
        "Run: git clone https://github.com/ggerganov/llama.cpp ${LLAMA_CPP_DIR}"
    )
endif()

# llama.cpp build options
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.4-a+dotprod+fp16" CACHE STRING "" FORCE)

add_subdirectory(${LLAMA_CPP_DIR} llama.cpp.build EXCLUDE_FROM_ALL)

# ============================================================================
# Sentinel Native Library
# ============================================================================
add_library(sentinel_native SHARED
    native-lib.cpp
)

target_include_directories(sentinel_native PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(sentinel_native PRIVATE
    llama
    ggml
    android
    log
)

target_compile_features(sentinel_native PRIVATE cxx_std_23)

set_target_properties(sentinel_native PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    C_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

install(TARGETS sentinel_native LIBRARY DESTINATION lib/${ANDROID_ABI})
