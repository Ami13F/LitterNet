package com.kotlinapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kotlinapp.auth.data.AuthRepository
import com.kotlinapp.detection.DetectorActivity
import com.kotlinapp.utils.TAG


class MainActivity : AppCompatActivity(),
    BottomNavigationView.OnNavigationItemSelectedListener {

    lateinit var bottomNav : BottomNavigationView
    private val secondActivityRequestCode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(TAG, "onCreate")

        setContentView(R.layout.activity_main)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        // Hide the status bar.
        actionBar?.hide()

        bottomNav = findViewById(R.id.bottom_navigation)
        bottomNav.setOnNavigationItemSelectedListener(this)
    }

    override fun onResume() {
        super.onResume()
        Log.d(javaClass.name,"I'm back")
        val score = intent.getIntExtra("Score", 0)
        Log.d(javaClass.name, "Score from resume $score")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val score = intent.getIntExtra("Score", 0)
            Log.d(javaClass.name, "Score from activity $score")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.navigate_out -> {
                Log.d(TAG, "Logout")
                AuthRepository.logout()
                findNavController(R.id.nav_host_fragment).navigate(R.id.login_fragment)
            }
            R.id.navigate_leaderboard -> {
                Log.d(TAG, "Leaderboard")
                findNavController(R.id.nav_host_fragment).navigate(R.id.item_list_fragment)
            }
            R.id.navigate_openCamera -> {
                Log.d(TAG, "Open Camera view...")
                val intent = Intent(this, DetectorActivity::class.java)
                startActivityForResult(intent, secondActivityRequestCode)
            }
            R.id.navigate_profile -> {
                Log.d(TAG, "Profile")
                findNavController(R.id.nav_host_fragment).navigate(R.id.item_edit_fragment)
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }
}
