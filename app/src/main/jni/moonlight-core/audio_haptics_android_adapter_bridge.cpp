// SPDX-License-Identifier: GPL-3.0-or-later

#include "audio_haptics_android_adapter_bridge.h"

#ifndef MOONLIGHT_AUDIO_HAPTICS_OUTPUT
#define MOONLIGHT_AUDIO_HAPTICS_OUTPUT 0
#endif

#if MOONLIGHT_AUDIO_HAPTICS_OUTPUT

#include "moonlight_haptics/android_adapter.h"

#include <android/log.h>
#include <dlfcn.h>
#include <sched.h>

#include <atomic>
#include <cstdint>
#include <cstring>

namespace {

constexpr const char* kLogTag = "moonlight-haptics";

using AcquireFunction = void (*)(MhAndroidSession*);
using ReleaseFunction = void (*)(MhAndroidSession*);
using ConfigureFunction = int32_t (*)(MhAndroidSession*, uint32_t, uint32_t);
using ResetFunction = void (*)(MhAndroidSession*);
using SetSceneFunction = void (*)(MhAndroidSession*, uint32_t);
using ProcessFunction = int32_t (*)(MhAndroidSession*, const int16_t*, uint32_t);
using NotifyFunction = void (*)(MhAndroidSession*);

void* g_library = nullptr;
AcquireFunction g_acquire = nullptr;
ReleaseFunction g_release = nullptr;
ConfigureFunction g_configure = nullptr;
ResetFunction g_reset = nullptr;
SetSceneFunction g_setScene = nullptr;
ProcessFunction g_process = nullptr;
NotifyFunction g_notify = nullptr;
std::atomic<MhAndroidSession*> g_session{nullptr};
std::atomic<MhAndroidSession*> g_hazard{nullptr};
std::atomic<MhAndroidSession*> g_configuredSession{nullptr};
std::atomic<bool> g_enabled{false};
std::atomic<int> g_scene{0};
std::atomic<int> g_sampleRate{0};
std::atomic<int> g_channelCount{0};
std::atomic<bool> g_resolutionAttempted{false};

template <typename Function>
bool ResolveSymbol(Function& destination, const char* name) {
    void* symbol = dlsym(g_library, name);
    static_assert(sizeof(destination) == sizeof(symbol), "function pointer size mismatch");
    std::memcpy(&destination, &symbol, sizeof(destination));
    return destination != nullptr;
}

bool ResolveAdapter() {
    if (g_process != nullptr) return true;
    if (g_resolutionAttempted.exchange(true, std::memory_order_acq_rel)) return false;

    g_library = dlopen("libmoonlight_haptics_android.so", RTLD_NOW | RTLD_LOCAL);
    const bool resolved = g_library != nullptr &&
        ResolveSymbol(g_acquire, "mh_android_session_acquire") &&
        ResolveSymbol(g_release, "mh_android_session_release") &&
        ResolveSymbol(g_configure, "mh_android_session_configure") &&
        ResolveSymbol(g_reset, "mh_android_session_reset") &&
        ResolveSymbol(g_setScene, "mh_android_session_set_scene") &&
        ResolveSymbol(g_process, "mh_android_session_process_i16") &&
        ResolveSymbol(g_notify, "mh_android_session_notify");
    const char* resolution_error = resolved ? "none" : dlerror();
    __android_log_print(
        resolved ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR,
        kLogTag,
        "[HAPTICS_ANDROID_ADAPTER] resolved=%s error=%s",
        resolved ? "true" : "false",
        resolution_error != nullptr ? resolution_error : "unknown");
    return resolved;
}

MhAndroidSession* ProtectSession() {
    for (;;) {
        MhAndroidSession* session = g_session.load(std::memory_order_acquire);
        g_hazard.store(session, std::memory_order_seq_cst);
        if (session == g_session.load(std::memory_order_acquire)) return session;
    }
}

void UnprotectSession() {
    g_hazard.store(nullptr, std::memory_order_release);
}

} // namespace

extern "C" {

void audio_haptics_android_adapter_set_session_handle(uint64_t handle) {
    MhAndroidSession* replacement = reinterpret_cast<MhAndroidSession*>(
        static_cast<uintptr_t>(handle));
    if (replacement == g_session.load(std::memory_order_acquire)) return;
    if (replacement != nullptr && (!ResolveAdapter() || g_acquire == nullptr)) return;
    if (replacement != nullptr) g_acquire(replacement);

    MhAndroidSession* previous = g_session.exchange(replacement, std::memory_order_acq_rel);
    g_configuredSession.store(nullptr, std::memory_order_release);
    while (previous != nullptr &&
           g_hazard.load(std::memory_order_acquire) == previous) {
        sched_yield();
    }
    if (previous != nullptr && g_release != nullptr) g_release(previous);
}

void audio_haptics_android_adapter_set_enabled(int enabled) {
    const bool requested = enabled != 0;
    const bool previous = g_enabled.exchange(requested, std::memory_order_acq_rel);
    if (requested && !previous) {
        // Renderer stop clears device state. Recreate the producer-owned engine
        // on the next PCM callback so unchanged continuous state is emitted again.
        g_configuredSession.store(nullptr, std::memory_order_release);
    }
}

void audio_haptics_android_adapter_set_scene_mode(int mode) {
    const int clamped = mode < 0 ? 0 : (mode > 2 ? 2 : mode);
    g_scene.store(clamped, std::memory_order_release);
}

void audio_haptics_android_adapter_init(int sample_rate, int channel_count) {
    g_sampleRate.store(sample_rate, std::memory_order_release);
    g_channelCount.store(channel_count, std::memory_order_release);
    g_configuredSession.store(nullptr, std::memory_order_release);
}

int audio_haptics_android_adapter_process_frame(
    const int16_t* pcm_data,
    int frame_count) {
    if (!g_enabled.load(std::memory_order_acquire) || pcm_data == nullptr || frame_count <= 0 ||
        g_process == nullptr) {
        return 0;
    }

    MhAndroidSession* session = ProtectSession();
    if (session == nullptr) {
        UnprotectSession();
        return 0;
    }

    if (g_configuredSession.load(std::memory_order_acquire) != session) {
        const int sampleRate = g_sampleRate.load(std::memory_order_acquire);
        const int channelCount = g_channelCount.load(std::memory_order_acquire);
        if (sampleRate <= 0 || channelCount <= 0 || g_configure == nullptr ||
            g_configure(session, static_cast<uint32_t>(sampleRate),
                        static_cast<uint32_t>(channelCount)) != 0) {
            UnprotectSession();
            return 0;
        }
        g_configuredSession.store(session, std::memory_order_release);
    }

    if (g_setScene != nullptr) {
        g_setScene(session, static_cast<uint32_t>(g_scene.load(std::memory_order_acquire)));
    }
    const int32_t result = g_process(
        session, pcm_data, static_cast<uint32_t>(frame_count));
    UnprotectSession();
    return static_cast<int>(result);
}

void audio_haptics_android_adapter_notify(void) {
    if (!g_enabled.load(std::memory_order_acquire) || g_notify == nullptr) return;
    MhAndroidSession* session = ProtectSession();
    if (session != nullptr) g_notify(session);
    UnprotectSession();
}

void audio_haptics_android_adapter_cleanup(void) {
    MhAndroidSession* session = ProtectSession();
    if (session != nullptr && g_reset != nullptr) g_reset(session);
    UnprotectSession();
    g_configuredSession.store(nullptr, std::memory_order_release);
    g_sampleRate.store(0, std::memory_order_release);
    g_channelCount.store(0, std::memory_order_release);
}

int audio_haptics_android_adapter_is_compiled(void) { return 1; }

} // extern "C"

#else

extern "C" {

void audio_haptics_android_adapter_set_session_handle(uint64_t handle) { (void)handle; }
void audio_haptics_android_adapter_set_enabled(int enabled) { (void)enabled; }
void audio_haptics_android_adapter_set_scene_mode(int mode) { (void)mode; }
void audio_haptics_android_adapter_init(int sample_rate, int channel_count) {
    (void)sample_rate;
    (void)channel_count;
}
int audio_haptics_android_adapter_process_frame(const int16_t* pcm_data, int frame_count) {
    (void)pcm_data;
    (void)frame_count;
    return 0;
}
void audio_haptics_android_adapter_notify(void) {}
void audio_haptics_android_adapter_cleanup(void) {}
int audio_haptics_android_adapter_is_compiled(void) { return 0; }

} // extern "C"

#endif
