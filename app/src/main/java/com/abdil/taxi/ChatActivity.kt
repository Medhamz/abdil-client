package com.abdil.taxi

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abdil.taxi.model.MessageResponse
import com.abdil.taxi.network.RetrofitClient
import com.abdil.taxi.network.SendMessageRequest
import com.bumptech.glide.Glide
import com.github.dhaval2404.imagepicker.ImagePicker
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : BaseActivity() {

    companion object {
        private const val TAG = "ChatDebug"
        // 🔧 ADRESSE IP CORRECTE (même IP que le chauffeur)
        private const val BASE_URL = "http://192.168.11.105:8080"
    }

    private lateinit var tvRideInfo: TextView
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnVoice: ImageButton
    private lateinit var btnCall: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTyping: TextView
    private lateinit var btnImage: ImageButton
    private lateinit var btnLiveSharing: ImageButton

    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<MessageResponse>()
    private var rideId: Long = -1
    private var driverId: Long = -1
    private var clientId: Long = -1

    private val handler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var mediaPlayer: MediaPlayer? = null

    private var typingHandler = Handler(Looper.getMainLooper())
    private var typingRunnable: Runnable? = null
    private var isTyping = false

    private var shouldAutoScroll = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        rideId = intent.getLongExtra("RIDE_ID", -1)
        driverId = intent.getLongExtra("DRIVER_ID", -1)
        clientId = intent.getLongExtra("CLIENT_ID", -1)

        Log.d(TAG, "========================================")
        Log.d(TAG, "onCreate: rideId=$rideId")
        Log.d(TAG, "onCreate: driverId=$driverId")
        Log.d(TAG, "onCreate: clientId=$clientId")
        Log.d(TAG, "BASE_URL = $BASE_URL")
        Log.d(TAG, "========================================")

        if (rideId == -1L || driverId == -1L) {
            Toast.makeText(this, "Erreur: IDs invalides", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        setupScrollListener()
        setupClickListeners()
        loadMessages()
        startPolling()
    }

    private fun initViews() {
        tvRideInfo = findViewById(R.id.tvRideInfo)
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnVoice = findViewById(R.id.btnVoice)
        btnCall = findViewById(R.id.btnCall)
        progressBar = findViewById(R.id.progressBar)
        tvTyping = findViewById(R.id.tvTyping)
        btnImage = findViewById(R.id.btnImage)
        btnLiveSharing = findViewById(R.id.btnLiveSharing)

        tvRideInfo.text = "Course #$rideId"
        tvTyping.visibility = View.GONE
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messages, "CLIENT",
            onVoicePlay = { message ->
                if (message.messageType == "VOICE" && message.mediaUrl != null) {
                    playAudio(message.mediaUrl)
                }
            },
            onAddReaction = { message ->
                showReactionDialog(message.id, message.reaction)
            }
        )
        rvMessages.layoutManager = LinearLayoutManager(this)
        rvMessages.adapter = messageAdapter
    }

    private fun setupScrollListener() {
        rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount
                shouldAutoScroll = (lastVisiblePosition >= totalItemCount - 1)
            }
        })
    }

    private fun setupClickListeners() {
        btnSend.setOnClickListener {
            val content = etMessage.text.toString().trim()
            if (content.isNotEmpty()) {
                sendMessage(content, "TEXT", null)
                etMessage.text.clear()
                resetTypingIndicator()
            } else {
                Toast.makeText(this, "Message vide", Toast.LENGTH_SHORT).show()
            }
        }

        btnVoice.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                checkAudioPermissionAndStart()
            }
        }

        btnCall.setOnClickListener {
            val intent = Intent(this, CallActivity::class.java)
            intent.putExtra("DRIVER_ID", driverId)
            intent.putExtra("DRIVER_NAME", "Chauffeur")
            intent.putExtra("RIDE_ID", rideId)
            startActivity(intent)
        }

        btnImage.setOnClickListener {
            ImagePicker.with(this)
                .crop()
                .compress(1024)
                .maxResultSize(1080, 1080)
                .start()
        }

        btnLiveSharing.setOnClickListener {
            if (rideId != -1L) {
                val intent = Intent(this, LiveSharingActivity::class.java)
                intent.putExtra("RIDE_ID", rideId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Aucune course active", Toast.LENGTH_SHORT).show()
            }
        }

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && s.isNotEmpty() && !isTyping) {
                    isTyping = true
                    showTypingIndicator()
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) {
                    resetTypingIndicator()
                }
            }
        })
    }

    private fun showTypingIndicator() {
        tvTyping.visibility = View.VISIBLE
        tvTyping.text = "✏️ Vous écrivez..."
        tvTyping.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))

        typingRunnable?.let { typingHandler.removeCallbacks(it) }
        typingRunnable = Runnable {
            resetTypingIndicator()
        }
        typingHandler.postDelayed(typingRunnable!!, 3000)
    }

    private fun resetTypingIndicator() {
        isTyping = false
        tvTyping.visibility = View.GONE
        typingRunnable?.let { typingHandler.removeCallbacks(it) }
    }

    private fun checkAudioPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    private fun startRecording() {
        try {
            audioFile = File(cacheDir, "audio_${System.currentTimeMillis()}.3gp")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            btnVoice.setImageResource(android.R.drawable.presence_audio_online)
            btnVoice.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            Toast.makeText(this, "🎤 Enregistrement...", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({ if (isRecording) stopRecording() }, 30000)
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur enregistrement", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            btnVoice.setImageResource(android.R.drawable.ic_btn_speak_now)
            btnVoice.setColorFilter(null)
            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) uploadAudio(file)
                else Toast.makeText(this, "Enregistrement vide", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur arrêt: ${e.message}")
        }
    }

    private fun uploadAudio(file: File) {
        progressBar.visibility = View.VISIBLE
        try {
            val mediaType = "audio/3gpp".toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            RetrofitClient.apiService.uploadAudio(body).enqueue(object : Callback<Map<String, String>> {
                override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val url = response.body()!!["url"] ?: ""
                        if (url.isNotEmpty()) sendMessage("🎤 Message vocal", "VOICE", url)
                    }
                }
                override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ChatActivity, "Erreur audio", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val uri = data.data
            uri?.let {
                uploadImage(it)
            }
        }
    }

    private fun uploadImage(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        val file = File(getRealPathFromUri(uri))

        val mediaType = "image/jpeg".toMediaTypeOrNull()
        val requestFile = file.asRequestBody(mediaType)
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

        RetrofitClient.apiService.uploadImage(body).enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val url = response.body()!!["url"] ?: ""
                    sendMessage("📷 Image", "IMAGE", url)
                }
            }
            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@ChatActivity, "Erreur upload image", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun getRealPathFromUri(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.moveToFirst()
        val columnIndex = cursor?.getColumnIndex(MediaStore.Images.Media.DATA)
        val path = columnIndex?.let { cursor.getString(it) } ?: uri.path ?: ""
        cursor?.close()
        return path
    }

    // 🔧 FONCTION playAudio CORRIGÉE
    private fun playAudio(url: String) {
        try {
            mediaPlayer?.release()

            Log.d(TAG, "=== PLAY AUDIO ===")
            Log.d(TAG, "URL reçue: '$url'")

            if (url.isNullOrEmpty()) {
                Log.e(TAG, "URL vide!")
                Toast.makeText(this, "URL audio vide", Toast.LENGTH_SHORT).show()
                return
            }

            // Construire l'URL complète avec la bonne IP
            val fullUrl = when {
                url.startsWith("http") -> url
                url.startsWith("/") -> "$BASE_URL$url"
                else -> "$BASE_URL/$url"
            }

            Log.d(TAG, "URL complète: '$fullUrl'")

            val audioUri = Uri.parse(fullUrl)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@ChatActivity, audioUri)
                prepareAsync()
                setOnPreparedListener {
                    Log.d(TAG, "Audio prêt, lecture...")
                    start()
                }
                setOnCompletionListener {
                    Log.d(TAG, "Audio terminé")
                    release()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Erreur audio: what=$what, extra=$extra")
                    Toast.makeText(this@ChatActivity, "Lecture audio impossible", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception playAudio: ${e.message}")
            Toast.makeText(this, "Lecture non disponible: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage(content: String, type: String, mediaUrl: String?) {
        val tokenManager = TokenManager(this)
        val userId = tokenManager.getUserId()

        val tempId = System.currentTimeMillis()
        val tempMessage = MessageResponse(
            id = tempId,
            rideId = rideId,
            senderId = userId,
            senderType = "CLIENT",
            receiverId = driverId,
            content = content,
            messageType = type,
            mediaUrl = mediaUrl,
            reaction = null,
            isRead = false,
            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        )

        messages.add(tempMessage)
        messageAdapter.notifyItemInserted(messages.size - 1)
        animateNewMessage(messages.size - 1)
        if (shouldAutoScroll) {
            rvMessages.scrollToPosition(messages.size - 1)
        }
        etMessage.text.clear()
        resetTypingIndicator()

        val request = SendMessageRequest(
            rideId = rideId,
            senderId = userId,
            senderType = "CLIENT",
            receiverId = driverId,
            content = content,
            messageType = type,
            mediaUrl = mediaUrl ?: ""
        )

        progressBar.visibility = View.VISIBLE

        RetrofitClient.apiService.sendMessage(request).enqueue(object : Callback<Map<String, Any>> {
            override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val realMessage = response.body()!!
                    val index = messages.indexOfFirst { it.id == tempId }
                    if (index != -1) {
                        messages[index] = MessageResponse(
                            id = (realMessage["id"] as Number).toLong(),
                            rideId = (realMessage["rideId"] as Number).toLong(),
                            senderId = (realMessage["senderId"] as Number).toLong(),
                            senderType = realMessage["senderType"] as String,
                            receiverId = (realMessage["receiverId"] as Number).toLong(),
                            content = realMessage["content"] as String,
                            messageType = realMessage["messageType"] as? String ?: "TEXT",
                            mediaUrl = realMessage["mediaUrl"] as? String,
                            reaction = realMessage["reaction"] as? String,
                            isRead = false,
                            createdAt = realMessage["createdAt"] as String
                        )
                        messageAdapter.notifyItemChanged(index)
                    }
                    Toast.makeText(this@ChatActivity, if (type == "VOICE") "Audio envoyé!" else "Message envoyé!", Toast.LENGTH_SHORT).show()
                } else {
                    messages.removeIf { it.id == tempId }
                    messageAdapter.notifyDataSetChanged()
                    Toast.makeText(this@ChatActivity, "Erreur d'envoi", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                progressBar.visibility = View.GONE
                messages.removeIf { it.id == tempId }
                messageAdapter.notifyDataSetChanged()
                Toast.makeText(this@ChatActivity, "Erreur: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadMessages() {
        RetrofitClient.apiService.getMessages(rideId).enqueue(object : Callback<List<Map<String, Any>>> {
            override fun onResponse(call: Call<List<Map<String, Any>>>, response: Response<List<Map<String, Any>>>) {
                if (response.isSuccessful && response.body() != null) {
                    val newMessages = response.body()!!.map { msg ->
                        MessageResponse(
                            id = (msg["id"] as Number).toLong(),
                            rideId = (msg["rideId"] as Number).toLong(),
                            senderId = (msg["senderId"] as Number).toLong(),
                            senderType = msg["senderType"] as String,
                            receiverId = (msg["receiverId"] as Number).toLong(),
                            content = msg["content"] as String,
                            messageType = msg["messageType"] as? String ?: "TEXT",
                            mediaUrl = msg["mediaUrl"] as? String,
                            reaction = msg["reaction"] as? String,
                            isRead = msg["isRead"] as? Boolean ?: false,
                            createdAt = msg["createdAt"] as String
                        )
                    }

                    val hadNewMessages = newMessages.size > messages.size

                    messages.clear()
                    messages.addAll(newMessages)
                    messageAdapter.notifyDataSetChanged()

                    if (shouldAutoScroll && hadNewMessages && messages.isNotEmpty()) {
                        rvMessages.scrollToPosition(messages.size - 1)
                    }

                    val hasUnread = messages.any { !it.isRead && it.senderType != "CLIENT" }
                    if (hasUnread) {
                        markAllMessagesAsRead()
                    }
                }
            }
            override fun onFailure(call: Call<List<Map<String, Any>>>, t: Throwable) {
                Log.e(TAG, "loadMessages error: ${t.message}")
            }
        })
    }

    private fun markAllMessagesAsRead() {
        RetrofitClient.apiService.markAllMessagesAsRead(rideId, clientId).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d(TAG, "Tous les messages marqués comme lus")
                    loadMessages()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e(TAG, "Erreur markAllMessagesAsRead: ${t.message}")
            }
        })
    }

    private fun startPolling() {
        pollRunnable = object : Runnable {
            override fun run() {
                loadMessages()
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(pollRunnable!!)
    }

    private fun showReactionDialog(messageId: Long, currentReaction: String?) {
        try {
            val reactions = arrayOf("👍", "❤️", "😂", "😮", "😢", "🙏", "⭐", "❌ Supprimer")
            AlertDialog.Builder(this)
                .setTitle("Ajouter une réaction")
                .setItems(reactions) { _, which ->
                    val reaction = if (reactions[which] == "❌ Supprimer") "" else reactions[which]
                    sendReaction(messageId, reaction)
                }
                .setNegativeButton("Annuler", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "ERREUR dans showReactionDialog: ${e.message}", e)
        }
    }

    private fun sendReaction(messageId: Long, reaction: String) {
        try {
            RetrofitClient.apiService.addReaction(messageId, clientId, reaction).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        val index = messages.indexOfFirst { it.id == messageId }
                        if (index != -1) {
                            messages[index] = messages[index].copy(reaction = if (reaction.isEmpty()) null else reaction)
                            messageAdapter.notifyItemChanged(index)
                        }
                    }
                }
                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.e(TAG, "sendReaction onFailure: ${t.message}", t)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "ERREUR dans sendReaction: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollRunnable?.let { handler.removeCallbacks(it) }
        mediaRecorder?.release()
        mediaPlayer?.release()
        resetTypingIndicator()
    }

    private fun animateNewMessage(position: Int) {
        val holder = rvMessages.findViewHolderForAdapterPosition(position)
        holder?.itemView?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right))
    }

    // MessageAdapter inner class
    inner class MessageAdapter(
        private val messages: List<MessageResponse>,
        private val currentUserType: String,
        private val onVoicePlay: (MessageResponse) -> Unit,
        private val onAddReaction: (MessageResponse) -> Unit
    ) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = layoutInflater.inflate(R.layout.item_message, parent, false)
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            holder.bind(messages[position])
        }

        override fun getItemCount(): Int = messages.size

        inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val layoutMine: LinearLayout = itemView.findViewById(R.id.layout_mine)
            private val layoutOther: LinearLayout = itemView.findViewById(R.id.layout_other)
            private val tvMessageMine: TextView = itemView.findViewById(R.id.tvMessageMine)
            private val tvMessageOther: TextView = itemView.findViewById(R.id.tvMessageOther)
            private val tvTimeMine: TextView = itemView.findViewById(R.id.tvTimeMine)
            private val tvTimeOther: TextView = itemView.findViewById(R.id.tvTimeOther)
            private val btnPlayVoiceMine: ImageButton = itemView.findViewById(R.id.btnPlayVoiceMine)
            private val btnPlayVoiceOther: ImageButton = itemView.findViewById(R.id.btnPlayVoiceOther)
            private val tvStatusMine: TextView = itemView.findViewById(R.id.tvStatusMine)
            private val tvReactionOther: TextView = itemView.findViewById(R.id.tvReactionOther)
            private val btnAddReactionOther: ImageButton = itemView.findViewById(R.id.btnAddReactionOther)
            private val ivImageMine: ImageView = itemView.findViewById(R.id.ivImageMine)
            private val ivImageOther: ImageView = itemView.findViewById(R.id.ivImageOther)

            fun bind(message: MessageResponse) {
                val isMine = message.senderType == currentUserType
                val timeStr = try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val date = sdf.parse(message.createdAt)
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                } catch (e: Exception) { "??:??" }

                if (isMine) {
                    layoutMine.visibility = View.VISIBLE
                    layoutOther.visibility = View.GONE

                    when (message.messageType) {
                        "TEXT" -> {
                            tvMessageMine.visibility = View.VISIBLE
                            btnPlayVoiceMine.visibility = View.GONE
                            ivImageMine.visibility = View.GONE
                            tvMessageMine.text = message.content
                        }
                        "VOICE" -> {
                            tvMessageMine.visibility = View.GONE
                            btnPlayVoiceMine.visibility = View.VISIBLE
                            ivImageMine.visibility = View.GONE
                            btnPlayVoiceMine.setOnClickListener { onVoicePlay(message) }
                        }
                        "IMAGE" -> {
                            tvMessageMine.visibility = View.GONE
                            btnPlayVoiceMine.visibility = View.GONE
                            ivImageMine.visibility = View.VISIBLE
                            // 🔧 Construire l'URL complète pour l'image
                            val fullUrl = when {
                                message.mediaUrl?.startsWith("http") == true -> message.mediaUrl
                                message.mediaUrl?.startsWith("/") == true -> "$BASE_URL${message.mediaUrl}"
                                else -> "$BASE_URL/${message.mediaUrl}"
                            }
                            Log.d(TAG, "Chargement image: $fullUrl")
                            Glide.with(this@ChatActivity)
                                .load(fullUrl)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_menu_report_image)
                                .into(ivImageMine)
                        }
                        else -> {
                            tvMessageMine.visibility = View.VISIBLE
                            btnPlayVoiceMine.visibility = View.GONE
                            ivImageMine.visibility = View.GONE
                            tvMessageMine.text = message.content
                        }
                    }
                    tvTimeMine.text = timeStr
                    val statusText = if (message.isRead) "✓✓" else "✓"
                    tvStatusMine.text = statusText
                    tvStatusMine.visibility = View.VISIBLE
                } else {
                    layoutMine.visibility = View.GONE
                    layoutOther.visibility = View.VISIBLE

                    when (message.messageType) {
                        "TEXT" -> {
                            tvMessageOther.visibility = View.VISIBLE
                            btnPlayVoiceOther.visibility = View.GONE
                            ivImageOther.visibility = View.GONE
                            tvMessageOther.text = message.content
                        }
                        "VOICE" -> {
                            tvMessageOther.visibility = View.GONE
                            btnPlayVoiceOther.visibility = View.VISIBLE
                            ivImageOther.visibility = View.GONE
                            btnPlayVoiceOther.setOnClickListener { onVoicePlay(message) }
                        }
                        "IMAGE" -> {
                            tvMessageOther.visibility = View.GONE
                            btnPlayVoiceOther.visibility = View.GONE
                            ivImageOther.visibility = View.VISIBLE
                            // 🔧 Construire l'URL complète pour l'image
                            val fullUrl = when {
                                message.mediaUrl?.startsWith("http") == true -> message.mediaUrl
                                message.mediaUrl?.startsWith("/") == true -> "$BASE_URL${message.mediaUrl}"
                                else -> "$BASE_URL/${message.mediaUrl}"
                            }
                            Log.d(TAG, "Chargement image: $fullUrl")
                            Glide.with(this@ChatActivity)
                                .load(fullUrl)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_menu_report_image)
                                .into(ivImageOther)
                        }
                        else -> {
                            tvMessageOther.visibility = View.VISIBLE
                            btnPlayVoiceOther.visibility = View.GONE
                            ivImageOther.visibility = View.GONE
                            tvMessageOther.text = message.content
                        }
                    }
                    tvTimeOther.text = timeStr

                    if (!message.reaction.isNullOrEmpty()) {
                        tvReactionOther.visibility = View.VISIBLE
                        tvReactionOther.text = message.reaction
                    } else {
                        tvReactionOther.visibility = View.GONE
                    }

                    btnAddReactionOther.setOnClickListener {
                        onAddReaction(message)
                    }
                }

                itemView.setOnLongClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Message", if (isMine) tvMessageMine.text else tvMessageOther.text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(itemView.context, "Message copié", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
    }
}