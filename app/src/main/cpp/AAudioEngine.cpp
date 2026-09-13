#include <aaudio/AAudio.h>
#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <time.h>
#include <vector>

#define TAG "MikuAAudio"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
struct Engine {
    AAudioStream* stream = nullptr;
    std::vector<uint8_t> ring;
    size_t readPos = 0;
    size_t writePos = 0;
    size_t used = 0;
    size_t frameBytes = 4;
    int sampleRate = 0;
    int channels = 0;
    int format = 0;
    int deviceId = AAUDIO_UNSPECIFIED;
    bool exclusive = false;
    bool started = false;
    std::atomic<bool> running{false};
    std::atomic<long> underruns{0};
    std::mutex mutex;
} engine;

int bytesPerSample(int format) {
    if (format == 2 || format == 4) return 4;
    if (format == 3) return 3;
    return 2;
}

aaudio_format_t toAAudioFormat(int format) {
    switch (format) {
        case 2: return AAUDIO_FORMAT_PCM_FLOAT;
        case 3: return AAUDIO_FORMAT_PCM_I24_PACKED;
        case 4: return AAUDIO_FORMAT_PCM_I32;
        default: return AAUDIO_FORMAT_PCM_I16;
    }
}

int fromAAudioFormat(aaudio_format_t format) {
    switch (format) {
        case AAUDIO_FORMAT_PCM_FLOAT: return 2;
        case AAUDIO_FORMAT_PCM_I24_PACKED: return 3;
        case AAUDIO_FORMAT_PCM_I32: return 4;
        case AAUDIO_FORMAT_PCM_I16: return 1;
        default: return 0;
    }
}

size_t freeBytesLocked() { return engine.ring.size() - engine.used; }

void writeLocked(const uint8_t* source, size_t count) {
    count = std::min(count, freeBytesLocked());
    const size_t first = std::min(count, engine.ring.size() - engine.writePos);
    std::memcpy(engine.ring.data() + engine.writePos, source, first);
    std::memcpy(engine.ring.data(), source + first, count - first);
    engine.writePos = (engine.writePos + count) % engine.ring.size();
    engine.used += count;
}

size_t readLocked(uint8_t* destination, size_t count) {
    count = std::min(count, engine.used);
    const size_t first = std::min(count, engine.ring.size() - engine.readPos);
    std::memcpy(destination, engine.ring.data() + engine.readPos, first);
    std::memcpy(destination + first, engine.ring.data(), count - first);
    engine.readPos = (engine.readPos + count) % engine.ring.size();
    engine.used -= count;
    return count;
}

aaudio_data_callback_result_t dataCallback(AAudioStream*, void*, void* audioData, int32_t frames) {
    std::lock_guard<std::mutex> lock(engine.mutex);
    const size_t required = static_cast<size_t>(frames) * engine.frameBytes;
    const size_t supplied = engine.running ? readLocked(static_cast<uint8_t*>(audioData), required) : 0;
    if (supplied < required) {
        std::memset(static_cast<uint8_t*>(audioData) + supplied, 0, required - supplied);
        if (engine.running) engine.underruns.fetch_add(1, std::memory_order_relaxed);
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void errorCallback(AAudioStream*, void*, aaudio_result_t error) {
    engine.running.store(false, std::memory_order_release);
    LOGE("AAudio disconnected with error %d", error);
}

aaudio_result_t buildAndOpen(int deviceId, int rate, int channels, int format, int frames, aaudio_sharing_mode_t mode, AAudioStream** output) {
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) return result;
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setDeviceId(builder, deviceId);
    AAudioStreamBuilder_setSampleRate(builder, rate);
    AAudioStreamBuilder_setChannelCount(builder, channels);
    AAudioStreamBuilder_setFormat(builder, toAAudioFormat(format));
    AAudioStreamBuilder_setFramesPerDataCallback(builder, frames);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, mode);
    AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);
    AAudioStreamBuilder_setDataCallback(builder, dataCallback, nullptr);
    AAudioStreamBuilder_setErrorCallback(builder, errorCallback, nullptr);
    result = AAudioStreamBuilder_openStream(builder, output);
    AAudioStreamBuilder_delete(builder);
    return result;
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_prismora_player_audio_AudioEngine_nativeOpen(JNIEnv*, jobject, jint deviceId, jint rate, jint channels, jint format, jint frames) {
    AAudioStream* stream = nullptr;
    aaudio_result_t result = buildAndOpen(deviceId, rate, channels, format, frames, AAUDIO_SHARING_MODE_EXCLUSIVE, &stream);
    bool exclusive = result == AAUDIO_OK;
    if (result != AAUDIO_OK) {
        result = buildAndOpen(deviceId, rate, channels, format, frames, AAUDIO_SHARING_MODE_SHARED, &stream);
    }
    if (result != AAUDIO_OK || stream == nullptr) return result;

    std::lock_guard<std::mutex> lock(engine.mutex);
    engine.stream = stream;
    engine.sampleRate = AAudioStream_getSampleRate(stream);
    engine.channels = AAudioStream_getChannelCount(stream);
    engine.format = fromAAudioFormat(AAudioStream_getFormat(stream));
    engine.deviceId = AAudioStream_getDeviceId(stream);
    engine.exclusive = exclusive && AAudioStream_getSharingMode(stream) == AAUDIO_SHARING_MODE_EXCLUSIVE;
    engine.frameBytes = static_cast<size_t>(engine.channels) * bytesPerSample(engine.format);
    engine.ring.assign(std::max<size_t>(engine.sampleRate * engine.frameBytes / 2, frames * engine.frameBytes * 64), 0);
    engine.readPos = engine.writePos = engine.used = 0;
    engine.started = false;
    engine.underruns.store(0, std::memory_order_relaxed);
    engine.running.store(true, std::memory_order_release);
    return AAUDIO_OK;
}

extern "C" JNIEXPORT void JNICALL
Java_com_prismora_player_audio_AudioEngine_nativeClose(JNIEnv*, jobject) {
    AAudioStream* stream = nullptr;
    {
        std::lock_guard<std::mutex> lock(engine.mutex);
        engine.running.store(false, std::memory_order_release);
        stream = engine.stream;
        engine.stream = nullptr;
        engine.started = false;
    }
    if (stream != nullptr) {
        AAudioStream_requestStop(stream);
        AAudioStream_close(stream);
    }
    std::lock_guard<std::mutex> lock(engine.mutex);
    engine.ring.clear();
    engine.readPos = engine.writePos = engine.used = 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_prismora_player_audio_AudioEngine_nativeWritePcm(JNIEnv* env, jobject, jbyteArray data, jint count) {
    if (data == nullptr || count <= 0) return 0;
    jbyte* source = env->GetByteArrayElements(data, nullptr);
    size_t written = 0;
    while (written < static_cast<size_t>(count) && engine.running.load(std::memory_order_acquire)) {
        size_t amount = 0;
        AAudioStream* streamToStart = nullptr;
        {
            std::lock_guard<std::mutex> lock(engine.mutex);
            if (!engine.running || engine.ring.empty()) break;
            amount = std::min(static_cast<size_t>(count) - written, freeBytesLocked());
            if (amount > 0) {
                writeLocked(reinterpret_cast<uint8_t*>(source) + written, amount);
                written += amount;
                if (!engine.started && engine.stream != nullptr) {
                    engine.started = true;
                    streamToStart = engine.stream;
                }
            }
        }
        if (streamToStart != nullptr) {
            const aaudio_result_t result = AAudioStream_requestStart(streamToStart);
            if (result != AAUDIO_OK) {
                engine.running.store(false, std::memory_order_release);
                LOGE("AAudio start failed: %d", result);
                break;
            }
        }
        if (amount == 0) {
            timespec delay{0, 2'000'000};
            nanosleep(&delay, nullptr);
        }
    }
    env->ReleaseByteArrayElements(data, source, JNI_ABORT);
    return static_cast<jint>(written);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_prismora_player_audio_AudioEngine_nativeFlush(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(engine.mutex);
    engine.readPos = engine.writePos = engine.used = 0;
    return AAUDIO_OK;
}

extern "C" JNIEXPORT jlong JNICALL Java_com_prismora_player_audio_AudioEngine_nativeGetUnderruns(JNIEnv*, jobject) { return engine.underruns.load(); }
extern "C" JNIEXPORT jint JNICALL Java_com_prismora_player_audio_AudioEngine_nativeActualDeviceId(JNIEnv*, jobject) { std::lock_guard<std::mutex> lock(engine.mutex); return engine.deviceId; }
extern "C" JNIEXPORT jint JNICALL Java_com_prismora_player_audio_AudioEngine_nativeActualSampleRate(JNIEnv*, jobject) { std::lock_guard<std::mutex> lock(engine.mutex); return engine.sampleRate; }
extern "C" JNIEXPORT jint JNICALL Java_com_prismora_player_audio_AudioEngine_nativeActualChannelCount(JNIEnv*, jobject) { std::lock_guard<std::mutex> lock(engine.mutex); return engine.channels; }
extern "C" JNIEXPORT jint JNICALL Java_com_prismora_player_audio_AudioEngine_nativeActualFormat(JNIEnv*, jobject) { std::lock_guard<std::mutex> lock(engine.mutex); return engine.format; }
extern "C" JNIEXPORT jboolean JNICALL Java_com_prismora_player_audio_AudioEngine_nativeExclusive(JNIEnv*, jobject) { std::lock_guard<std::mutex> lock(engine.mutex); return engine.exclusive; }
extern "C" JNIEXPORT jint JNICALL Java_com_prismora_player_audio_AudioEngine_nativeUsedBytes(JNIEnv*, jobject) { std::lock_guard<std::mutex> lock(engine.mutex); return static_cast<jint>(engine.used); }

extern "C" JNIEXPORT jint JNICALL
Java_com_prismora_player_audio_AudioEngine_nativePause(JNIEnv*, jobject) {
    AAudioStream* stream = nullptr;
    { std::lock_guard<std::mutex> lock(engine.mutex); if (engine.started) stream = engine.stream; }
    return stream != nullptr ? AAudioStream_requestPause(stream) : AAUDIO_ERROR_INVALID_STATE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_prismora_player_audio_AudioEngine_nativeResume(JNIEnv*, jobject) {
    AAudioStream* stream = nullptr;
    { std::lock_guard<std::mutex> lock(engine.mutex); if (engine.started) stream = engine.stream; }
    return stream != nullptr ? AAudioStream_requestStart(stream) : AAUDIO_ERROR_INVALID_STATE;
}
