package com.abdil.taxi

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date

data class ChatMessage(
    val id: String = "",
    val rideId: Long,
    val senderId: Long,
    val senderType: String,
    val receiverId: Long,
    val content: String,
    val timestamp: Date = Date()
)

object ChatFirestoreService {
    private const val TAG = "ChatFirestore"
    val db = FirebaseFirestore.getInstance()

    fun getMessagesForRide(rideId: Long): Flow<List<ChatMessage>> = callbackFlow {
        val listener = db.collection("chats")
            .document(rideId.toString())
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erreur: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(messages)
            }

        awaitClose { listener.remove() }
    }

    fun sendMessage(rideId: Long, message: ChatMessage) {
        val data = mapOf(
            "rideId" to message.rideId,
            "senderId" to message.senderId,
            "senderType" to message.senderType,
            "receiverId" to message.receiverId,
            "content" to message.content,
            "timestamp" to message.timestamp
        )

        db.collection("chats")
            .document(rideId.toString())
            .collection("messages")
            .document()
            .set(data)
            .addOnSuccessListener { Log.d(TAG, "Message envoyé") }
            .addOnFailureListener { Log.e(TAG, "Erreur: ${it.message}") }
    }
}