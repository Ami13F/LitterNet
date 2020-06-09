package com.kotlinapp.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.kotlinapp.model.Player
import com.kotlinapp.utils.TAG

object AppPreferences {
    private const val name = "MainActivity"
    private const val mode = Context.MODE_PRIVATE
    private lateinit var preferences: SharedPreferences
    private val gson = Gson()

    //SharedPreferences variables
    private val IS_LOGIN = Pair("is_login", false)
    private val USERNAME = Pair("username", "")
    private val TOKEN = Pair("token", "")


    fun init(context: Context) {
        preferences = context.getSharedPreferences(
            name,
            mode
        )
    }

    //save variable
    private inline fun SharedPreferences.edit(operation: (SharedPreferences.Editor) -> Unit) {
        val editor = edit()
        operation(editor)
        editor.apply()
    }

    fun getCurrentPlayer(): Player {
        val json: String = preferences.getString("player", "")!!
        Log.d(TAG,json)
        return gson.fromJson(json, Player::class.java)
    }

    fun setCurrentPlayer(player: Player){
        val prefsEditor: SharedPreferences.Editor = preferences.edit()
        val json: String = gson.toJson(player)
        prefsEditor.putString("player", json)
        prefsEditor.apply()
    }

    //SharedPreferences variables getters/setters
    var isLogin: Boolean
        get() = preferences.getBoolean(
            IS_LOGIN.first, IS_LOGIN.second)
        set(value) = preferences.edit {
            it.putBoolean(IS_LOGIN.first, value)
        }

    var username: String
        get() = preferences.getString(
            USERNAME.first, USERNAME.second) ?: ""
        set(value) = preferences.edit {
            it.putString(USERNAME.first, value)
        }

    var token: String
        get() = preferences.getString(
            TOKEN.first, TOKEN.second) ?: ""
        set(value) = preferences.edit {
            it.putString(TOKEN.first, value)
        }

}