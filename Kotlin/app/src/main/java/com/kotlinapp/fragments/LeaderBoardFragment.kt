package com.kotlinapp.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.kotlinapp.PlayersListAdapter
import com.kotlinapp.R
import com.kotlinapp.utils.TAG
import com.kotlinapp.viewModels.LeaderboardViewModel
import kotlinx.android.synthetic.main.item_list_fragment.*


class LeaderBoardFragment : Fragment() {

    private lateinit var itemListAdapter: PlayersListAdapter
    private lateinit var itemsModel: LeaderboardViewModel
    private var isCountrySelected = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
        ):View?{
        Log.v(TAG,"onCreateView")
        return inflater.inflate(R.layout.item_list_fragment,container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.v(TAG, "onActivityCreated Leaderboard...")

        setupItemList()

        generalBtn.setOnClickListener{
            isCountrySelected = false
            itemsModel.sortGlobal()
        }
        myCountryBtn.setOnClickListener{
            isCountrySelected = true
            itemsModel.sortByCountry()
        }
    }

    private fun setupItemList() {
        itemListAdapter = PlayersListAdapter(this)
        item_list.adapter = itemListAdapter
        itemsModel = ViewModelProviders.of(this).get(LeaderboardViewModel::class.java)

        itemsModel.players.observe(this, Observer { items ->
            Log.v(TAG, "update items")
            itemListAdapter.players = items
        })

        itemsModel.users.observe(this, Observer { items ->
            Log.v(TAG, "update users")
            itemListAdapter.users = items
        })
        itemsModel.leaderList.observe(this, Observer { items->
            Log.d(TAG, "Update leaders...")
            if(!isCountrySelected)
                itemListAdapter.leaders = items
        })

        itemsModel.leaderCountryList.observe(this, Observer { items->
            Log.d(TAG, "Update leaders...")
            if(isCountrySelected)
                itemListAdapter.leaders = items
        })

        itemsModel.loading.observe(this, Observer { loading ->
            Log.v(TAG, "update loading")
            progress.visibility = if (loading) View.VISIBLE else View.GONE
        })

        itemsModel.loadingError.observe(this, Observer { exception ->
            if (exception != null) {
                Log.v(TAG, "update loading error")
                val message = "Loading exception ${exception.message}"
                val parentActivity = activity?.parent

                if (parentActivity != null) {
                    Toast.makeText(parentActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
        itemsModel.sortGlobal()
    }

}