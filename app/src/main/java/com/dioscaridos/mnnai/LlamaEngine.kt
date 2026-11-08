package com.dioscaridos.mnnai

internal class LlamaEngine {
    @Volatile
    var isLoaded: Boolean = false
        private set

    init {
        System.loadLibrary("dixai")
    }

    fun load(path: String, threads: Int, context: Int): Boolean {
        isLoaded = nativeLoad(path, threads, context)
        return isLoaded
    }

    fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
        check(isLoaded) { "Модель не загружена" }
        return nativeGenerate(prompt, maxTokens, temperature)
    }

    fun close() {
        if (isLoaded) {
            nativeRelease()
            isLoaded = false
        }
    }

    private external fun nativeLoad(path: String, threads: Int, contextSize: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeRelease()
}
