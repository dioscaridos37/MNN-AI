#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

#define LOG_TAG "DIX-AI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_context = nullptr;
static bool g_backend_ready = false;
static int g_context_size = 4096;

static void throw_java(JNIEnv * env, const std::string & message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, message.c_str());
}

static bool decode_tokens(
        llama_context * context,
        llama_batch_ext * batch,
        const std::vector<llama_token> & tokens,
        llama_pos start,
        bool final_logits) {
    constexpr int32_t chunk_size = 256;
    for (size_t offset = 0; offset < tokens.size(); offset += chunk_size) {
        llama_batch_ext_clear(batch);
        const size_t count = std::min<size_t>(chunk_size, tokens.size() - offset);
        for (size_t i = 0; i < count; ++i) {
            const int32_t index = llama_batch_ext_add_token(batch, 0, tokens[offset + i]);
            if (index < 0) return false;
            const llama_pos position = start + static_cast<llama_pos>(offset + i);
            if (!llama_batch_ext_set_pos(batch, index, &position)) return false;
            const bool output = final_logits && offset + i == tokens.size() - 1;
            llama_batch_ext_set_output_logits(batch, index, output);
        }
        if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) return false;
    }
    return true;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_dioscaridos_mnnai_LlamaEngine_nativeLoad(
        JNIEnv * env,
        jobject,
        jstring jpath,
        jint threads,
        jint context_size) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model && g_context) return JNI_TRUE;

    const char * path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return JNI_FALSE;

    if (!g_backend_ready) {
        llama_backend_init();
        g_backend_ready = true;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(jpath, path);

    if (!g_model) {
        throw_java(env, "llama.cpp не смог загрузить GGUF-модель.");
        return JNI_FALSE;
    }

    llama_context_params params = llama_context_default_params();
    g_context_size = std::max(1024, std::min(8192, static_cast<int>(context_size)));
    const int cpu_threads = std::max(2, std::min(8, static_cast<int>(threads)));
    params.n_ctx = static_cast<uint32_t>(g_context_size);
    params.n_batch = 512;
    params.n_ubatch = 256;
    params.n_threads = cpu_threads;
    params.n_threads_batch = cpu_threads;
    params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;

    g_context = llama_init_from_model(g_model, params);
    if (!g_context) {
        llama_model_free(g_model);
        g_model = nullptr;
        throw_java(env, "Недостаточно памяти для запуска модели. Закройте другие приложения.");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_dioscaridos_mnnai_LlamaEngine_nativeGenerate(
        JNIEnv * env,
        jobject,
        jstring jprompt,
        jint max_tokens,
        jfloat temperature) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context) {
        throw_java(env, "Модель не загружена.");
        return nullptr;
    }

    const char * prompt_chars = env->GetStringUTFChars(jprompt, nullptr);
    if (!prompt_chars) return nullptr;
    const std::string prompt(prompt_chars);
    env->ReleaseStringUTFChars(jprompt, prompt_chars);

    llama_memory_clear(llama_get_memory(g_context), true);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    int32_t token_count = llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            nullptr, 0, true, true);
    if (token_count >= 0) {
        throw_java(env, "Не удалось определить размер токенов запроса.");
        return nullptr;
    }
    token_count = -token_count;
    if (token_count <= 0) {
        throw_java(env, "Пустой запрос после токенизации.");
        return nullptr;
    }

    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    const int32_t actual = llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), token_count, true, true);
    if (actual < 0) {
        throw_java(env, "Не удалось токенизировать запрос.");
        return nullptr;
    }
    tokens.resize(static_cast<size_t>(actual));

    const int limit = std::max(64, g_context_size - std::max(64, static_cast<int>(max_tokens)) - 8);
    if (static_cast<int>(tokens.size()) > limit) {
        const int keep_head = std::min(128, limit / 4);
        const int keep_tail = limit - keep_head;
        std::vector<llama_token> trimmed;
        trimmed.insert(trimmed.end(), tokens.begin(), tokens.begin() + keep_head);
        trimmed.insert(trimmed.end(), tokens.end() - keep_tail, tokens.end());
        tokens.swap(trimmed);
    }

    llama_batch_ext * batch = llama_batch_ext_init(g_context);
    if (!batch) {
        throw_java(env, "Не удалось создать пакет токенов.");
        return nullptr;
    }

    std::string output;
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    if (!sampler) {
        llama_batch_ext_free(batch);
        throw_java(env, "Не удалось создать генератор текста.");
        return nullptr;
    }

    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(std::max(0.1f, std::min(1.5f, temperature))));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    bool ok = decode_tokens(g_context, batch, tokens, 0, true);
    if (!ok) {
        llama_sampler_free(sampler);
        llama_batch_ext_free(batch);
        throw_java(env, "Ошибка обработки входного текста llama.cpp.");
        return nullptr;
    }

    const int generation_limit = std::max(1, std::min(1024, static_cast<int>(max_tokens)));
    std::vector<char> piece(512);
    for (int i = 0; i < generation_limit; ++i) {
        const llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        int32_t piece_size = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, false);
        if (piece_size < 0) {
            piece.resize(static_cast<size_t>(-piece_size));
            piece_size = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, false);
        }
        if (piece_size > 0) output.append(piece.data(), static_cast<size_t>(piece_size));

        if (i + 1 < generation_limit) {
            llama_batch_ext_clear(batch);
            const int32_t index = llama_batch_ext_add_token(batch, 0, token);
            if (index < 0) break;
            const llama_pos position = static_cast<llama_pos>(tokens.size() + i);
            llama_batch_ext_set_pos(batch, index, &position);
            llama_batch_ext_set_output_logits(batch, index, true);
            if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) break;
        }
    }

    llama_sampler_free(sampler);
    llama_batch_ext_free(batch);
    return env->NewStringUTF(output.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_dioscaridos_mnnai_LlamaEngine_nativeRelease(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    if (g_backend_ready) {
        llama_backend_free();
        g_backend_ready = false;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_dioscaridos_mnnai_LlamaEngine_nativeSetThreads(JNIEnv *, jobject, jint threads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context) {
        const int count = std::max(2, std::min(8, static_cast<int>(threads)));
        llama_set_n_threads(g_context, count, count);
    }
}
