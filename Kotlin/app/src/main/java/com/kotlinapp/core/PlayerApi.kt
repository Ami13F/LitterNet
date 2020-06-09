package com.kotlinapp.core

import com.kotlinapp.model.Player
import retrofit2.Call
import retrofit2.http.*


object PlayerApi{

    interface Service{
        @GET("Players")
        fun getPlayers(): Call<List<Player>>

        @GET("Players/{id}")
        suspend fun find(@Path("id") personId: Int): Player

        @Headers("Content-Type: application/json")
        @POST("Players")
        suspend fun create(@Body player: Player): Player

        @Headers("Content-Type: application/json")
        @GET("Players/findOne")
        suspend fun findOne(@Query("filter") query: String): Player

        @Headers("Content-Type: application/json")
        @PUT("Players")
        suspend fun update(@Body player: Player): Player
    }

    val service: Service = Api.retrofit.create(
        Service::class.java)

}