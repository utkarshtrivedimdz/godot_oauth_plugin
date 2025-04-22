package org.godotengine.plugin.android.oauth2

import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

class GodotAndroidPlugin(godot: Godot): GodotPlugin(godot) {
    // Class-level properties
    private val TAG = "GodotOAuth2Plugin"
    private lateinit var credentialManager: CredentialManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun onMainCreate(activity: android.app.Activity): View? {
        super.onMainCreate(activity)
        // Initialize the CredentialManager
        credentialManager = CredentialManager.create(activity.applicationContext)
        return super.onMainCreate(activity)
    }

    /**
     * Example showing how to declare a method that's used by Godot.
     *
     * Shows a 'Hello World' toast.
     */
    @UsedByGodot
    fun helloWorld() {
        runOnUiThread {
            Toast.makeText(activity, "Hello World", Toast.LENGTH_LONG).show()
            Log.v(pluginName, "Hello World")
        }
    }

    /**
     * Signs in with Google using the Credential Manager API
     * This uses the bottom sheet UI for credential selection
     *
     * @param webClientId Your Google OAuth web client ID
     * @param filterByAuthorizedAccounts Whether to only show accounts already authorized for your app
     * @param enableAutoSelect Whether to enable automatic sign-in when a single matching credential is found
     * @param nonce Optional nonce for security (can be null)
     * @return A boolean indicating if the sign-in process was started successfully
     */
    @UsedByGodot
    fun signInWithGoogle(webClientId: String, filterByAuthorizedAccounts: Boolean, 
                         enableAutoSelect: Boolean, nonce: String?): Boolean {
        try {
            // Create the Google ID Option
            val googleIdOptionBuilder = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(enableAutoSelect)
            
            // Add nonce if provided
            if (!nonce.isNullOrEmpty()) {
                googleIdOptionBuilder.setNonce(nonce)
            }
            
            val googleIdOption = googleIdOptionBuilder.build()
            
            // Create the credential request
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            
            // Launch the authentication flow
            coroutineScope.launch {
                try {
                    val result = credentialManager.getCredential(
                        request = request,
                        context = activity!!.applicationContext
                    )
                    handleSignInResult(result)
                } catch (e: GetCredentialException) {
                    Log.e(TAG, "Sign-in failed: ${e.localizedMessage}")
                    emitSignal("authentication_failed", e.localizedMessage ?: "Unknown error")
                }
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up sign-in: ${e.localizedMessage}")
            emitSignal("authentication_failed", e.localizedMessage ?: "Unknown error")
            return false
        }
    }
    
    /**
     * Explicitly shows the Sign in with Google button flow
     *
     * @param webClientId Your Google OAuth web client ID
     * @param nonce Optional nonce for security (can be null)
     * @return A boolean indicating if the sign-in process was started successfully
     */
    @UsedByGodot
    fun showSignInWithGoogleButton(webClientId: String, nonce: String?): Boolean {
        try {
            // Create the Sign In With Google Option
            val signInWithGoogleOptionBuilder = GetSignInWithGoogleOption.Builder(
                serverClientId = webClientId
            )
            
            // Add nonce if provided
            if (!nonce.isNullOrEmpty()) {
                signInWithGoogleOptionBuilder.setNonce(nonce)
            }
            
            val signInWithGoogleOption = signInWithGoogleOptionBuilder.build()
            
            // Create the credential request
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()
            
            // Launch the authentication flow
            coroutineScope.launch {
                try {
                    val result = credentialManager.getCredential(
                        request = request,
                        context = activity!!.applicationContext
                    )
                    handleSignInResult(result)
                } catch (e: GetCredentialException) {
                    Log.e(TAG, "Sign-in failed: ${e.localizedMessage}")
                    emitSignal("authentication_failed", e.localizedMessage ?: "Unknown error")
                }
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up sign-in: ${e.localizedMessage}")
            emitSignal("authentication_failed", e.localizedMessage ?: "Unknown error")
            return false
        }
    }
    
    /**
     * Handle the sign-in result
     */
    private fun handleSignInResult(result: GetCredentialResponse) {
        val credential = result.credential
        
        when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        // Extract the Google ID Token credential
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        
                        // Get the ID token
                        val idToken = googleIdTokenCredential.idToken
                        
                        // Create a dictionary to return to Godot
                        val resultDict = Dictionary()
                        resultDict.put("success", true)
                        resultDict.put("id_token", idToken)
                        
                        // Optional: Extract additional profile info if needed
                        googleIdTokenCredential.displayName?.let {
                            resultDict.put("display_name", it)
                        }
                        googleIdTokenCredential.profilePictureUri?.let {
                            resultDict.put("profile_picture_uri", it.toString())
                        }
                        googleIdTokenCredential.givenName?.let {
                            resultDict.put("given_name", it)
                        }
                        googleIdTokenCredential.id.let {
                            resultDict.put("id", it)
                        }
                        
                        // Emit signal with the result
                        emitSignal("authentication_completed", resultDict)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Invalid Google ID token: ${e.localizedMessage}")
                        emitSignal("authentication_failed", "Invalid Google ID token")
                    }
                } else {
                    Log.e(TAG, "Unexpected credential type: ${credential.type}")
                    emitSignal("authentication_failed", "Unexpected credential type")
                }
            }
            else -> {
                Log.e(TAG, "Unexpected credential: ${credential.javaClass.simpleName}")
                emitSignal("authentication_failed", "Unexpected credential type")
            }
        }
    }
    
    /**
     * Signs out the user
     */
    @UsedByGodot
    fun signOut(): Boolean {
        try {
            coroutineScope.launch {
                try {
                    // Clear the credential state
                    val clearRequest = ClearCredentialStateRequest()
                    credentialManager.clearCredentialState(
                        request = clearRequest
                    )
                    emitSignal("sign_out_completed")
                } catch (e: Exception) {
                    Log.e(TAG, "Sign-out failed: ${e.localizedMessage}")
                    emitSignal("sign_out_failed", e.localizedMessage ?: "Unknown error")
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up sign-out: ${e.localizedMessage}")
            emitSignal("sign_out_failed", e.localizedMessage ?: "Unknown error")
            return false
        }
    }

    /**
     * Get registered signal names for this plugin
     */
    override fun getPluginSignals(): Set<SignalInfo> {
        val signals = mutableSetOf<SignalInfo>()
        signals.add(SignalInfo("authentication_completed", Dictionary::class.java))
        signals.add(SignalInfo("authentication_failed", String::class.java))
        signals.add(SignalInfo("sign_out_completed"))
        signals.add(SignalInfo("sign_out_failed", String::class.java))
        return signals
    }
}