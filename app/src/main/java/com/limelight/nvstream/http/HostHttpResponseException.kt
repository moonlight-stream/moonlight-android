package com.limelight.nvstream.http

import java.io.IOException

class HostHttpResponseException(
    private val errorCode: Int,
    private val errorMsg: String,
    private val sunshineErrorCode: String? = null
) : IOException() {

    companion object {
        private const val serialVersionUID = 1543508830807804222L
    }

    fun getErrorCode(): Int = errorCode

    fun getErrorMessage(): String = errorMsg

    fun getSunshineErrorCode(): String? = sunshineErrorCode

    fun withSunshineErrorCode(value: String?): HostHttpResponseException =
        HostHttpResponseException(errorCode, errorMsg, value).apply {
            initCause(this@HostHttpResponseException)
        }

    override val message: String
        get() = "Host PC returned error: $errorMsg (Error code: $errorCode)"
}
