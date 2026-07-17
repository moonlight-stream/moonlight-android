// SPDX-License-Identifier: GPL-3.0-or-later

#include "audio_haptics_shadow_bridge.h"

#ifndef MOONLIGHT_AUDIO_HAPTICS_SHADOW
#define MOONLIGHT_AUDIO_HAPTICS_SHADOW 0
#endif

#if MOONLIGHT_AUDIO_HAPTICS_SHADOW

#include "moonlight_haptics/audio_haptics.h"

#include <android/log.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdlib>

namespace {

constexpr const char* kLogTag = "moonlight-haptics";
constexpr uint64_t kMatchWindowUs = 50000ULL;
constexpr size_t kQueueCapacity = 16U;
constexpr size_t kOutputCapacity = 32U;

struct EventQueue {
    std::array<uint64_t, kQueueCapacity> values{};
    size_t count = 0U;

    bool Push(uint64_t timestampUs) {
        if (count == values.size()) return false;
        values[count++] = timestampUs;
        return true;
    }

    uint64_t Front() const { return values[0]; }

    void Pop() {
        for (size_t index = 1U; index < count; ++index) values[index - 1U] = values[index];
        if (count > 0U) --count;
    }
};

struct ShadowState {
    AhEngine* engine = nullptr;
    uint32_t sampleRate = 0U;
    uint32_t channelCount = 0U;
    uint64_t inputBlocks = 0U;
    uint64_t inputFrames = 0U;
    uint64_t referenceEvents = 0U;
    uint64_t candidateEvents = 0U;
    uint64_t matchedEvents = 0U;
    uint64_t referenceOnlyEvents = 0U;
    uint64_t candidateOnlyEvents = 0U;
    int64_t signedMatchDeltaUs = 0;
    uint64_t maximumAbsoluteMatchDeltaUs = 0U;
    uint64_t processErrors = 0U;
    uint64_t totalProcessTimeNs = 0U;
    uint64_t maximumProcessTimeNs = 0U;
    std::array<uint64_t, 6U> processTimeHistogram{};
    EventQueue referenceQueue{};
    EventQueue candidateQueue{};
    uint64_t lastLogFrame = 0U;
    bool logDue = false;
};

std::atomic<bool> g_requestedEnabled{false};
std::atomic<int> g_requestedSceneMode{0};
ShadowState g_state{};

void ResetState() {
    AhEngine* engine = g_state.engine;
    g_state = ShadowState{};
    g_state.engine = engine;
}

void RecordLatency(uint64_t elapsedNs) {
    g_state.totalProcessTimeNs += elapsedNs;
    g_state.maximumProcessTimeNs = std::max(g_state.maximumProcessTimeNs, elapsedNs);
    const uint64_t elapsedUs = elapsedNs / 1000ULL;
    size_t bucket = 5U;
    if (elapsedUs <= 50ULL) bucket = 0U;
    else if (elapsedUs <= 100ULL) bucket = 1U;
    else if (elapsedUs <= 200ULL) bucket = 2U;
    else if (elapsedUs <= 500ULL) bucket = 3U;
    else if (elapsedUs <= 1000ULL) bucket = 4U;
    ++g_state.processTimeHistogram[bucket];
}

void MatchPending(uint64_t nowUs) {
    while (g_state.referenceQueue.count > 0U && g_state.candidateQueue.count > 0U) {
        const uint64_t reference = g_state.referenceQueue.Front();
        const uint64_t candidate = g_state.candidateQueue.Front();
        const int64_t delta = static_cast<int64_t>(candidate) - static_cast<int64_t>(reference);
        const uint64_t absoluteDelta = static_cast<uint64_t>(std::llabs(delta));
        if (absoluteDelta <= kMatchWindowUs) {
            ++g_state.matchedEvents;
            g_state.signedMatchDeltaUs += delta;
            g_state.maximumAbsoluteMatchDeltaUs =
                std::max(g_state.maximumAbsoluteMatchDeltaUs, absoluteDelta);
            g_state.referenceQueue.Pop();
            g_state.candidateQueue.Pop();
        } else if (reference < candidate) {
            ++g_state.referenceOnlyEvents;
            g_state.referenceQueue.Pop();
        } else {
            ++g_state.candidateOnlyEvents;
            g_state.candidateQueue.Pop();
        }
    }

    const uint64_t expiry = nowUs > kMatchWindowUs ? nowUs - kMatchWindowUs : 0U;
    while (g_state.referenceQueue.count > 0U && g_state.referenceQueue.Front() < expiry) {
        ++g_state.referenceOnlyEvents;
        g_state.referenceQueue.Pop();
    }
    while (g_state.candidateQueue.count > 0U && g_state.candidateQueue.Front() < expiry) {
        ++g_state.candidateOnlyEvents;
        g_state.candidateQueue.Pop();
    }
}

void AddReferenceEvent(uint64_t timestampUs) {
    ++g_state.referenceEvents;
    if (!g_state.referenceQueue.Push(timestampUs)) {
        ++g_state.referenceOnlyEvents;
        g_state.referenceQueue.Pop();
        (void)g_state.referenceQueue.Push(timestampUs);
    }
}

void AddCandidateEvent(uint64_t timestampUs) {
    ++g_state.candidateEvents;
    if (!g_state.candidateQueue.Push(timestampUs)) {
        ++g_state.candidateOnlyEvents;
        g_state.candidateQueue.Pop();
        (void)g_state.candidateQueue.Push(timestampUs);
    }
}

} // namespace

extern "C" {

void audio_haptics_shadow_set_enabled(int enabled) {
    g_requestedEnabled.store(enabled != 0, std::memory_order_release);
}

void audio_haptics_shadow_set_scene_mode(int mode) {
    g_requestedSceneMode.store(std::max(0, std::min(2, mode)), std::memory_order_release);
}

void audio_haptics_shadow_init(int sample_rate, int channel_count) {
    if (g_state.engine != nullptr) {
        ah_destroy(g_state.engine);
        g_state.engine = nullptr;
    }
    ResetState();
    g_state.sampleRate = sample_rate > 0 ? static_cast<uint32_t>(sample_rate) : 0U;
    g_state.channelCount = channel_count > 0 ? static_cast<uint32_t>(channel_count) : 0U;
    if (!g_requestedEnabled.load(std::memory_order_acquire)) return;

    AhConfig config{};
    AhStatus status = ah_config_init(&config, g_state.sampleRate, g_state.channelCount);
    if (status == AH_STATUS_OK) {
        config.requested_scene = static_cast<uint32_t>(
            g_requestedSceneMode.load(std::memory_order_acquire));
        status = ah_create(&config, &g_state.engine);
    }
    if (status != AH_STATUS_OK || g_state.engine == nullptr) ++g_state.processErrors;
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "[HAPTICS_SHADOW] platform=android compiled=true ready=%s sdk=%s params=%s rate=%u channels=%u scene=%d",
        g_state.engine != nullptr ? "true" : "false",
        ah_get_version_string(),
        ah_get_parameter_set_version(),
        g_state.sampleRate,
        g_state.channelCount,
        g_requestedSceneMode.load(std::memory_order_relaxed));
}

void audio_haptics_shadow_process_frame(
    const int16_t* pcm_data,
    int frame_count,
    int reference_event) {
    if (!g_requestedEnabled.load(std::memory_order_acquire) ||
        g_state.engine == nullptr || pcm_data == nullptr || frame_count <= 0) {
        return;
    }

    const uint32_t frames = static_cast<uint32_t>(frame_count);
    const uint64_t firstSampleTimeUs =
        g_state.inputFrames * 1000000ULL / g_state.sampleRate;
    const uint64_t endTimeUs =
        (g_state.inputFrames + frames) * 1000000ULL / g_state.sampleRate;
    AhProcessInput input{};
    input.struct_size = AH_PROCESS_INPUT_V1_SIZE;
    input.interleaved_pcm = pcm_data;
    input.frame_count = frames;
    input.first_sample_time_us = firstSampleTimeUs;
    std::array<AhHapticFrame, kOutputCapacity> outputs{};
    uint32_t outputCount = 0U;

    const uint32_t requiredCapacity = ah_get_max_output_frames(g_state.engine, frames);
    const auto start = std::chrono::steady_clock::now();
    AhStatus status = AH_STATUS_BUFFER_TOO_SMALL;
    if (requiredCapacity <= outputs.size()) {
        status = ah_process_i16(
            g_state.engine,
            &input,
            outputs.data(),
            static_cast<uint32_t>(outputs.size()),
            &outputCount);
    }
    const auto stop = std::chrono::steady_clock::now();
    const uint64_t elapsedNs = static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(stop - start).count());

    ++g_state.inputBlocks;
    g_state.inputFrames += frames;
    RecordLatency(elapsedNs);
    if (status != AH_STATUS_OK && status != AH_STATUS_OUTPUT_AVAILABLE) {
        ++g_state.processErrors;
    } else {
        for (uint32_t index = 0U; index < outputCount; ++index) {
            const uint32_t eventFlags = AH_FRAME_TRANSIENT |
                AH_FRAME_CONTINUOUS_CHANGED | AH_FRAME_STOP;
            if ((outputs[index].flags & eventFlags) != 0U) {
                AddCandidateEvent(outputs[index].timestamp_us);
            }
        }
    }
    if (reference_event != 0) AddReferenceEvent(endTimeUs);
    MatchPending(endTimeUs);
    if (g_state.inputFrames - g_state.lastLogFrame >= g_state.sampleRate) {
        g_state.lastLogFrame = g_state.inputFrames;
        g_state.logDue = true;
    }

}

void audio_haptics_shadow_maybe_log(void) {
    if (!g_state.logDue || g_state.inputBlocks == 0U) return;
    g_state.logDue = false;
    const uint64_t meanUs =
        g_state.totalProcessTimeNs / g_state.inputBlocks / 1000ULL;
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "[HAPTICS_SHADOW] platform=android blocks=%llu frames=%llu ref=%llu sdk=%llu "
        "matched=%llu refOnly=%llu sdkOnly=%llu pending=%zu/%zu totalUs=%llu meanUs=%llu maxUs=%llu errors=%llu",
        static_cast<unsigned long long>(g_state.inputBlocks),
        static_cast<unsigned long long>(g_state.inputFrames),
        static_cast<unsigned long long>(g_state.referenceEvents),
        static_cast<unsigned long long>(g_state.candidateEvents),
        static_cast<unsigned long long>(g_state.matchedEvents),
        static_cast<unsigned long long>(g_state.referenceOnlyEvents),
        static_cast<unsigned long long>(g_state.candidateOnlyEvents),
        g_state.referenceQueue.count,
        g_state.candidateQueue.count,
        static_cast<unsigned long long>(g_state.totalProcessTimeNs / 1000ULL),
        static_cast<unsigned long long>(meanUs),
        static_cast<unsigned long long>(g_state.maximumProcessTimeNs / 1000ULL),
        static_cast<unsigned long long>(g_state.processErrors));
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "[HAPTICS_SHADOW] latencyBucketsUs le50=%llu le100=%llu le200=%llu le500=%llu "
        "le1000=%llu gt1000=%llu matchDeltaSumUs=%lld matchDeltaAbsMaxUs=%llu",
        static_cast<unsigned long long>(g_state.processTimeHistogram[0]),
        static_cast<unsigned long long>(g_state.processTimeHistogram[1]),
        static_cast<unsigned long long>(g_state.processTimeHistogram[2]),
        static_cast<unsigned long long>(g_state.processTimeHistogram[3]),
        static_cast<unsigned long long>(g_state.processTimeHistogram[4]),
        static_cast<unsigned long long>(g_state.processTimeHistogram[5]),
        static_cast<long long>(g_state.signedMatchDeltaUs),
        static_cast<unsigned long long>(g_state.maximumAbsoluteMatchDeltaUs));
}

void audio_haptics_shadow_cleanup(void) {
    if (g_state.inputBlocks > 0U) {
        g_state.logDue = true;
        audio_haptics_shadow_maybe_log();
    }
    if (g_state.engine != nullptr) {
        ah_destroy(g_state.engine);
        g_state.engine = nullptr;
    }
    ResetState();
}

int audio_haptics_shadow_is_compiled(void) { return 1; }

} // extern "C"

#else

extern "C" {

void audio_haptics_shadow_set_enabled(int enabled) { (void)enabled; }
void audio_haptics_shadow_set_scene_mode(int mode) { (void)mode; }
void audio_haptics_shadow_init(int sample_rate, int channel_count) {
    (void)sample_rate;
    (void)channel_count;
}
void audio_haptics_shadow_process_frame(
    const int16_t* pcm_data,
    int frame_count,
    int reference_event) {
    (void)pcm_data;
    (void)frame_count;
    (void)reference_event;
}
void audio_haptics_shadow_maybe_log(void) {}
void audio_haptics_shadow_cleanup(void) {}
int audio_haptics_shadow_is_compiled(void) { return 0; }

} // extern "C"

#endif
