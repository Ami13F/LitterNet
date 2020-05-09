package com.kotlinapp.auth.data

import android.util.Log
import com.kotlinapp.auth.AuthApi
import com.kotlinapp.core.Api
import com.kotlinapp.core.Result
import com.kotlinapp.core.ItemApi
import com.kotlinapp.entities.Player
import com.kotlinapp.utils.TAG

object AuthRepository {
     var user: User? = null
     var currentPlayer : Player? = null

    val isLoggedIn: Boolean
        get() = user !=null

    fun logout(){
        user = null
        Api.tokenInterceptor.token = null
    }

    suspend fun login(username: String, password: String): Result<TokenHolder> {
        val user = User(username, password)
        val result = AuthApi.login(user)
        if (result is Result.Success<TokenHolder>) {
            setLoggedInUser(user, result.data)
        }
        return result
    }

    private suspend fun setLoggedInUser(user: User, tokenHolder: TokenHolder) {
        user.id = tokenHolder.userId
        Log.d(TAG,"User...$user")
        Api.tokenInterceptor.token = tokenHolder.id
        val query = "{\"where\":{\"email\":\"${user.email}\"}}"
        when (val result = AuthApi.findOne(query)) {
                is Result.Success ->{
                    this.user = result.data
                }
        }
        Log.d(TAG,"User logged: ${this.user}")
       currentPlayer = getCurrentPlayer(user.id!!)
    }

    suspend fun getCurrentPlayer(id: Int): Player{
        val playerQuery = "{\"where\":{\"idPlayer\": $id }}"
        this.currentPlayer = ItemApi.service.findOne(playerQuery)
        Log.d(TAG, "Current player is... $currentPlayer")
        return currentPlayer!!
    }
}