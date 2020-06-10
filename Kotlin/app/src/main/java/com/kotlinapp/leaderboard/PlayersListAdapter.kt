package com.kotlinapp.leaderboard

import android.graphics.Color
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.kotlinapp.R
import com.kotlinapp.auth.data.User
import com.kotlinapp.core.AppPreferences
import com.kotlinapp.core.persistence.ItemDao
import com.kotlinapp.model.Player
import com.kotlinapp.utils.ImageUtils
import com.kotlinapp.utils.TAG
import kotlinx.android.synthetic.main.classament_view.view.*


class PlayersListAdapter(
    private val fragment: Fragment
) : RecyclerView.Adapter<PlayersListAdapter.ViewHolder>() {

    var players = emptyList<Player>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var users = emptyList<User>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var leaders = emptyList<ItemDao.BoardItem>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.classament_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (itemCount > 0) {
            Log.d(TAG, "Size elements: $itemCount")
            val leader = leaders[position]
            //Set current user color
            if(leader.username == AppPreferences.username)
                holder.layout.setBackgroundColor(Color.parseColor("#8C6A6868"))
            else
                holder.layout.setBackgroundColor(Color.parseColor("#009E9D9D"))
            holder.country.text = leader.country
            holder.score.text = leader.score.toString()
            holder.username.text = leader.username
            holder.imageView.setImageBitmap(ImageUtils.arrayToBitmap(leader.avatar.data))

            holder.itemView.tag = leader
        }else{
            Toast.makeText(this.fragment.context,"No players available yet", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = leaders.size

    // recycler format
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layout: ConstraintLayout = view.findViewById(R.id.leaderLayout)
        val username: TextView = view.username
        val imageView: ImageView = view.imageView
        val country: TextView = view.country
        val score: TextView = view.score
    }
}
