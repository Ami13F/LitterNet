package com.kotlinapp.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.kotlinapp.R
import com.kotlinapp.auth.data.AuthRepository
import com.kotlinapp.auth.data.User
import com.kotlinapp.auth.login.afterTextChanged
import com.kotlinapp.entities.AvatarHolder
import com.kotlinapp.entities.Player
import com.kotlinapp.utils.ImageUtils
import com.kotlinapp.utils.ImageUtils.FILE_SELECTED
import com.kotlinapp.utils.ImageUtils.REQUEST_CAMERA
import com.kotlinapp.utils.ImageUtils.galleryIntent
import com.kotlinapp.utils.Permissions
import com.kotlinapp.utils.TAG
import com.kotlinapp.viewModels.ProfileViewModel
import kotlinx.android.synthetic.main.create_account_fragment.*
import kotlinx.android.synthetic.main.create_account_fragment.progress
import kotlinx.android.synthetic.main.profile_fragment.*
import java.io.IOException

@SuppressLint("SetTextI18n")
class ProfileFragment : Fragment() {

    private lateinit var viewModel: ProfileViewModel

    private var player: Player? = null
    private var user: User? = null
    private var avatar = AvatarHolder()

    private var userChoose: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        user = AuthRepository.user
        player = AuthRepository.currentPlayer
        Log.d(TAG, "Editing....$player")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.v(TAG, "onCreateView")
        if(arguments != null){
            val score = requireArguments().getString("Score")
            Log.d(TAG, "Score::::: $score")
        }
        return inflater.inflate(R.layout.profile_fragment, container, false)
    }

    override fun onResume() {
        super.onResume()
        // get score from user game
        updateScore()
        //release resource
        requireActivity().intent.putExtra("Score",0)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (!AuthRepository.isLoggedIn) {
            findNavController().navigate(R.id.login_fragment)
            return
        }

        Log.v(TAG, "onActivityCreated")
        Log.d(TAG, "Logggeed  ${AuthRepository.isLoggedIn}")

        viewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)

        setupViewModel()
        Log.d(TAG,"Setting initial values...")
        avatarEdit.setImageBitmap(ImageUtils.arrayToBitmap(AuthRepository.currentPlayer!!.avatar.data))
        //countryName-Code -> getting code
        countryEdit.setCountryForNameCode(AuthRepository.currentPlayer!!.country.split("-")[1])

        setupPasswordState()
        countrySpinner()


        avatarEdit.setOnClickListener{
            Log.d(TAG, "Choosing avatar")
            avatarChooser()
        }

        saveEditBtn.setOnClickListener {
            Log.v(TAG, "Update Password")
            viewModel.changePassword( oldPass = oldPassword.text.toString(), newPass = newPassword1.text.toString())
        }

    }

    private fun setupPasswordState(){
        viewModel.passwordState.observe(viewLifecycleOwner, Observer {passState->
            saveEditBtn.isEnabled = passState.isValid
            if(passState.oldPasswordError != null){
                oldPassword.error = getString(passState.oldPasswordError!!)
            }
            if(passState.newPassword1Error!=null){
                newPassword1.error = getString(passState.newPassword1Error!!)
            }
            if(passState.newPassword2Error!=null){
                newPassword2.error = getString(passState.newPassword2Error!!)
            }
        })

        oldPassword.afterTextChanged { viewModel.validatePasswords(
            oldPassword.text.toString(),
            newPassword1.text.toString(),
            newPassword2.text.toString())
        }
        newPassword1.afterTextChanged { viewModel.validatePasswords(
            oldPassword.text.toString(),
            newPassword1.text.toString(),
            newPassword2.text.toString())
        }
        newPassword2.afterTextChanged { viewModel.validatePasswords(
            oldPassword.text.toString(),
            newPassword1.text.toString(),
            newPassword2.text.toString())
        }
    }

    private fun setupViewModel() {

        val score = AuthRepository.currentPlayer!!.score
        val username = AuthRepository.user!!.username
        Log.d(TAG, "Score: $score  Username: $username")
        scoreTotal.text = "Your score: $score"
        usernameText.text = "Hello, $username"

        viewModel.playerUpdate.observe(viewLifecycleOwner, Observer {player->
            avatarEdit.setImageBitmap(ImageUtils.arrayToBitmap(AuthRepository.currentPlayer!!.avatar.data))
            this.player = player
        })

        viewModel.fetching.observe(viewLifecycleOwner, Observer { fetching ->
            Log.v(TAG, "update fetching")
            progress.visibility = if (fetching) View.VISIBLE else View.GONE
        })
        viewModel.fetchingError.observe(viewLifecycleOwner, Observer { exception ->
            if (exception != null) {
                Log.v(TAG, "update fetching error")
                val message = "Fetching exception ${exception.message}"
                val parentActivity = activity?.parent
                if (parentActivity != null) {
                    Toast.makeText(parentActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
        viewModel.completed.observe(viewLifecycleOwner, Observer { completed ->
            if (completed) {
                Log.v(TAG, "completed, navigate back")
                if (this.activity != null) {
                    Log.d(TAG, "Change succeed")
                    Toast.makeText(this.activity, "Password changed!", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
        })
    }

    private fun updateScore(){
        val obtainedScore = requireActivity().intent.getIntExtra("Score",0)
        Log.d(TAG, "Score $obtainedScore")
        if(obtainedScore != 0 ){
            val score = AuthRepository.currentPlayer!!.score + obtainedScore
            AuthRepository.currentPlayer!!.score = score
            viewModel.updateProfile( AuthRepository.currentPlayer!!)
            scoreTotal.text = "Your score: $score"
        }
    }
    private fun countrySpinner() {
        var country: String
        countryEdit.setOnCountryChangeListener{
            country = countryEdit.selectedCountryName + "-" + countryEdit.selectedCountryNameCode
            Log.d(TAG, "Selected country... $country")
            AuthRepository.currentPlayer!!.country = country
            viewModel.updateProfile( AuthRepository.currentPlayer!!)
        }
    }

    private fun setAvatar(){
        Log.d(TAG, "Saving avatar")
        avatar.data = ImageUtils.bitmapToArray((avatarEdit.drawable as BitmapDrawable).bitmap)
        AuthRepository.currentPlayer!!.avatar = avatar
        viewModel.updateProfile(AuthRepository.currentPlayer!!)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == FILE_SELECTED)
                onSelectFromGalleryResult(data)
            else if (requestCode == REQUEST_CAMERA)
                onCaptureImageResult(data!!)
            setAvatar()
        }
    }


    private fun avatarChooser() {
        val types = arrayOf<CharSequence>(
            "Choose from Gallery", "Take a Photo", "Cancel"
        )
        val builder: AlertDialog.Builder = AlertDialog.Builder(this.activity)
        builder.setTitle("Choose Photo from...")
        builder.setItems(types) { dialog, item ->
            val result: Boolean = Permissions.checkPermission(this.activity)
            if (types[item] == "Take a Photo") {
                userChoose = "Take a Photo"
                if (result) cameraIntent()
            } else if (types[item] == "Choose from Gallery") {
                userChoose = "Choose from Gallery"
                if (result) galleryIntent(this)
            } else if (types[item] == "Cancel") {
                dialog.dismiss()
            }
        }
        builder.show()
    }

    private fun cameraIntent(){
        Log.d(TAG,"Starting intent...")
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_CAMERA)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == Permissions.MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if(userChoose == "Take a Photo")
                    cameraIntent()
                else if(userChoose == "Choose from Gallery")
                    galleryIntent(this)
            } else {
                Toast.makeText(this.context, "Something went wrong with permissions", Toast.LENGTH_LONG).show()
            }
    }
    private fun onCaptureImageResult(data: Intent){
        val bitmap = data.extras!!["data"] as Bitmap?

//        ImageUtils.saveImageToFile(bitmap!!)
        avatarEdit.setImageBitmap(bitmap)
        setAvatar()
    }

    private fun onSelectFromGalleryResult(data: Intent?){
        var bitmap: Bitmap? = null
        if (data != null) {
            try {
                bitmap = MediaStore.Images.Media.getBitmap(
                    requireContext().contentResolver,
                    data.data
                )
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        avatarImage.setImageBitmap(bitmap)
        setAvatar()
    }
}
