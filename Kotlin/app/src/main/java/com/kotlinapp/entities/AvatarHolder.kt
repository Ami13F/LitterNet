package com.kotlinapp.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity
data class AvatarHolder (
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    @SerializedName("type")
    val type: String="Buffer",
    @SerializedName("data") @ColumnInfo(name="data", typeAffinity = ColumnInfo.BLOB)
    var data: ByteArray
){
    constructor(): this(1, "Buffer", ByteArray(10000))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AvatarHolder

        if (type != other.type) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}