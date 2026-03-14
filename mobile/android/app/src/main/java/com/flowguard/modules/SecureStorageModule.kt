package com.flowguard.modules

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = SecureStorageModule.NAME)
class SecureStorageModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "FlowGuardSecureStorage"
        private const val PREFS_FILE = "flowguard_secure_prefs"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(reactApplicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            reactApplicationContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun saveToken(key: String, value: String, promise: Promise) {
        try {
            prefs.edit().putString(key, value).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SECURE_STORAGE_ERROR", "Échec de sauvegarde: ${e.message}", e)
        }
    }

    @ReactMethod
    fun getToken(key: String, promise: Promise) {
        try {
            val value = prefs.getString(key, null)
            promise.resolve(value)
        } catch (e: Exception) {
            promise.reject("SECURE_STORAGE_ERROR", "Échec de lecture: ${e.message}", e)
        }
    }

    @ReactMethod
    fun deleteToken(key: String, promise: Promise) {
        try {
            prefs.edit().remove(key).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SECURE_STORAGE_ERROR", "Échec de suppression: ${e.message}", e)
        }
    }

    @ReactMethod
    fun clearAll(promise: Promise) {
        try {
            prefs.edit().clear().apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SECURE_STORAGE_ERROR", "Échec de nettoyage: ${e.message}", e)
        }
    }
}
