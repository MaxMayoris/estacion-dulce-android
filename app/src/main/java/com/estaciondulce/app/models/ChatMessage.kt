package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a chat message from Cha (virtual brother)
 * @param text The response text from Cha
 * @param avatarResId Resource ID for Cha's avatar image
 */
@Parcelize
data class ChatMessage(
    val text: String,
    val avatarResId: Int = com.estaciondulce.app.R.drawable.cha_avatar
) : Parcelable

/**
 * Represents a user query in the chat
 * @param text The user's query text
 */
@Parcelize
data class UserMessage(
    val text: String
) : Parcelable

/**
 * Represents a typing indicator from Cha
 * @param isTyping Whether Cha is currently typing
 */
@Parcelize
data class TypingMessage(
    val isTyping: Boolean = true
) : Parcelable
