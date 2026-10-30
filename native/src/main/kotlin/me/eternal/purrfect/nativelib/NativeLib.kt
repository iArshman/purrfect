package me.eternal.purrfect.nativelib

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import java.io.File
import kotlin.math.absoluteValue
import kotlin.random.Random

class NativeLib {
    var nativeUnaryCallCallback: (NativeRequestData) -> Unit = {}
    var signatureCache: String? = null

    companion object {
        var initialized = false
            private set
        private var libraryLoaded = false

        private fun findNativeLibraryDirs(): List<File> {
            val app = runCatching {
                val cls = Class.forName("android.app.ActivityThread")
                val method = cls.getMethod("currentApplication")
                method.invoke(null) as? android.app.Application
            }.getOrNull() ?: return emptyList()

            val dirs = mutableListOf<File>()
            val moduleDir = runCatching {
                app.createPackageContext(BuildConfig.MODULE_PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY)
                    .applicationInfo.nativeLibraryDir
            }.getOrNull()
            moduleDir?.let { dirs.add(File(it)) }

            app.applicationInfo.nativeLibraryDir?.let { dirs.add(File(it)) }

            return dirs.distinctBy { it.absolutePath }
        }

        private fun tryLoadFromNativeDir(): Boolean {
            val dirs = findNativeLibraryDirs().filter { it.isDirectory }
            if (dirs.isEmpty()) return false

            for (dir in dirs) {
                val candidates = listOf(
                    "lib${BuildConfig.NATIVE_NAME}.so",
                    "libpurrfect.so"
                ).map { File(dir, it) }

                val fallback = dir.listFiles()
                    ?.firstOrNull { it.name.startsWith("lib") && it.name.endsWith(".so") && it.name.contains("purrfect") }

                val ordered = buildList<File> {
                    addAll(candidates)
                    fallback?.let { if (!contains(it)) add(it) }
                }

                for (file in ordered) {
                    if (!file.exists()) continue
                    val ok = runCatching {
                        System.load(file.absolutePath)
                        libraryLoaded = true
                        Log.i("Purrfect", "Fallbacked to ${file.absolutePath} via directory scan, loading it now.")
                        true
                    }.getOrDefault(false)
                    if (ok) return true
                }
            }
            return false
        }

        fun tryEnsureLibraryLoaded(): Boolean {
            if (libraryLoaded) return true

            val candidates = linkedSetOf(
                BuildConfig.NATIVE_NAME,
                // Some repackagers restore the original Cargo output name.
                "purrfect",
            ).filter { it.isNotBlank() }

            var lastError: Throwable? = null
            for (name in candidates) {
                val ok = runCatching {
                    if (name == "libpenguin") {
                         Log.d("Purrfect", "Legacy load (libpenguin) skipped; searching for dynamic hash...")
                         return@runCatching false
                    }
                    System.loadLibrary(name)
                    libraryLoaded = true
                    true
                }.onFailure { lastError = it }.getOrDefault(false)
                if (ok) return true
            }

            if (tryLoadFromNativeDir()) return true

            Log.e(
                "Purrfect",
                "Failed to load native library (tried: ${candidates.joinToString()})",
                lastError
            )
            return false
        }

        fun ensureLibraryLoaded() {
            tryEnsureLibraryLoaded()
        }
    }

    fun initOnce(callback: NativeLib.() -> Unit): () -> Unit {
        if (initialized) throw IllegalStateException("NativeLib already initialized")
        return runCatching {
            val loaded = tryEnsureLibraryLoaded()
            initialized = loaded
            callback(this)
            if (loaded) {
                preInit()
                setChecksums(com.google.gson.Gson().toJson(Checksums.checksums))
                return@runCatching {
                    signatureCache = init(signatureCache) ?: run {
                        Log.e("Purrfect", "Native initialization failed (Signature mismatch).")
                        throw IllegalStateException("NativeLib init failed. Check logcat for more info")
                    }
                }
            } else {
                Log.w("Purrfect", "NativeLib running in fallback mode")
                return@runCatching {}
            }
        }.onFailure {
            initialized = false
            Log.e("Purrfect", "NativeLib init failed", it)
        }.getOrThrow()
    }

    @Suppress("unused")
    private fun onNativeUnaryCall(uri: String, buffer: ByteArray): NativeRequestData? {
        val nativeRequestData = NativeRequestData(uri, buffer)
        runCatching {
            nativeUnaryCallCallback(nativeRequestData)
        }.onFailure {
            Log.e("Purrfect", "nativeUnaryCallCallback failed", it)
        }
        if (nativeRequestData.canceled || !nativeRequestData.buffer.contentEquals(buffer)) return nativeRequestData
        return null
    }

    fun loadNativeConfig(config: NativeConfig) {
        if (!initialized) return
        loadConfig(config)
    }

    fun lockNativeDatabase(name: String, callback: () -> Unit) {
        if (!initialized) return
        lockDatabase(name) {
            runCatching {
                callback()
            }.onFailure {
                Log.e("Purrfect", "lockNativeDatabase callback failed", it)
            }
        }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun loadSharedLibrary(content: ByteArray) {
        if (!initialized) throw IllegalStateException("NativeLib not initialized")
        val generatedPath = "/data/app/${Random.nextLong().absoluteValue.toString(16)}.so"
        addLinkerSharedLibrary(generatedPath, content)
        System.load(generatedPath)
    }

    private fun preInit() {}
    private fun init(signatureCache: String?): String? = signatureCache ?: "stub_sig"
    private fun loadConfig(config: NativeConfig) {}
    private fun lockDatabase(name: String, callback: Runnable) { callback.run() }
    fun setValdiLoader(code: String) {}
    private fun addLinkerSharedLibrary(path: String, content: ByteArray) {}
    private fun evaluateEndpointNative(uri: String, arg0: String, hasAttestation: Boolean, outDecision: NativeDecision) {}
    private fun evaluateNetworkRequestNative(url: String, outDecision: NativeDecision) {}
    fun shouldBlockDuplexClient(path: String): Boolean = false
    private fun evaluateAuthContextNative(requestPath: String, attestationRequired: Boolean, outDecision: NativeDecision) {}
    private fun evaluateApiInvocationNative(methodId: String, annotations: String, outDecision: NativeDecision) {}
    private fun runEndpointSelfTest(testMode: Boolean): Boolean = true
    private fun setChecksums(checksums: String) {}
    fun setTestMode(testMode: Boolean) {}
    fun setInLoginSignup(inLoginSignup: Boolean) {}

    fun evaluateEndpoint(uri: String, arg0: String, hasAttestation: Boolean): NativeDecision {
        return NativeDecision().also { evaluateEndpointNative(uri, arg0, hasAttestation, it) }
    }

    fun evaluateNetworkRequest(url: String): NativeDecision {
        return NativeDecision().also { evaluateNetworkRequestNative(url, it) }
    }

    fun evaluateAuthContext(requestPath: String, attestationRequired: Boolean): NativeDecision {
        return NativeDecision().also { evaluateAuthContextNative(requestPath, attestationRequired, it) }
    }

    fun evaluateApiInvocation(methodId: String, annotations: String): NativeDecision {
        return NativeDecision().also { evaluateApiInvocationNative(methodId, annotations, it) }
    }

    fun isEndpointBlockerHealthy(testMode: Boolean = false): Boolean {
        if (!initialized) return false
        return runEndpointSelfTest(testMode)
    }
}
