package com.freetia.tv.ui.player

import androidx.media3.common.PlaybackException

const val PROTECTED_MESSAGE = "This channel uses protection Freetia can't play."
const val UNAVAILABLE_MESSAGE = "This channel isn't available right now. Press ↑ or ↓ for another channel."

enum class RetryAction { RETRY, NEXT_VARIANT, GIVE_UP, PROTECTED }

/** DRM failures and formats the device can't decode never recover by retrying. */
fun isProtectedOrUnsupported(errorCode: Int): Boolean =
    errorCode in 6000..6999 ||
        errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
        errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED

/** A few plain retries, then one step down to the next variant, then a clear message. */
fun retryAction(errorCode: Int, retries: Int, variantStepped: Boolean, hasLowerVariant: Boolean, maxRetries: Int = 3): RetryAction = when {
    isProtectedOrUnsupported(errorCode) -> RetryAction.PROTECTED
    retries < maxRetries -> RetryAction.RETRY
    !variantStepped && hasLowerVariant -> RetryAction.NEXT_VARIANT
    else -> RetryAction.GIVE_UP
}
