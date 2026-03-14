package com.flowguard.modules

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = BiometricModule.NAME)
class BiometricModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "FlowGuardBiometric"
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun isAvailable(promise: Promise) {
        try {
            val biometricManager = BiometricManager.from(reactApplicationContext)
            val canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            promise.resolve(canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS)
        } catch (e: Exception) {
            promise.reject("BIOMETRIC_ERROR", "Erreur de vérification biométrique: ${e.message}", e)
        }
    }

    @ReactMethod
    fun authenticate(reason: String, promise: Promise) {
        try {
            val activity = currentActivity as? FragmentActivity
            if (activity == null) {
                val result = Arguments.createMap()
                result.putBoolean("success", false)
                result.putString("error", "Activité non disponible")
                promise.resolve(result)
                return
            }

            val executor = ContextCompat.getMainExecutor(reactApplicationContext)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val map = Arguments.createMap()
                    map.putBoolean("success", true)
                    promise.resolve(map)
                }

                override fun onAuthenticationFailed() {
                    val map = Arguments.createMap()
                    map.putBoolean("success", false)
                    map.putString("error", "Authentification échouée")
                    promise.resolve(map)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val map = Arguments.createMap()
                    map.putBoolean("success", false)
                    map.putString("error", errString.toString())
                    promise.resolve(map)
                }
            }

            val biometricPrompt = BiometricPrompt(activity, executor, callback)

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("FlowGuard")
                .setSubtitle(reason)
                .setNegativeButtonText("Annuler")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            activity.runOnUiThread {
                biometricPrompt.authenticate(promptInfo)
            }
        } catch (e: Exception) {
            val map = Arguments.createMap()
            map.putBoolean("success", false)
            map.putString("error", e.message ?: "Erreur inconnue")
            promise.resolve(map)
        }
    }

    @ReactMethod
    fun getBiometryType(promise: Promise) {
        try {
            val biometricManager = BiometricManager.from(reactApplicationContext)
            val canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    promise.resolve("Fingerprint")
                } else {
                    promise.resolve("Fingerprint")
                }
            } else {
                promise.resolve("None")
            }
        } catch (e: Exception) {
            promise.resolve("None")
        }
    }
}
