package com.kotlinapp.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.kotlinapp.utils.TAG
import com.kotlinapp.model.Player
import com.kotlinapp.model.PlayerRepository
import kotlinx.coroutines.launch
import com.kotlinapp.utils.Result
import com.kotlinapp.core.persistence.LitterDatabase
import com.kotlinapp.R

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val mutableFetching = MutableLiveData<Boolean>().apply { value = false }
    private val mutableCompleted = MutableLiveData<Boolean>().apply { value = false }
    private val mutableException = MutableLiveData<Exception>().apply { value = null }


    private val mutablePasswordState = MutableLiveData<PasswordState>()
    val passwordState: LiveData<PasswordState> = mutablePasswordState

    private val mutablePlayerUpdate = MutableLiveData<Player>()
    val playerUpdate = mutablePlayerUpdate

    val fetching: LiveData<Boolean> = mutableFetching
    val fetchingError: LiveData<Exception> = mutableException
    val completed: LiveData<Boolean> = mutableCompleted

    private val itemRepository: PlayerRepository

    init {
        val itemDao = LitterDatabase.getDatabase(application, viewModelScope).itemDao()
        itemRepository = PlayerRepository(itemDao)
    }

    fun validatePasswords(oldPass: String, newPass1: String, newPass2: String){
         when {
            oldPass.length < 6 -> {
                mutablePasswordState.value =
                    PasswordState(oldPasswordError = R.string.invalid_password)
            }
            newPass1.length < 6 -> {
                mutablePasswordState.value =
                    PasswordState(newPassword1Error = R.string.invalid_password)
            }
            newPass2.length < 6 -> {
                mutablePasswordState.value =
                    PasswordState(newPassword2Error = R.string.invalid_password)
            }
            newPass1 != newPass2 -> {
                mutablePasswordState.value =
                    PasswordState(newPassword2Error = R.string.same_passwords)
            }
            else -> {
                mutablePasswordState.value =
                    PasswordState(isValid = true)
            }
        }
    }

    fun updateProfile(player: Player) {
        viewModelScope.launch {
            Log.v(TAG, "Update Profile...")
            mutableFetching.value = true
            mutableException.value = null

            when(val result= itemRepository.updatePlayer(player)) {
                is Result.Success -> {
                    Log.d(TAG, "Update succeeded")
                    mutablePlayerUpdate.value = player
                }
                is Result.Error -> {
                    Log.w(TAG, "Update failed", result.exception)
                    mutableException.value = result.exception
                }
            }
            mutableFetching.value = false
        }
    }

    fun changePassword(oldPass: String, newPass: String) {
        viewModelScope.launch {
            Log.d(TAG, "Change password....")
            mutableFetching.value = true
            mutableException.value = null

            when(val result= itemRepository.changePassword(oldPass, newPass)) {
                is Result.Success -> {
                    Log.d(TAG, "Update succeeded")
                    mutableCompleted.value = true
                }
                is Result.Error -> {
                    Log.w(TAG, "Update failed", result.exception)
                    mutablePasswordState.value =
                        PasswordState(oldPasswordError = R.string.invalid_old_password)
                    mutableException.value = result.exception
                }
            }
            mutableFetching.value = false
        }
    }
}
