package com.kotlinapp.entities

import androidx.room.*
import com.kotlinapp.utils.AvatarConverter

@Entity(tableName = "Player")
data class Player (

    @PrimaryKey @ColumnInfo(name = "idPlayer")
    var idPlayer: Int?,

    @TypeConverters(AvatarConverter::class)
    @ColumnInfo(name = "avatar")
    var avatar: AvatarHolder,

    @ColumnInfo(name = "country")
    var country: String,

    @ColumnInfo(name = "score")
    var score: Int = 0
){

    constructor(id: Int?,  country: String) : this(id, AvatarHolder(),country,111)

    @Ignore
    constructor(id: Int, country: String) : this(id, AvatarHolder(), country,111)

    constructor(avatar: AvatarHolder, country: String) : this(1, avatar, country,111)

    constructor(): this(0, AvatarHolder(),"",111)

    override fun toString(): String = "ID: $idPlayer, Country: $country Score: $score"
}
