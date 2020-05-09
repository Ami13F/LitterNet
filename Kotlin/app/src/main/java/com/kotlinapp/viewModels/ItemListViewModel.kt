package com.kotlinapp.viewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.kotlinapp.auth.data.AuthRepository
import com.kotlinapp.auth.data.User
import com.kotlinapp.utils.TAG
import com.kotlinapp.entities.Player
import com.kotlinapp.entities.PlayerRepository
import kotlinx.coroutines.launch
import java.lang.Exception
import com.kotlinapp.core.Result
import com.kotlinapp.localPersistence.ItemDao
import com.kotlinapp.localPersistence.LitterDatabase

class ItemsListViewModel(application: Application): AndroidViewModel(application) {

    private val mutableLoading = MutableLiveData<Boolean>().apply { value = false }
    private val mutableException = MutableLiveData<Exception>().apply { value = null }

    val players: LiveData<List<Player>>
    val users: LiveData<List<User>>
    var leaderList: LiveData<List<ItemDao.BoardItem>>

    private val itemDao : ItemDao = LitterDatabase.getDatabase(application, viewModelScope).itemDao()
    var leaderCountryList: LiveData<List<ItemDao.BoardItem>>

    var country = AuthRepository.currentPlayer!!.country
    val loading: LiveData<Boolean> = mutableLoading
    val loadingError: LiveData<Exception> = mutableException

    val itemRepository: PlayerRepository

    init {
        itemRepository = PlayerRepository(itemDao)
        players = itemRepository.players
        users = itemRepository.users
        leaderList = itemRepository.leaders
        leaderCountryList = itemDao.getSortedByCountry(country)
    }

    fun sortGlobal() {
        viewModelScope.launch {
            Log.v(TAG, "refresh...")
            mutableLoading.value = true
            mutableException.value = null
            when (val result = itemRepository.sortLeaders()) {
                is Result.Success -> {
                    Log.d(TAG, "refresh succeeded")
                    leaderList = itemRepository.leaders
                }
                is Result.Error -> {
                    Log.w(TAG, "refresh failed", result.exception)
                    mutableException.value = result.exception
                }
            }
            mutableLoading.value = false
        }
    }

    fun sortByCountry() {
        viewModelScope.launch {
            Log.v(TAG, "Sorting by country...")
            mutableLoading.value = true
            mutableException.value = null
            when (val result = itemRepository.sortLeaders()) {
                is Result.Success -> {
                    Log.d(TAG, "refresh succeeded")
                    leaderCountryList = itemDao.getSortedByCountry(country)

                }
                is Result.Error -> {
                    Log.w(TAG, "refresh failed", result.exception)
                    mutableException.value = result.exception
                }
            }
            mutableLoading.value = false
        }
    }

}
