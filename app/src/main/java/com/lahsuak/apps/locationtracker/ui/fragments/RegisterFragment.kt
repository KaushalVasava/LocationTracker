package com.lahsuak.apps.locationtracker.ui.fragments

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.database.FirebaseDatabase
import com.lahsuak.apps.locationtracker.R
import com.lahsuak.apps.locationtracker.databinding.FragmentRegisterBinding
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.PhoneAuthProvider

import com.google.firebase.auth.PhoneAuthCredential


private const val TAG = "RegisterFragment"

class RegisterFragment : Fragment(R.layout.fragment_register) {

    private lateinit var binding: FragmentRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var verifyCredential: PhoneAuthCredential
    private lateinit var verificationId: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentRegisterBinding.bind(view)

        auth = FirebaseAuth.getInstance()

        binding.sendCode.setOnClickListener {
            val phNumber = binding.etPhoneNumber.text.toString()

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber("+91$phNumber")       // Phone number to verify
                .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
                .setActivity(requireActivity())                 // Activity (for callback binding)
                .setCallbacks(callbacks)          // OnVerificationStateChangedCallbacks
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
        binding.verify.setOnClickListener {
//            if(phoneNumber.text.toString()=="1111222200") {
//                val d = "Ov4dZxbvtXgRGyvHZHnyeyczlKV1"
//                val pref =
//                    requireContext().getSharedPreferences("LOGIN_DATA", MODE_PRIVATE).edit()
//                pref.putBoolean("registered", true)
//                pref.putString("phoneNo", phoneNumber.text.toString())
//                pref.apply()
//                val action =RegisterFragmentDirections.actionRegisterFragmentToHomeFragment(
//                        phoneNumber.text.toString()
//                    )
//                findNavController().navigate(action)
//            }else

            if (TextUtils.isEmpty(binding.otpView.text.toString())) {
                // if the OTP text field is empty display
                // a message to user to enter OTP
                Toast.makeText(requireContext(), "Please enter OTP", Toast.LENGTH_SHORT).show();
            } else {
                // if OTP field is not empty calling
                // method to verify the OTP.
                verifyCode(binding.otpView.text.toString())
            }
//            val credential = PhoneAuthProvider.getCredential(verificationId, code)
//
//            Log.d(TAG, "onVerificationCompleted:$verifyCredential")

        }

    }

    // below method is use to verify code from Firebase.
    private fun verifyCode(code: String) {
        // below line is used for getting getting
        // credentials from our verification id and code.
        val credential = PhoneAuthProvider.getCredential(verificationId, code)

        // after getting credential we are
        // calling sign in method.
        signInWithCredential(credential)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // This callback will be invoked in two situations:
            // 1 - Instant verification. In some cases the phone number can be instantly
            //     verified without needing to send or enter a verification code.
            // 2 - Auto-retrieval. On some devices Google Play services can automatically
            //     detect the incoming verification SMS and perform verification without
            //     user action.
            Log.d(TAG, "onVerificationCompleted:$credential")
//            verifyCredential = credential
            val code = credential.smsCode

            // checking if the code
            // is null or not.
            if (code != null) {
                // if the code is not null then
                // we are setting that code to
                // our OTP edittext field.
                binding.otpView.setText(code)

                // after setting this code
                // to OTP edittext field we
                // are calling our verifycode method.
                verifyCode(code)
            }
            //signInWithCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            // This callback is invoked in an invalid request for verification is made,
            // for instance if the the phone number format is not valid.
            Log.w(TAG, "onVerificationFailed", e)

            if (e is FirebaseAuthInvalidCredentialsException) {
                // Invalid request
            } else if (e is FirebaseTooManyRequestsException) {
                // The SMS quota for the project has been exceeded
            }

            // Show a message and update the UI
        }

        override fun onCodeSent(
            s: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            // The SMS verification code has been sent to the provided phone number, we
            // now need to ask the user to enter the code and then construct a credential
            // by combining the code with a verification ID.
            Log.d(TAG, "onCodeSent:$s")
            verificationId = s
            // Save verification ID and resending token so we can use them later
            //verificationID = verificationId
            //     resendToken = token
        }
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val pref =
                        requireContext().getSharedPreferences("LOGIN_DATA", MODE_PRIVATE).edit()
                    pref.putBoolean("registered", true)
                    pref.putString("phoneNo", binding.etPhoneNumber.text.toString())
                    pref.putString("userName", binding.etName.text.toString())
                    pref.apply()

                    val currentUserId = FirebaseAuth.getInstance().currentUser!!.uid
                    val userRef =
                        FirebaseDatabase.getInstance().reference.child("LTUsers")

                    val userMap = HashMap<String, Any>()
                    userMap["name"] = binding.etName.text.toString()
                    userMap["uid"] = currentUserId
                    userMap["phoneNumber"] =
                        binding.etPhoneNumber.text.toString()//1111222200//phNumber
                    userMap["lat"] = 18.2390
                    userMap["lng"] = 72.1342

                    userRef.child(currentUserId).setValue(userMap)
                        .addOnCompleteListener { task1 ->
                            if (task1.isSuccessful) {
                                val action =
                                    RegisterFragmentDirections.actionRegisterFragmentToHomeFragment(
                                    )
                                findNavController().navigate(action)
                                Log.d(TAG, "onComplete: verify successfully ")

                                Toast.makeText(
                                    requireContext(),
                                    "Location added successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                FirebaseAuth.getInstance().signOut()
                            }
                        }

                } else {
                    // if the code is not correct then we are
                    // displaying an error message to the user.
                    Toast.makeText(
                        requireContext(),
                        task.exception.toString(),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}