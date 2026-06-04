package com.abdil.taxi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import java.security.SecureRandom

class CallActivity : BaseActivity() {

    companion object {
        private const val TAG = "CallActivity"
        private const val PERMISSION_REQ_ID = 100

        // 🔴 TON APP ID AGORA (projet de test sans certificat)
        private const val AGORA_APP_ID = "6da841141d97433783d36f1839cbde41"
    }

    private lateinit var tvCallStatus: TextView
    private lateinit var tvCallDuration: TextView
    private lateinit var btnMute: Button
    private lateinit var btnSpeaker: Button
    private lateinit var btnSwitchCamera: Button
    private lateinit var btnEndCall: Button
    private lateinit var localVideoView: FrameLayout
    private lateinit var remoteVideoView: FrameLayout

    private var isMuted = false
    private var isSpeakerOn = false
    private var callDuration = 0
    private val handler = Handler(Looper.getMainLooper())
    private var durationRunnable: Runnable? = null

    private var agoraEngine: RtcEngine? = null
    private var channelName: String = ""
    private var uid: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        val driverName = intent.getStringExtra("DRIVER_NAME") ?: "Chauffeur"
        val rideId = intent.getLongExtra("RIDE_ID", -1)

        uid = SecureRandom().nextInt(1000000)
        channelName = "ride_${rideId}"

        Log.d(TAG, "Channel: $channelName, UID: $uid")

        tvCallStatus = findViewById(R.id.tvCallStatus)
        tvCallDuration = findViewById(R.id.tvCallDuration)
        btnMute = findViewById(R.id.btnMute)
        btnSpeaker = findViewById(R.id.btnSpeaker)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnEndCall = findViewById(R.id.btnEndCall)
        localVideoView = findViewById(R.id.localVideoView)
        remoteVideoView = findViewById(R.id.remoteVideoView)

        tvCallStatus.text = "Appel avec $driverName..."

        checkPermissionsAndJoin()

        btnMute.setOnClickListener {
            isMuted = !isMuted
            agoraEngine?.muteLocalAudioStream(isMuted)
            btnMute.text = if (isMuted) "🔇" else "🎤"
        }

        btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            agoraEngine?.setEnableSpeakerphone(isSpeakerOn)
            btnSpeaker.text = if (isSpeakerOn) "🔊" else "🔈"
        }

        btnSwitchCamera.setOnClickListener {
            agoraEngine?.switchCamera()
        }

        btnEndCall.setOnClickListener {
            leaveCall()
        }
    }

    private fun checkPermissionsAndJoin() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            joinChannel()
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQ_ID)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_ID && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            joinChannel()
        } else {
            Toast.makeText(this, "Permissions refusées", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun joinChannel() {
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = AGORA_APP_ID
                mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                        Log.d(TAG, "✅ Join channel success")
                        runOnUiThread {
                            Toast.makeText(this@CallActivity, "Canal rejoint", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        Log.d(TAG, "✅ Utilisateur rejoint: $uid")
                        runOnUiThread {
                            tvCallStatus.text = "En communication"
                            startCallDuration()
                        }
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        runOnUiThread {
                            Toast.makeText(this@CallActivity, "L'autre utilisateur a quitté", Toast.LENGTH_SHORT).show()
                            leaveCall()
                        }
                    }

                    override fun onError(err: Int) {
                        Log.e(TAG, "Erreur Agora: $err")
                        runOnUiThread {
                            Toast.makeText(this@CallActivity, "Erreur: $err", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            agoraEngine = RtcEngine.create(config)

            // 🔥 AUDIO OBLIGATOIRE
            agoraEngine?.enableAudio()
            agoraEngine?.setEnableSpeakerphone(true)

            // Vidéo optionnelle
            agoraEngine?.enableVideo()
            agoraEngine?.setupLocalVideo(VideoCanvas(localVideoView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
            agoraEngine?.startPreview()
            agoraEngine?.setupRemoteVideo(VideoCanvas(remoteVideoView, VideoCanvas.RENDER_MODE_HIDDEN, 0))

            // Rejoindre le canal
            agoraEngine?.joinChannel(null, channelName, "", uid)
            Log.d(TAG, "joinChannel appelé")

        } catch (e: Exception) {
            Log.e(TAG, "Erreur: ${e.message}")
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCallDuration() {
        durationRunnable = object : Runnable {
            override fun run() {
                callDuration++
                val minutes = callDuration / 60
                val seconds = callDuration % 60
                tvCallDuration.text = String.format("%02d:%02d", minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(durationRunnable!!)
    }

    private fun leaveCall() {
        durationRunnable?.let { handler.removeCallbacks(it) }
        agoraEngine?.leaveChannel()
        agoraEngine?.stopPreview()
        agoraEngine = null
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        leaveCall()
    }
}