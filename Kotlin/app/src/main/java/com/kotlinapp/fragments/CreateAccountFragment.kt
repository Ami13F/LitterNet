package com.kotlinapp.fragments


import android.app.Activity.RESULT_OK
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
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.kotlinapp.R
import com.kotlinapp.auth.data.User
import com.kotlinapp.auth.login.afterTextChanged
import com.kotlinapp.entities.AvatarHolder
import com.kotlinapp.entities.Player
import com.kotlinapp.utils.TAG
import com.kotlinapp.utils.ImageUtils
import com.kotlinapp.utils.ImageUtils.FILE_SELECTED
import com.kotlinapp.utils.ImageUtils.REQUEST_CAMERA
import com.kotlinapp.utils.ImageUtils.cameraIntent
import com.kotlinapp.utils.ImageUtils.galleryIntent
import com.kotlinapp.utils.Permissions
import com.kotlinapp.viewModels.AccountViewModel
import kotlinx.android.synthetic.main.create_account_fragment.*
import java.io.IOException


class CreateAccountFragment : Fragment() {
    private lateinit var viewModel: AccountViewModel

    private var userChoose: String = ""

    private  var avatar: AvatarHolder = AvatarHolder()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.v(TAG, "onCreateView")
        return inflater.inflate(R.layout.create_account_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.v(TAG, "onActivityCreated")
        viewModel = ViewModelProviders.of(this).get(AccountViewModel::class.java)

        setupViewModel()
        setupValidation()

        avatarImage.setOnClickListener{
            avatarChooser()
        }

        saveAccountBtn.isEnabled = false

        saveAccountBtn.setOnClickListener {
            Log.v(TAG, "save item")

            val email = emailField.text.toString()
            val username = usernameField.text.toString()
            val country = countryField.selectedCountryName + "-" + countryField.selectedCountryNameCode

            val password = passwordField.text.toString()

            avatar.data = ImageUtils.bitmapToArray((avatarImage.drawable as BitmapDrawable).bitmap)

            viewModel.saveAccount( User(email, username, password), Player(avatar, country))
        }
    }

    private fun setupValidation(){
        viewModel.emailExists.observe(this, Observer {
            if (it){
                viewModel.validateCreateAccount(
                    emailField.text.toString(),
                    usernameField.text.toString(),
                    passwordField.text.toString(),
                    validateEmail = true,
                    validateUsername = false)
            }
        })

        viewModel.userNameExists.observe(this, Observer {
            if(it){
                viewModel.validateCreateAccount(
                    emailField.text.toString(),
                    usernameField.text.toString(),
                    passwordField.text.toString(),
                    validateEmail = false,
                    validateUsername = true)
            }
        })

        viewModel.validFormState.observe(this, Observer { validState->
            saveAccountBtn.isEnabled = validState.isDataValid

            if(validState.emailError != null){
                emailField.error = getString(validState.emailError)
            }
            if(validState.usernameError != null){
                usernameField.error = getString(validState.usernameError)
            }
            if(validState.passwordError != null){
                passwordField.error = getString(validState.passwordError)
            }
        })

        // If is done typing validate
        emailField.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus){
                Log.d(TAG, "has focus stopped..")
                viewModel.findOne(emailField.text.toString())
                viewModel.validateCreateAccount(
                    emailField.text.toString(),
                    usernameField.text.toString(),
                    passwordField.text.toString(),
                    validateEmail = true,
                    validateUsername = false
                )
            }
        }

        usernameField.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                Log.d(TAG, "has focus stopped..")
                viewModel.findUserName(usernameField.text.toString())
                viewModel.validateCreateAccount(
                    emailField.text.toString(),
                    usernameField.text.toString(),
                    passwordField.text.toString(),
                    validateEmail = false,
                    validateUsername = true
                )
            }
        }

        passwordField.afterTextChanged {
            viewModel.validateCreateAccount(
                emailField.text.toString(),
                usernameField.text.toString(),
                passwordField.text.toString(),
                validateEmail = false,
                validateUsername = false
            )
        }
    }

    private fun setupViewModel() {
        viewModel.completed.observe(this, Observer { completed ->
            if (completed) {
                Log.v(TAG, "Completed, navigate back")
                Toast.makeText(this.activity, "Create account succeed", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == FILE_SELECTED)
                onSelectFromGalleryResult(data)
            else if (requestCode == REQUEST_CAMERA)
                onCaptureImageResult(data!!)
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
                if (result) cameraIntent(this)
            } else if (types[item] == "Choose from Gallery") {
                userChoose = "Choose from Gallery"
                if (result) galleryIntent(this)
            } else if (types[item] == "Cancel") {
                dialog.dismiss()
            }
        }
        builder.show()
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
                    cameraIntent(this)
                else if(userChoose == "Choose from Gallery")
                    galleryIntent(this)
            } else {
                Toast.makeText(this.context, "Something went wrong with permissions", Toast.LENGTH_LONG).show()
            }
    }

    private fun onCaptureImageResult(data: Intent){
        Log.d(TAG, "start set bitmap")
        val bitmap = data.extras!!["data"] as Bitmap?

        ImageUtils.saveImageToFile(bitmap!!)
        Log.d(TAG, "set bitmap")
        avatarImage.setImageBitmap(bitmap)
    }


    private fun onSelectFromGalleryResult(data: Intent?){
        var bitmap: Bitmap? = null
        if (data != null) {
            try {
                bitmap = MediaStore.Images.Media.getBitmap(
                    context!!.contentResolver,
                    data.data
                )
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        avatarImage.setImageBitmap(bitmap)
    }
}