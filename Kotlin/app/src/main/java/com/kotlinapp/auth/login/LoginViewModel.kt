package com.kotlinapp.auth.login

import android.util.Log
import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kotlinapp.R
import com.kotlinapp.auth.data.AuthRepository
import com.kotlinapp.auth.data.TokenHolder
import com.kotlinapp.utils.TAG
import com.kotlinapp.utils.Result
import kotlinx.coroutines.launch

class LoginViewModel: ViewModel() {
    private val mutableLoginFormState = MutableLiveData<ValidateFormState>()
    val validateFormState: LiveData<ValidateFormState> = mutableLoginFormState


    private val mutableLoginResult = MutableLiveData<Result<TokenHolder>>()
    val loginResult: LiveData<Result<TokenHolder>> = mutableLoginResult

    fun login(username: String, password: String) {
        viewModelScope.launch {
            Log.v(TAG, "login...")
            mutableLoginResult.value = AuthRepository.login(username, password)
        }
    }
    fun loginDataChanged(username: String, password: String) {
        if (!isUserNameValid(username)) {
            mutableLoginFormState.value = ValidateFormState(usernameError = R.string.invalid_username)
        } else if (!isPasswordValid(password)) {
            mutableLoginFormState.value = ValidateFormState(passwordError = R.string.invalid_password)
        } else {
            mutableLoginFormState.value = ValidateFormState(isDataValid = true)
        }
    }

    private fun isUserNameValid(username: String): Boolean {
        return if (username.contains('@')) {
            Patterns.EMAIL_ADDRESS.matcher(username).matches()
        } else {
            username.isNotBlank()
        }
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.isNotEmpty()
    }
}