package com.kotlinapp.viewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.kotlinapp.auth.AuthApi
import com.kotlinapp.auth.data.User
import com.kotlinapp.auth.login.ValidateFormState
import com.kotlinapp.utils.TAG
import com.kotlinapp.entities.Player
import com.kotlinapp.entities.PlayerRepository
import kotlinx.coroutines.launch
import com.kotlinapp.core.Result
import com.kotlinapp.localPersistence.LitterDatabase
import com.kotlinapp.R

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val mutableCompleted = MutableLiveData<Boolean>().apply { value = false }
    private val mutableExceptionEmail = MutableLiveData<Exception>().apply { value = null }
    private val mutableExceptionUsername = MutableLiveData<Exception>().apply { value = null }

    private val exitsEmail = MutableLiveData<Boolean>().apply { value = false }
    val emailExists = exitsEmail
    private val usernameValid = MutableLiveData<Boolean>().apply { value = false }
    val userNameExists = usernameValid

    private val mutableValidForm = MutableLiveData<ValidateFormState>()
    val validFormState = mutableValidForm

    val completed: LiveData<Boolean> = mutableCompleted

    private val itemRepository: PlayerRepository

    init {
        val itemDao = LitterDatabase.getDatabase(application, viewModelScope).itemDao()
        itemRepository = PlayerRepository(itemDao)
    }

    fun validateCreateAccount(email: String, username: String, password: String, validateEmail: Boolean, validateUsername: Boolean){
       Log.d(TAG, "Validating.....")
        if(validateEmail && !validateEmail(email)){
           mutableValidForm.value = ValidateFormState(emailError = R.string.invalid_email_format)
       }else if(validateEmail && exitsEmail.value!!){
           mutableValidForm.value = ValidateFormState(emailError = R.string.email_exists)
       }else if(validateUsername && username.isEmpty()){
           mutableValidForm.value = ValidateFormState(usernameError = R.string.invalid_username)
       }
       else if(validateUsername && usernameValid.value!!){
           mutableValidForm.value = ValidateFormState(usernameError = R.string.username_exists)
       }else if(validateUsername && username.contains("!@#$%^&*()")){
            mutableValidForm.value = ValidateFormState(usernameError = R.string.invalid_username_chars)
        }else if(password.isEmpty()){
           mutableValidForm.value = ValidateFormState(passwordError = R.string.invalid_password)
       }else if(password.contains("!@#$%^&*()")){
            mutableValidForm.value = ValidateFormState(passwordError = R.string.invalid_password_chars)
        } else {
           mutableValidForm.value = ValidateFormState(isDataValid = true)
       }
    }


    private fun validateEmail(email: String): Boolean{
        Log.d(TAG, "Validating email: $email")
        val pattern = Regex("[a-zA-Z0-9\\-]{1,35}@[a-zA-Z0-9\\-]{1,35}\\.[a-zA-Z0-9\\-]{1,10}")
        val match = pattern.containsMatchIn(email)
        if (!match)
            return false
        return true
    }

    fun findUserName(username: String){
        viewModelScope.launch {
            mutableExceptionUsername.value = null
            usernameValid.value = false
            Log.v(TAG, "Find by email: $username...")
            val query = "{\"where\":{\"username\":\"$username\"}}"
            when(val result = AuthApi.findOne(query)) {
                is Result.Success -> {
                    Log.d(TAG, "Username found ${result.data}")
                    usernameValid.value = true
                }
                is Result.Error -> {
                    Log.w(TAG, "No existing username")
                    Log.d(TAG,"${result.exception}")
                    mutableExceptionUsername.value = java.lang.Exception("Username not found")
                }
            }
        }
    }

    fun findOne(email: String) {
        // Return true if email exists else false
        viewModelScope.launch {
            mutableExceptionEmail.value = null
            exitsEmail.value = false
            Log.v(TAG, "Find by email: $email...")
            val emailAfter = "{\"where\":{\"email\":\"$email\"}}"
            when(val result = AuthApi.findOne(emailAfter)) {
                is Result.Success -> {
                    Log.d(TAG, "Email found ${result.data}")
                    exitsEmail.value = true
                }
                is Result.Error -> {
                    Log.w(TAG, "No existing user ")
                    Log.d(TAG,"${result.exception}")
                    mutableExceptionEmail.value = java.lang.Exception("Email not found")
                }
            }
        }
    }



    fun saveAccount(user: User, player: Player) {
        viewModelScope.launch {
            Log.v(TAG, "saveOrUpdateItem...")
            Log.d(TAG,"name: ${player.avatar}")
            Log.d(TAG,"name: ${player.country}")
            Log.d(TAG,"age: ${player.score}")
            Log.d(TAG,"id: ${player.idPlayer}")

            Log.v(TAG, "Create account...")
            when(val result =  AuthApi.createAccount(user)) {
                is Result.Success -> {
                    Log.d(TAG, "Create account succeeded ${result.data.id}")
                    player.idPlayer = result.data.id
                    user.id = result.data.id
                    itemRepository.save(player)
                    //Because we can't override default value from id in loopback
//                    itemRepository.updateUser(user)
                    mutableCompleted.value = true
                }
                is Result.Error -> {
                    Log.w(TAG, "Create account failed", result.exception)
                }
            }
        }
    }
}
