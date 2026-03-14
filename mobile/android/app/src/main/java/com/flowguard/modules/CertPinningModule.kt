package com.flowguard.modules

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import okhttp3.CertificatePinner
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@ReactModule(name = CertPinningModule.NAME)
class CertPinningModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "FlowGuardCertPinning"
        private const val HOST = "api.flowguard.fr"
        private const val PIN_1 = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        private const val PIN_2 = "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
    }

    private val client: OkHttpClient by lazy {
        val certificatePinner = CertificatePinner.Builder()
            .add(HOST, PIN_1)
            .add(HOST, PIN_2)
            .build()

        OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun secureFetch(url: String, options: ReadableMap, promise: Promise) {
        try {
            val method = if (options.hasKey("method")) options.getString("method") ?: "GET" else "GET"
            val headersMap = mutableMapOf<String, String>()

            if (options.hasKey("headers")) {
                val headers = options.getMap("headers")
                headers?.let { map ->
                    val iterator = map.keySetIterator()
                    while (iterator.hasNextKey()) {
                        val key = iterator.nextKey()
                        val value = map.getString(key)
                        if (value != null) {
                            headersMap[key] = value
                        }
                    }
                }
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .headers(headersMap.toHeaders())

            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> {
                    val body = if (options.hasKey("body")) options.getString("body") ?: "" else ""
                    val mediaType = "application/json".toMediaTypeOrNull()
                    requestBuilder.post(body.toRequestBody(mediaType))
                }
                "PUT" -> {
                    val body = if (options.hasKey("body")) options.getString("body") ?: "" else ""
                    val mediaType = "application/json".toMediaTypeOrNull()
                    requestBuilder.put(body.toRequestBody(mediaType))
                }
                "DELETE" -> requestBuilder.delete()
                else -> requestBuilder.get()
            }

            Thread {
                try {
                    val response = client.newCall(requestBuilder.build()).execute()
                    val result = Arguments.createMap()
                    result.putInt("status", response.code)
                    result.putString("body", response.body?.string() ?: "")

                    val responseHeaders = Arguments.createMap()
                    response.headers.forEach { (name, value) ->
                        responseHeaders.putString(name, value)
                    }
                    result.putMap("headers", responseHeaders)

                    promise.resolve(result)
                } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
                    promise.reject("CERTIFICATE_PINNING_FAILED", "Échec de vérification du certificat", e)
                } catch (e: Exception) {
                    promise.reject("NETWORK_ERROR", "Erreur réseau: ${e.message}", e)
                }
            }.start()
        } catch (e: Exception) {
            promise.reject("FETCH_ERROR", "Erreur de requête: ${e.message}", e)
        }
    }
}
