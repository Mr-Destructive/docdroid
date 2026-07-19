package com.docdroid.engine

/**
 * JNI bridge to the Cactus Engine native library (libcactus_engine.so).
 * Maps directly to the C API in cactus_engine.h.
 *
 * Build: cactus build --android
 * Copy: android/libcactus_engine.so → app/src/main/jniLibs/arm64-v8a/
 */
object CactusJNI {

    init {
        System.loadLibrary("cactus_engine")
    }

    /**
     * Initialize a Cactus model from a CQ4 bundle directory.
     * @param modelPath Path to the directory containing model weights
     * @param corpusDir Optional path to corpus for RAG (null if unused)
     * @param cacheIndex Whether to cache the index
     * @return Model handle (pointer), or 0 on failure
     */
    external fun nativeInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long

    /**
     * Destroy a model and free resources.
     */
    external fun nativeDestroy(handle: Long)

    /**
     * Generate a completion using the model.
     * @param handle Model handle from nativeInit
     * @param messagesJson JSON array of messages: [{"role":"user","content":"..."}]
     * @param responseBuffer Buffer to write the response into
     * @param optionsJson Optional generation options (null for defaults)
     * @param toolsJson JSON array of tool definitions
     * @param callback Optional token-by-token callback (null if unused)
     * @return 0 on success, negative on error
     */
    external fun nativeComplete(
        handle: Long,
        messagesJson: String,
        responseBuffer: ByteArray,
        optionsJson: String?,
        toolsJson: String?,
        callback: Any?,
        pcmData: ByteArray?,
        pcmDataSize: Int
    ): Int

    /**
     * Tokenize text and return token IDs.
     */
    external fun nativeTokenize(handle: Long, text: String): IntArray

    /**
     * Get model info as JSON string.
     */
    external fun nativeModelInfo(handle: Long): String
}
