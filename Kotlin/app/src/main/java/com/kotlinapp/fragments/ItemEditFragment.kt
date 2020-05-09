package com.kotlinapp.fragments

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
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.kotlinapp.R
import com.kotlinapp.auth.data.AuthRepository
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
import com.kotlinapp.viewModels.ItemEditViewModel
import kotlinx.android.synthetic.main.create_account_fragment.*
import kotlinx.android.synthetic.main.create_account_fragment.progress
import kotlinx.android.synthetic.main.item_edit_fragment.*
import java.io.IOException


class ItemEditFragment : Fragment() {

    private lateinit var viewModel: ItemEditViewModel

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
        return inflater.inflate(R.layout.item_edit_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG,"Setting initial values...")
        avatarEdit.setImageBitmap(ImageUtils.arrayToBitmap(AuthRepository.currentPlayer!!.avatar.data))
        //countryName-Code -> getting code
        countryEdit.setCountryForNameCode(AuthRepository.currentPlayer!!.country.split("-")[1])
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.v(TAG, "onActivityCreated")
        viewModel = ViewModelProviders.of(this).get(ItemEditViewModel::class.java)

        setupPasswordState()
        setupViewModel()
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
        viewModel.passwordState.observe(this, Observer {passState->
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

        viewModel.playerUpdate.observe(this, Observer {player->
            avatarEdit.setImageBitmap(ImageUtils.arrayToBitmap(AuthRepository.currentPlayer!!.avatar.data))
            this.player = player
        })

        viewModel.fetching.observe(this, Observer { fetching ->
            Log.v(TAG, "update fetching")
            progress.visibility = if (fetching) View.VISIBLE else View.GONE
        })
        viewModel.fetchingError.observe(this, Observer { exception ->
            if (exception != null) {
                Log.v(TAG, "update fetching error")
                val message = "Fetching exception ${exception.message}"
                val parentActivity = activity?.parent
                if (parentActivity != null) {
                    Toast.makeText(parentActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
        viewModel.completed.observe(this, Observer { completed ->
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
        val bitmap = data.extras!!["data"] as Bitmap?

        ImageUtils.saveImageToFile(bitmap!!)
        avatarEdit.setImageBitmap(bitmap)
        setAvatar()
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
        setAvatar()
    }
}
