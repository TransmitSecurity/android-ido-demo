package com.transmit.idodemo

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.transmit.authentication.*
import com.transmit.authentication.biometrics.*
import com.transmit.idodemo.databinding.ActivityMainBinding
import com.transmit.idosdk.*
import org.json.JSONObject

/**
 * @suppress (suppress from dokka gfm generation)
 */
class MainActivity : AppCompatActivity(),
    TSIdoCallback<TSIdoServiceResponse> {

    val TAG = "IdoDemo"

    private lateinit var binding: ActivityMainBinding
    private lateinit var idoCallback: TSIdoCallback<TSIdoServiceResponse>
    private lateinit var idvStartToken: String
    private lateinit var idvBaseEndpoint: String
    private lateinit var currentServiceResponse: TSIdoServiceResponse
    private lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        idoCallback = this
        initJourneyIdInputField()
        initStartJourneyButton()
    }

    override fun idoSuccess(result: TSIdoServiceResponse) {
        when(result) {
            is TSIdoServiceResponse -> processServiceResponse(result)
            else -> throw RuntimeException("Unsupported Ido service response.")
        }
    }


    override fun idoError(error: com.transmit.idosdk.TSIdoJourneyError, errorMsg: String?) {
        Log.e(TAG, "IDO error: " + error.error)
    }

    private fun initJourneyIdInputField() {
        binding.etJourneyId.setText(readSharedPrefs("journey_id", ""))
    }

    private fun initStartJourneyButton() {
        binding.btnStartJourney.setOnClickListener {
            saveInputVals()
            TSIdo.startJourney(binding.etJourneyId.text.toString(),
                TSIdoStartJourneyOptions(null,
                    binding.etFlowId.text.toString()), this) //"register", "auth", "native-biometrics"
        }
    }

    private fun saveInputVals() {
        writeSharedPrefs("journey_id", binding.etJourneyId.text.toString())
    }


    private fun handleJourneySuccessCompletion(result: TSIdoServiceResponse) {
        clearViews()
        val completedTextView = TextView(this)
        completedTextView.text = getText(R.string.journey_success_title)
        binding.mainLlFormContainer.addView(completedTextView)

    }

    private fun processServiceResponse(result: TSIdoServiceResponse) {
        currentServiceResponse = result
        when(result.journeyStepId) {
            TSIdoJourneyActionType.Information.type -> collectInformationAndSubmit(result)
            TSIdoJourneyActionType.Rejection.type -> {}
            TSIdoJourneyActionType.DebugBreak.type -> showDebugMessageAndSubmit(result)
            TSIdoJourneyActionType.RegisterDevice.type -> collectBindAcceptAndSubmit(result)
            TSIdoJourneyActionType.ValidateDeviceAction.type -> collectValidationAcceptAndSubmit(result)
            CustomIdoJourneyActionType.PHONE_INPUT.type -> collectPhoneInputAndSubmit(result)
            CustomIdoJourneyActionType.KBA_INPUT.type -> collectKBAInputAndSubmit(result)
            CustomIdoJourneyActionType.COLLECT_USERNAME.type -> collectUserIdAndSubmit(result)
            TSIdoJourneyActionType.WaitForAnotherDevice.type -> handleWaitForAnotherDeviceAndSubmit(result)
            TSIdoJourneyActionType.DrsTriggerAction.type -> handleTriggerAction(result)
            TSIdoJourneyActionType.IdentityVerification.type -> handleIdentityVerificationAndSubmit(result)
            TSIdoJourneyActionType.WebAuthnRegistration.type -> handleWebAuthnRegistrationAndSubmit(result)
            //IdoJourneyActionType.Authentication.type -> handleAuthenticationAndSubmit(result)
            TSIdoJourneyActionType.RegisterNativeBiometrics.type -> handleRegisterBiometrics(result)
            TSIdoJourneyActionType.AuthenticateNativeBiometrics.type -> handleAuthenticateBiometrics(result)
            TSIdoJourneyActionType.Rejection.type -> handleJourneyRejection(result)
            TSIdoJourneyActionType.Success.type -> handleJourneySuccessCompletion(result)
            else -> throw IllegalStateException("Unsupported Ido journey action type. " + result.journeyStepId)
        }
    }


    private fun showDebugMessageAndSubmit(result: TSIdoServiceResponse) {
        clearViews()
        showInformation(getString(R.string.debug_title), getString(R.string.debug_text), getString(R.string.debug_btn_accept))
    }

    private fun collectKBAInputAndSubmit(response: TSIdoServiceResponse) {
        clearViews()
        val title = TextView(this)
        title.text = getText(R.string.kba_input_title)
        binding.mainLlFormContainer.addView(title)
        val appData = (response.data as JSONObject).opt("app_data")
        appData?.let {
            val questions = (appData as JSONObject).optJSONArray("questions")
            val answers = ArrayList<EditText>()
            for (i in 0 until questions.length()) {
                val question = TextView(this)
                question.text = questions.getString(i)
                binding.mainLlFormContainer.addView(question)

                val answerInput = EditText(this)
                answerInput.inputType = InputType.TYPE_CLASS_TEXT
                answerInput.hint = getString(R.string.kba_input_your_answer_hint)
                binding.mainLlFormContainer.addView(answerInput)
                answers.add(answerInput)
            }

            val submitButton = Button(this)
            submitButton.text = getText(R.string.kba_input_button_submit_answers)
            submitButton.setOnClickListener{
                val responseData = ArrayList<KBAData>()
                for (i in 0 until answers.size) {
                    responseData.add(KBAData(questions.getString(i), answers[i].text.toString()))
                }
                TSIdo.submitClientResponse(TSIdoClientResponseOptionType.ClientInput.type, KBA(responseData), this) //TODO: retrofit converts json object and adds "nameValuePairs" what to do if client passes json object and not string
            }
            binding.mainLlFormContainer.addView(submitButton)
        }
    }


    private fun collectBindAcceptAndSubmit(result: TSIdoServiceResponse) {
        clearViews()
        val title = TextView(this)
        title.text = getText(R.string.crypto_bind_title)
        binding.mainLlFormContainer.addView(title)

        val text = TextView(this)
        text.text = getText(R.string.crypto_bind_approve)
        binding.mainLlFormContainer.addView(text)

        val button = Button(this)
        button.text = getText(R.string.crypto_bind_btn_accept)
        button.setOnClickListener{
            TSIdo.submitClientResponse(TSIdoClientResponseOptionType.ClientInput.type, null, this)
        }
        binding.mainLlFormContainer.addView(button)

        val escapeButton = getEscapeButton(result.clientResponseOptions)
        escapeButton?.let {
            binding.mainLlFormContainer.addView(it)
            it.setOnClickListener {
                val option = it.getTag(R.id.escapeViewTag) as Map.Entry<String, TSIdoClientResponseOption>
                TSIdo.submitClientResponse(option.key, Escape(option.value.id, null), this)
            }
        }

    }

    private fun collectValidationAcceptAndSubmit(result: TSIdoServiceResponse) {
        clearViews()
        showInformation(getString(R.string.device_validation_title), getString(R.string.device_validation_approve), getString(
            R.string.device_validation_btn_accept
        ))
    }

    private fun collectPhoneInputAndSubmit(
        response: TSIdoServiceResponse
    ) {
        clearViews()
        val title = TextView(this)
        title.text = getText(R.string.phone_input_enter_number)
        binding.mainLlFormContainer.addView(title)

        val phoneInput = EditText(this)
        phoneInput.inputType = InputType.TYPE_CLASS_PHONE
        phoneInput.hint = getString(R.string.phone_input_enter_number_hint)
        binding.mainLlFormContainer.addView(phoneInput)

        val submitButton = Button(this)
        submitButton.text = getText(R.string.phone_input_button_submit)
        submitButton.setOnClickListener{
            val responseData = PhoneResult(phoneInput.getText().toString())
            TSIdo.submitClientResponse(TSIdoClientResponseOptionType.ClientInput.type, responseData, this) //TODO: retrofit converts json object and adds "nameValuePairs" what to do if client passes json object and not string
        }
        binding.mainLlFormContainer.addView(submitButton)

        val escapeButton = getEscapeButton(response.clientResponseOptions)
        escapeButton?.let {
            binding.mainLlFormContainer.addView(it)
            it.setOnClickListener {
                val option = it.getTag(R.id.escapeViewTag) as Map.Entry<String, TSIdoClientResponseOption>
                TSIdo.submitClientResponse(TSIdoClientResponseOptionType.Custom.type, Escape(option.value.id, PhoneResult(phoneInput.getText().toString())), this)
            }
        }
    }

    private fun collectUserIdAndSubmit(idoServiceResponse: TSIdoServiceResponse) {
        clearViews()
        val title = TextView(this)
        title.text = getText(R.string.collect_userid_body)
        binding.mainLlFormContainer.addView(title)

        val userIdInput = EditText(this)
        userIdInput.inputType = InputType.TYPE_CLASS_TEXT
        userIdInput.hint = getString(R.string.collect_userid_title)
        binding.mainLlFormContainer.addView(userIdInput)

        val submitButton = Button(this)
        submitButton.text = getText(R.string.collect_userid_submit)
        submitButton.setOnClickListener{
            userId = userIdInput.text.toString()
            val responseData = UserIdResult(userIdInput.text.toString())
            TSIdo.submitClientResponse(TSIdoClientResponseOptionType.ClientInput.type, responseData, this)
        }
        binding.mainLlFormContainer.addView(submitButton)
    }

    private fun collectInformationAndSubmit(result: TSIdoServiceResponse) {
        clearViews()
        showInformation((result.data as JSONObject).optString("title"), (result.data as JSONObject).optString("text"), (result.data as JSONObject).optString("button_text"))
    }

    private fun showInformation(titleText: String?, bodyText: String?, buttonText: String?) {
        if (!titleText.isNullOrEmpty()) {
            val title = TextView(this)
            title.text = titleText
            binding.mainLlFormContainer.addView(title)
        }

        if (!bodyText.isNullOrEmpty()) {
            val text = TextView(this)
            text.text = bodyText
            binding.mainLlFormContainer.addView(text)
        }

        if (!buttonText.isNullOrEmpty()) {
            val button = Button(this)
            button.text = buttonText
            button.setOnClickListener{
                TSIdo.submitClientResponse(TSIdoClientResponseOptionType.ClientInput.type, null, this)
            }
            binding.mainLlFormContainer.addView(button)
        }
    }

    private fun handleWaitForAnotherDeviceAndSubmit(result: TSIdoServiceResponse) {
        clearViews()
        val resultJson = result.data as JSONObject
        var titleText = resultJson.optString("title") ?: getString(R.string.csm_title)
        if (!titleText.isNullOrEmpty()) {
            val title = TextView(this)
            title.text = titleText
            binding.mainLlFormContainer.addView(title)
        }

        val bodyText = resultJson.optString("text") ?: getString(R.string.csm_text)
        if (!bodyText.isNullOrEmpty()) {
            val text = TextView(this)
            text.text = bodyText
            binding.mainLlFormContainer.addView(text)
        }

        val buttonText = getString(R.string.csm_poll_btn)
        if (!buttonText.isNullOrEmpty()) {
            val button = Button(this)
            button.text = buttonText
            button.setOnClickListener{
                TSIdo.submitClientResponse(TSIdoClientResponseOptionType.ClientInput.type, null, this)
            }
            binding.mainLlFormContainer.addView(button)
        }

        showEscapesAndSubmitResponse(result)
    }

    private fun showEscapesAndSubmitResponse(result: TSIdoServiceResponse) {
        val escapeButton = getEscapeButton(result.clientResponseOptions)
        escapeButton?.let {
            binding.mainLlFormContainer.addView(it)
            it.setOnClickListener {
                val option = it.getTag(R.id.escapeViewTag) as Map.Entry<String, TSIdoClientResponseOption>
                TSIdo.submitClientResponse(option.key, Escape(option.value.id, null), this)
            }
        }
    }

    private fun handleJourneyRejection(result: TSIdoServiceResponse) {
        clearViews()
        val completedTextView = TextView(this)
        completedTextView.text = getText(R.string.journey_rejected_title)
        binding.mainLlFormContainer.addView(completedTextView)
    }


    private fun clearViews() {
        for (view in binding.mainLlFormContainer.children) {
            view.visibility = View.GONE
        }
    }

    private fun getEscapeButton(clientResponseOptions: Map<String, TSIdoClientResponseOption>?): Button? {
        clientResponseOptions?.let {
            for (option in it) {
                return when (option.value.type) {
                    TSIdoClientResponseOptionType.Custom -> getEscapeButton(option)
                    TSIdoClientResponseOptionType.Cancel -> getEscapeButton(option)
                    else -> null
                }
            }
        }

        return null
    }

    private fun getEscapeButton(option: Map.Entry<String, TSIdoClientResponseOption>): Button {
        val button = Button(this)
        button.text = option.value.label
        button.setTag(R.id.escapeViewTag, option)
        return button
    }


    private fun handleTriggerAction(result: TSIdoServiceResponse) {
        clearViews()
        val title = TextView(this)
        title.text = getText(R.string.drs_title)
        binding.mainLlFormContainer.addView(title)

        val text = TextView(this)
        text.text = getText(R.string.drs_text)
        binding.mainLlFormContainer.addView(text)

        val button = Button(this)
        button.text = getText(R.string.drs_btn)
        button.setOnClickListener{
            triggerActionAndSubmit(result)
        }
        binding.mainLlFormContainer.addView(button)
    }

    private fun triggerActionAndSubmit(result: TSIdoServiceResponse) {
        val action = (result.data as JSONObject).optString("action_type")
        val callback = this //TODO: better way
        if (!action.isNullOrEmpty()) {
         //TODO
        }
    }

    private fun handleIdentityVerificationAndSubmit(result: TSIdoServiceResponse) {
        clearViews()
        //TODO
    }

    private fun handleWebAuthnRegistrationAndSubmit(result: TSIdoServiceResponse) {
        clearViews()
        val userName = (result.data as JSONObject).optString("username")
        val displayName = (result.data as JSONObject).optString("display_name")
        val title = TextView(this)
        title.text = getText(R.string.webauthn_register_title)
        binding.mainLlFormContainer.addView(title)

        val text = TextView(this)
        text.text = getString(R.string.webauthn_register_text, displayName, userName)
        binding.mainLlFormContainer.addView(text)

        val button = Button(this)
        button.text = getText(R.string.webauthn_register_btn)
        button.setOnClickListener{
            startWebAuthnRegistrationAndSubmit(result, userName, displayName)
        }
        binding.mainLlFormContainer.addView(button)
    }

    private fun startWebAuthnRegistrationAndSubmit(result: TSIdoServiceResponse, userName: String?, displayName: String?) {
        showLoading()
        val callback = this
        if (!userName.isNullOrEmpty()) {
            TSAuthentication.registerWebAuthn(this, userName, if (displayName.isNullOrEmpty()) null else displayName, object: TSAuthCallback<RegistrationResult, TSWebAuthnRegistrationError>{
                override fun error(error: TSWebAuthnRegistrationError) {
                    showError(error.toString())
                }

                override fun success(result: RegistrationResult) {
                    TSIdo.submitClientResponse(TSIdoClientResponseOptionType.ClientInput.type, WebAuthnRegisterResult(result.result()), callback) //TODO: retrofit converts json object and adds "nameValuePairs" what to do if client passes json object and not string
                }
            })
        }
    }

    private fun handleAuthenticationAndSubmit(result: TSIdoServiceResponse) {
        clearViews()
        val userName = (result.data as JSONObject).optString("username")
        val methods = (result.data as JSONObject).optJSONArray("methods")

        (0 until methods.length()).forEach {
            val method = methods.getJSONObject(it).optString("type")
            if (method == "webauthn") {
                val title = TextView(this)
                title.text = getText(R.string.webauthn_auth_title)
                binding.mainLlFormContainer.addView(title)

                val text = TextView(this)
                text.text = getString(R.string.webauthn_auth_text, userName)
                binding.mainLlFormContainer.addView(text)

                val button = Button(this)
                button.text = getText(R.string.webauthn_register_btn)
                button.setOnClickListener{
                    startWebAuthnAuthenticationdSubmit(result, userName)
                }
                binding.mainLlFormContainer.addView(button)
            }

        }


    }

    private fun startWebAuthnAuthenticationdSubmit(response: TSIdoServiceResponse, userName: String) {
        showLoading()
        val callback = this
        if (!userName.isNullOrEmpty()) {
            TSAuthentication.authenticateWebAuthn(this, userName, object: TSAuthCallback<AuthenticationResult, TSWebAuthnAuthenticationError>{
                override fun error(error: TSWebAuthnAuthenticationError) {
                    showError(error.toString())
                }

                override fun success(result: AuthenticationResult) {
                    TSIdo.submitClientResponse(TSIdoClientResponseOptionType.ClientInput.type, AuthenticationActionResult("webauthn", result.result()), callback) //TODO: retrofit converts json object and adds "nameValuePairs" what to do if client passes json object and not string
                }
            })
        }
    }


    private fun handleRegisterBiometrics(idoServiceResponse: TSIdoServiceResponse) {
        clearViews()

        val title = TextView(this)
        title.text = getText(R.string.biometrics_register_title)
        binding.mainLlFormContainer.addView(title)

        val text = TextView(this)
        text.text = getString(R.string.biometrics_register_text, userId)
        binding.mainLlFormContainer.addView(text)

        val button = Button(this)
        button.text = getText(R.string.webauthn_register_btn)
        button.setOnClickListener{
            registerBiometricsAndSubmit()
        }
        binding.mainLlFormContainer.addView(button)
    }

    private fun registerBiometricsAndSubmit() {
        val callback = this
        TSAuthentication.registerNativeBiometrics(this, userId, object: TSAuthCallback<TSBiometricsRegistrationResult, TSBiometricsRegistrationError>{
            override fun error(error: TSBiometricsRegistrationError) {
                Log.e(TAG, "Biometrics registration error: " + error.eM ,error.tw)
            }

            override fun success(result: TSBiometricsRegistrationResult) {
                Log.d(TAG, "Biometrics registration success")
                TSIdo.submitClientResponse(TSIdoClientResponseOptionType.ClientInput.type, BiometricsRegistrationResult(result.keyId(), result.publicKey()), callback)
            }
        })
    }


    private fun handleAuthenticateBiometrics(idoServiceResponse: TSIdoServiceResponse) {
        clearViews()

        val title = TextView(this)
        title.text = getText(R.string.biometrics_auth_title)
        binding.mainLlFormContainer.addView(title)

        val text = TextView(this)
        text.text = getString(R.string.biometrics_auth_text, userId)
        binding.mainLlFormContainer.addView(text)

        val button = Button(this)
        button.text = getText(R.string.biometrics_auth_btn)
        button.setOnClickListener{
            authenticateBiometricsAndSubmit(idoServiceResponse)
        }
        binding.mainLlFormContainer.addView(button)
    }

    private fun authenticateBiometricsAndSubmit(idoServiceResponse: TSIdoServiceResponse) {
        val userIdentifier = (idoServiceResponse.data as JSONObject).optString("user_identifier")
        val challenge = (idoServiceResponse.data as JSONObject).optString("biometrics_challenge")
        val callback = this
        TSAuthentication.authenticateNativeBiometrics(this, userIdentifier, challenge, BiometricPromptTexts("Authentication", "Place your finger on the sensor", "Abort"),
        object: TSAuthCallback<TSBiometricsAuthResult, TSBiometricsAuthError> {
            override fun error(error: TSBiometricsAuthError) {
                Log.e(TAG, "Biometrics authentication error: " + error.eM ,error.tw)
                showError(error.eM ?: "Error occurred during authentication.")
            }

            override fun success(result: TSBiometricsAuthResult) {
                Log.d(TAG, "Biometrics authentication success")
                TSIdo.submitClientResponse(TSIdoClientResponseOptionType.ClientInput.type, BiometricsAuthenticationResult(result.keyId(), userIdentifier, result.signature()), callback)
            }
        })
    }

    private fun showError(error: String) {
        clearViews()
        val errorTextView = TextView(this)
        errorTextView.text = error
        binding.mainLlFormContainer.addView(errorTextView)
    }

    private fun showLoading() {
        val progressBar = ProgressBar(this)
        binding.mainLlFormContainer.addView(progressBar)
    }

    private fun writeSharedPrefs(key: String, value: String) {
        val sharedPref = this.getSharedPreferences(
            getString(R.string.preference_file_key), Context.MODE_PRIVATE)
        with (sharedPref.edit()) {
            putString(key, value)
            apply()
        }
    }

    private fun readSharedPrefs(key: String, defaultVal: String = ""): String {
        val sharedPref = this?.getSharedPreferences(
            getString(R.string.preference_file_key), Context.MODE_PRIVATE)
        return sharedPref?.getString(key, defaultVal) ?: defaultVal
    }
}


enum class CustomIdoJourneyActionType(val type: String) {
    PHONE_INPUT("phone_input"),
    KBA_INPUT("kba_input"),
    COLLECT_USERNAME("collect_username")
}

data class KBAData(val question: String, val answer: String)
data class KBA(val kba: ArrayList<KBAData>)
data class PhoneResult(val phone: String)
data class Escape(val escape_id: String, val escape_params: Any?)
data class DrsActionToken(val action_token: String)
data class IDVResult(val payload: IDVResponsePayload)
data class IDVResponsePayload(val sessionId: String, val state: String)
data class WebAuthnRegisterResult(val webauthn_encoded_result: String)

data class AuthenticationActionResult(val type: String, val webauthn_encoded_result: String?)
data class UserIdResult(val username: String)
data class BiometricsRegistrationResult(val publicKeyId: String, val publicKey: String, val os: String = "Android")

data class BiometricsAuthenticationResult(val publicKeyId: String, val userIdentifier: String, val signedChallenge: String)
