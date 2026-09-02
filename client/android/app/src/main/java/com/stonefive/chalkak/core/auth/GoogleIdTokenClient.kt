package com.stonefive.chalkak.core.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

class GoogleIdTokenClient(
    private val credentialManager: CredentialManager,
    private val serverClientId: String,
) {
    suspend fun getIdToken(activity: Activity): GoogleCredentialResult {
        if (serverClientId.isBlank()) {
            return GoogleCredentialResult.Failure(GoogleCredentialFailure.CONFIGURATION)
        }

        val request = GetCredentialRequest
            .Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(serverClientId).build(),
            ).build()

        return try {
            credentialManager
                .getCredential(
                    context = activity,
                    request = request,
                ).toGoogleCredentialResult()
        } catch (error: CancellationException) {
            throw error
        } catch (error: GetCredentialCancellationException) {
            GoogleCredentialResult.Cancelled
        } catch (error: NoCredentialException) {
            GoogleCredentialResult.Failure(GoogleCredentialFailure.NO_CREDENTIAL)
        } catch (error: GetCredentialInterruptedException) {
            GoogleCredentialResult.Failure(GoogleCredentialFailure.INTERRUPTED)
        } catch (error: GetCredentialProviderConfigurationException) {
            GoogleCredentialResult.Failure(GoogleCredentialFailure.CONFIGURATION)
        } catch (error: GetCredentialUnsupportedException) {
            GoogleCredentialResult.Failure(GoogleCredentialFailure.UNSUPPORTED)
        } catch (error: GetCredentialException) {
            GoogleCredentialResult.Failure(GoogleCredentialFailure.UNKNOWN)
        } catch (error: IllegalArgumentException) {
            GoogleCredentialResult.Failure(GoogleCredentialFailure.CONFIGURATION)
        } catch (error: Exception) {
            GoogleCredentialResult.Failure(GoogleCredentialFailure.UNKNOWN)
        }
    }

    private fun androidx.credentials.GetCredentialResponse.toGoogleCredentialResult(): GoogleCredentialResult {
        val customCredential = credential as? CustomCredential
            ?: return GoogleCredentialResult.Failure(GoogleCredentialFailure.UNEXPECTED_CREDENTIAL)
        if (customCredential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleCredentialResult.Failure(GoogleCredentialFailure.UNEXPECTED_CREDENTIAL)
        }

        return try {
            GoogleCredentialResult.Success(
                GoogleIdTokenCredential.createFrom(customCredential.data).idToken,
            )
        } catch (error: GoogleIdTokenParsingException) {
            GoogleCredentialResult.Failure(GoogleCredentialFailure.INVALID_CREDENTIAL)
        }
    }
}

sealed interface GoogleCredentialResult {
    data class Success(val idToken: String) : GoogleCredentialResult

    data object Cancelled : GoogleCredentialResult

    data class Failure(val reason: GoogleCredentialFailure) : GoogleCredentialResult
}

enum class GoogleCredentialFailure {
    NO_CREDENTIAL,
    INTERRUPTED,
    CONFIGURATION,
    UNSUPPORTED,
    UNEXPECTED_CREDENTIAL,
    INVALID_CREDENTIAL,
    UNKNOWN,
}
