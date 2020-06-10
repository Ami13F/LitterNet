package com.kotlinapp.model

import android.util.Log
import androidx.lifecycle.LiveData
import com.kotlinapp.auth.AuthApi
import com.kotlinapp.auth.AuthApi.authService
import com.kotlinapp.auth.data.User
import com.kotlinapp.core.PlayerApi
import retrofit2.await
import com.kotlinapp.utils.Result
import com.kotlinapp.core.persistence.ItemDao
import com.kotlinapp.utils.TAG

class PlayerRepository (private val itemDao: ItemDao){

    var players = itemDao.getAllPlayers()
    var users = itemDao.getAllUsers()
    var leaders = itemDao.getSortedEntities()

    suspend fun sortLeaders(): Result<Boolean> {
        return try {
            Log.d(TAG,"Refreshing...")
            val usersServer = authService.getAllUsers().await()
            val items = PlayerApi.service.getPlayers().await()
            Log.d(TAG,"Users from server: $usersServer")
            Log.d(TAG,"Players from server: $items")
            for(user in usersServer){
                Log.d(TAG,"USER: $user")
                itemDao.insertUser(user)
            }
            for (item in items) {
                itemDao.insert(item)
            }
            Result.Success(true)
        } catch(e: Exception) {
            Result.Error(e)
        }
    }

    fun findPlayer(playerId: Int): LiveData<Player> {
        return itemDao.findPlayer(playerId)
    }

     fun findUser(userEmail: String): LiveData<User> {
        return itemDao.findUser(userEmail)

    }

    suspend fun save(player: Player): Result<Player> {
        return try {
            Log.d(TAG,"Saving player...$player")
            val createdItem = PlayerApi.service.create(player)
            Log.d(TAG,"Saved player...$createdItem")
            Result.Success(createdItem)
        }catch (e: Exception){
            Log.d(TAG,"Error saving player...${e.message}")
            Result.Error(e)
        }
    }

    suspend fun updateUser(item: User): Result<User> {
        return try {
            val updatedItem = authService.updateUser(item.id!!)
            Result.Success(updatedItem)
        }catch(e: Exception){
            Result.Error(e)
        }
    }

    suspend fun changePassword(oldPass: String, newPasss: String): Result<Boolean> {
        return try {
            val resp = authService.changePass(AuthApi.PasswordChanger(oldPass, newPasss))
            Log.d(TAG, "Success resp... ${resp.code()}")
            if (resp.code() >= 400) {
                return Result.Error(null)
            }
            Result.Success(true)
        }catch(e: Exception){
            Result.Error(e)
        }
    }

    suspend fun updatePlayer(item: Player): Result<Player> {
        return try {
            val updatedItem = PlayerApi.service.update(item)
            Result.Success(updatedItem)
        }catch(e: Exception){
            Result.Error(e)
        }finally {
            itemDao.update(item)
        }
    }
}