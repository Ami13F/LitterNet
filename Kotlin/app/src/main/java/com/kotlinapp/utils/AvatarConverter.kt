package com.kotlinapp.utils

import androidx.room.TypeConverter
import com.google.gson.Gson

import com.kotlinapp.entities.AvatarHolder


class AvatarConverter {
    companion object{
        @TypeConverter
        @JvmStatic
        fun fromString(value: String?): AvatarHolder? {
            return Gson().fromJson(value, AvatarHolder::class.java)
        }

        @TypeConverter
        @JvmStatic
        fun avatarToString(list: AvatarHolder?): String? {
            val gson = Gson()
            return gson.toJson(list)
        }
    }

}