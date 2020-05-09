package com.kotlinapp.localPersistence

import androidx.lifecycle.LiveData
import androidx.room.*
import com.kotlinapp.auth.data.User
import com.kotlinapp.entities.AvatarHolder
import com.kotlinapp.entities.Player

@Dao
interface ItemDao {
    data class BoardItem(var username: String, var country: String, var avatar: AvatarHolder, var score:Int)

    @Query("SELECT * from Player")
    fun getAllPlayers(): LiveData<List<Player>>

    @Query("SELECT * from User")
    fun getAllUsers(): LiveData<List<User>>

    @Query("SELECT User.username, Player.country, Player.avatar, Player.score from Player JOIN User on User.id=Player.idPlayer order by Player.score DESC")// ,
    fun getSortedEntities(): LiveData<List<BoardItem>>

    @Query("SELECT User.username, Player.country, Player.avatar, Player.score from Player JOIN User on User.id=Player.idPlayer where Player.country=:country order by Player.score DESC")// ,
    fun getSortedByCountry(country: String): LiveData<List<BoardItem>>

    @Query("SELECT * FROM Player WHERE idPlayer=:id ")
    fun findPlayer(id: Int): LiveData<Player>


    @Query("SELECT * FROM User WHERE id=:id ")
    fun findUserId(id: Int): LiveData<User>

    @Query("SELECT * FROM User WHERE email=:email ")
    fun findUser(email: String): LiveData<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Player)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(item: User)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: Player)

    @Query("DELETE FROM Player where idPlayer=:id")
    suspend fun deleteOne(id: String)

    @Query("DELETE FROM Player")
    suspend fun deleteAll()
}