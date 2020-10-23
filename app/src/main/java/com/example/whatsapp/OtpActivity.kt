package com.example.whatsapp

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.android.synthetic.main.activity_otp.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

const val PHONE_NUMBER="phoneNumber"

class OtpActivity : AppCompatActivity(), View.OnClickListener {
    lateinit var callbacks:PhoneAuthProvider.OnVerificationStateChangedCallbacks
    var phoneNumber:String?=null
    var mVerificationId:String?=null
    private lateinit var progressDialog:ProgressDialog
    private var mCounterDown:CountDownTimer?=null
    var mResendToken:PhoneAuthProvider.ForceResendingToken?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)
        initView()
        startVerify()


    }

    private fun startVerify() {
         PhoneAuthProvider.getInstance().verifyPhoneNumber(
             phoneNumber!!,
             60,
             TimeUnit.SECONDS,
             this,
             callbacks
         )

        showTimer(60000)

        progressDialog=createProgressDialog("Sending a verification code",false)
        progressDialog.show()

    }

    private fun showTimer(milliSecInFuture: Long) {
        resendBtn.isEnabled=false
        mCounterDown=object:CountDownTimer(milliSecInFuture,1000){
            override fun onFinish() {
                resendBtn.isEnabled=true
                counterTv.isVisible=false

            }

            override fun onTick(millisUntilFinished: Long) {
                counterTv.isVisible=true
                counterTv.text=getString(R.string.second_remaining,millisUntilFinished/1000)
            }

        }.start()


    }

    override fun onDestroy() {
        super.onDestroy()
        if(mCounterDown!=null)
        {
            mCounterDown!!.cancel()
        }

    }

    private fun initView() {
      phoneNumber=intent.getStringExtra(PHONE_NUMBER)
        verifyTv.text=getString(R.string.verify_number,phoneNumber)
        setSpannableString()
        verificationBtn.setOnClickListener(this)
        resendBtn.setOnClickListener(this)
        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // This callback will be invoked in two situations:
                // 1 - Instant verification. In some cases the phone number can be instantly
                //     verified without needing to send or enter a verification code.
                // 2 - Auto-retrieval. On some devices Google Play services can automatically
                //     detect the incoming verification SMS and perform verification without
                //     user action.
                //Log.d(TAG, "onVerificationCompleted:$credential")
                if(::progressDialog.isInitialized)
                {
                    progressDialog.dismiss()
                }
                val smsCode=credential.smsCode
                if(!smsCode.isNullOrBlank())
                    sentcodeEt.setText(smsCode)

               signInWithPhoneAuthCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                // This callback is invoked in an invalid request for verification is made,
                // for instance if the the phone number format is not valid.
              //  Log.w(TAG, "onVerificationFailed", e)

                if (e is FirebaseAuthInvalidCredentialsException) {
                    // Invalid request
                    // ...
                } else if (e is FirebaseTooManyRequestsException) {
                    // The SMS quota for the project has been exceeded
                    // ...
                }
                Log.e("ERROR_FIREBASE",e.localizedMessage)
                notifyUserAndRetry("Your Phone Number might be wrong or connection error.Retry again!")

                // Show a message and update the UI
                // ...
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                // The SMS verification code has been sent to the provided phone number, we
                // now need to ask the user to enter the code and then construct a credential
                // by combining the code with a verification ID.
             //   Log.d(TAG, "onCodeSent:$verificationId")

                // Save verification ID and resending token so we can use them later
                mVerificationId = verificationId
                mResendToken = token

                // ...
            }
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {

        val mAuth=FirebaseAuth.getInstance()
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener{
              if(it.isSuccessful)
              {
                    startActivity(
                        Intent(this,SignUpActivity::class.java)
                    )
                  finish()

              }else{
                  notifyUserAndRetry("Your Phone Number verification failed.Try Again")

              }
            }
    }
    private fun notifyUserAndRetry(message: String)
    {
        MaterialAlertDialogBuilder(this).apply {
            setMessage(message)
            setPositiveButton("ok"){_,_->
                showLoginActivity()
            }
            setNegativeButton("Cancel"){dialog,_->
                dialog.dismiss()
            }
            setCancelable(false)
            create()
            show()

        }
    }


    private fun setSpannableString() {
         //   val span=SpannableString(getString(R.string.Waiting_text , phoneNumber))
        val span=SpannableString(getString(R.string.Waiting,phoneNumber))
        val clickableSpan = object:ClickableSpan(){
            override fun onClick(widget: View) {
                showLoginActivity()

            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText=false
                ds.color=ds.linkColor
            }

        }
        span.setSpan(clickableSpan,span.length-13,span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        waitingTv.text = span
        waitingTv.movementMethod=LinkMovementMethod.getInstance()

    }
    private fun showLoginActivity(){
        startActivity(Intent(this,LoginActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK ))



    }

    override fun onBackPressed() {

        //super.onBackPressed()
    }

    override fun onClick(v: View?) {
        when(v){
            verificationBtn->{

                val code=sentcodeEt.text.toString()
                if(code.isNotEmpty() && !mVerificationId.isNullOrBlank()){

                    progressDialog=createProgressDialog("Please wait....",isCancelable = false)
                    progressDialog.show()

                val credential=PhoneAuthProvider.getCredential(mVerificationId!!,code)
                signInWithPhoneAuthCredential(credential)
            }
        }
                resendBtn->{

                val code=sentcodeEt.text.toString()
                if(mResendToken!=null ){
                    showTimer(60000)

                    progressDialog=createProgressDialog("Sending a verification code ",isCancelable = false)
                    progressDialog.show()

                    PhoneAuthProvider.getInstance().verifyPhoneNumber(
                        phoneNumber!!,
                        60,
                        TimeUnit.SECONDS,
                        this,
                        callbacks,
                        mResendToken
                    )

                }
            }



        }
    }
}

fun Context.createProgressDialog(message:String,isCancelable:Boolean):ProgressDialog{
    return ProgressDialog(this).apply {
        setCancelable(false)
        setMessage(message)
        setCanceledOnTouchOutside(false)


    }
}