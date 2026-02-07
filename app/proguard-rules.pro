# Sentinel Agent ProGuard Rules

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NativeBridge
-keep class com.mazzlabs.sentinel.core.NativeBridge { *; }

# Keep data classes used with JSON
-keep class com.mazzlabs.sentinel.model.** { *; }

# Keep accessibility service
-keep class com.mazzlabs.sentinel.service.AgentAccessibilityService { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Graph state and orchestration
-keep class com.mazzlabs.sentinel.graph.AgentState { *; }
-keep class com.mazzlabs.sentinel.graph.AgentIntent { *; }
-keep class com.mazzlabs.sentinel.graph.Plan { *; }
-keep class com.mazzlabs.sentinel.graph.PlanStep { *; }
-keep class com.mazzlabs.sentinel.graph.Message { *; }
-keep class com.mazzlabs.sentinel.graph.Role { *; }

# Tool framework
-keep class com.mazzlabs.sentinel.tools.framework.ToolResponse { *; }
-keep class com.mazzlabs.sentinel.tools.framework.ToolResponse$* { *; }
-keep class com.mazzlabs.sentinel.tools.framework.ErrorCode { *; }
-keep class com.mazzlabs.sentinel.tools.framework.ToolModule { *; }
-keep class com.mazzlabs.sentinel.tools.modules.** { *; }

# Security
-keep class com.mazzlabs.sentinel.security.** { *; }

# Model classes used in serialization
-keep class com.mazzlabs.sentinel.model.** { *; }
