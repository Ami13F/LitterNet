package com.kotlinapp.profile

data class PasswordState(
    var oldPasswordError: Int? = null,
    var newPassword1Error: Int? = null,
    var newPassword2Error: Int? = null,
    var isValid: Boolean = false
)