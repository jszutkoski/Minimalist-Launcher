package com.jszutkoski.launcher

import android.util.Log

private const val TAG = "Minimalist"

/** For failures that are handled but worth knowing about when something misbehaves on a phone. */
fun warn(message: String, error: Throwable? = null) {
    Log.w(TAG, message, error)
}
