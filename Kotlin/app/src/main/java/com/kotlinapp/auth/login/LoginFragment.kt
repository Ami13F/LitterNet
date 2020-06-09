package com.kotlinapp.auth.login

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.kotlinapp.MainActivity
import com.kotlinapp.R
import com.kotlinapp.auth.data.TokenHolder
import com.kotlinapp.utils.Result
import kotlinx.android.synthetic.main.login_fragment.*


class LoginFragment: Fragment() {
    private lateinit var viewModel: LoginViewModel

    override fun  onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View?{
        return inflater.inflate(R.layout.login_fragment, container, false)
    }
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(LoginViewModel::class.java)
        (activity as MainActivity).bottomNav.visibility = View.GONE
        setupLoginForm()
    }

    @SuppressLint("SetTextI18n")
    private fun setupLoginForm(){
        viewModel.validateFormState.observe(viewLifecycleOwner, Observer{
            val loginState = it?: return@Observer
            login.isEnabled = loginState.isDataValid
            if(loginState.usernameError != null){
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })
        viewModel.loginResult.observe(viewLifecycleOwner,Observer{
            val loginResult = it ?: return@Observer
            loading.visibility =View.GONE
            if(loginResult is Result.Success<TokenHolder>){
                findNavController().navigate(R.id.item_edit_fragment)
                (activity as MainActivity).bottomNav.visibility = View.VISIBLE

            }else if(loginResult is Result.Error){
                error_text.text = "Login error ${loginResult.exception!!.message}"
                if (error_text.text.contains("401")){
                    error_text.text = "Password or username is incorrect"
                }
                error_text.visibility = View.VISIBLE
            }
        })
        username.afterTextChanged {
            viewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }
        password.afterTextChanged {
            viewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }

        viewModel.loginDataChanged(
            username.text.toString(),
            password.text.toString()
        )

        login.setOnClickListener {
            loading.visibility = View.VISIBLE
            error_text.visibility = View.GONE

            viewModel.login(username.text.toString(), password.text.toString())
        }
        createAccountBtn.setOnClickListener{
            findNavController().navigate(R.id.create_account_fragment)
        }
    }

}
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}