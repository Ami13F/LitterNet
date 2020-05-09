package com.kotlinapp.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.kotlinapp.R
import com.kotlinapp.auth.data.AuthRepository
import com.kotlinapp.detection.DetectorActivity
import com.kotlinapp.utils.TAG
import com.kotlinapp.utils.ImageUtils
import com.kotlinapp.viewModels.HomeViewModel
import kotlinx.android.synthetic.main.home_fragment.*


class HomeFragment : Fragment(){
    private lateinit var viewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.v(TAG, "onCreateView")
        return inflater.inflate(R.layout.home_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.v(TAG, "onActivityCreated")
        setupViewModel()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProviders.of(this).get(HomeViewModel::class.java)
        val score = AuthRepository.currentPlayer!!.score
        val username = AuthRepository.user!!.username
        val avatar = AuthRepository.currentPlayer!!.avatar
        Log.d(TAG, "Score: $score  Username: $username")
        scoreTotal.text = "Your score: $score"
        usernameField.text = "Hello, $username"

        val image = ImageUtils.arrayToBitmap(avatar.data)

        avatarHome.setImageBitmap(image)

        openCameraBtn.setOnClickListener{
            Log.d(TAG,"Open Camera view...")
            val intent = Intent(this.activity, DetectorActivity::class.java)
            startActivity(intent)
        }

        profileBtn.setOnClickListener{
            Log.d(TAG,"Profile editing")
            findNavController().navigate(R.id.item_edit_fragment)
        }

        classBtn.setOnClickListener {
            Log.v(TAG, "leader boards..")
            findNavController().navigate(R.id.item_list_fragment)

        }
    }

}