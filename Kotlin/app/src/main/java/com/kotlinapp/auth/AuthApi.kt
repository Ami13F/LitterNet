package com.kotlinapp.auth

import android.util.Log
import com.kotlinapp.auth.data.TokenHolder
import com.kotlinapp.auth.data.User
import com.kotlinapp.core.Api
import com.kotlinapp.utils.Result
import com.kotlinapp.auth.data.ResponseEntity
import com.kotlinapp.utils.TAG
import retrofit2.http.*
import retrofit2.Call
import retrofit2.Response


object AuthApi {
    data class PasswordChanger(val oldPassword: String, val newPassword: String)

    interface AuthService{
        @Headers("Content-Type: application/json")
        @POST("users/login")
        suspend fun login(@Body user: User): TokenHolder

        @Headers("Content-Type: application/json")
        @GET("users/")
        fun getAllUsers(): Call<List<User>>

        @Headers("Content-Type: application/json")
        @POST("users/")
        suspend fun createAccount(@Body user: User): ResponseEntity

        @Headers("Content-Type: application/json")
        @PUT("users/{id}")
        suspend fun updateUser(@Path("id") userID: Int): User

        @Headers("Content-Type: application/json")
        @POST("users/change-password")
        suspend fun changePass(@Body pass: PasswordChanger): Response<Unit>

        @Headers("Content-Type: application/json")
        @GET("users/findOne")
        suspend fun findOne(@Query("filter") email: String): User

    }
     val authService:AuthService = Api.retrofit.create(AuthService::class.java)

    suspend fun login(user: User): Result<TokenHolder> {
        return try{
            Result.Success(authService.login(user))
        }catch(e: Exception){
            Result.Error(e)
        }
    }

    suspend fun findOne(email: String): Result<User> {
        return try{
            Result.Success(authService.findOne(email))
        }catch(e: Exception){
            Result.Error(e)
        }
    }
    suspend fun createAccount(user: User): Result<ResponseEntity> {
        return try{
            val us = authService.createAccount(user)
            Log.d(TAG,"User account is... $us")
            Result.Success(us)
        }catch(e: Exception){
            Result.Error(e)
        }
    }


}