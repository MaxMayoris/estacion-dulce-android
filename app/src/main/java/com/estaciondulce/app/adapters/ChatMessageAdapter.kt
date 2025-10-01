package com.estaciondulce.app.adapters

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.models.ChatMessage
import com.estaciondulce.app.models.UserMessage
import com.estaciondulce.app.models.TypingMessage

/**
 * Adapter for displaying chat messages from Cha and user messages
 */
class ChatMessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    private val messages = mutableListOf<Any>()
    
    companion object {
        private const val TYPE_CHA_MESSAGE = 0
        private const val TYPE_USER_MESSAGE = 1
        private const val TYPE_TYPING_MESSAGE = 2
    }
    
    /**
     * Adds a new message from Cha to the chat
     */
    fun addChaMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    /**
     * Adds a new user message to the chat
     */
    fun addUserMessage(message: UserMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    /**
     * Shows or hides the typing indicator
     */
    fun showTypingIndicator(show: Boolean) {
        val typingIndex = messages.indexOfFirst { it is TypingMessage }
        
        if (show && typingIndex == -1) {
            // Add typing indicator
            messages.add(TypingMessage())
            notifyItemInserted(messages.size - 1)
        } else if (!show && typingIndex != -1) {
            // Remove typing indicator
            messages.removeAt(typingIndex)
            notifyItemRemoved(typingIndex)
        }
    }
    
    /**
     * Clears all messages from the chat
     */
    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (messages[position]) {
            is ChatMessage -> TYPE_CHA_MESSAGE
            is UserMessage -> TYPE_USER_MESSAGE
            is TypingMessage -> TYPE_TYPING_MESSAGE
            else -> TYPE_CHA_MESSAGE
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_CHA_MESSAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_message, parent, false)
                ChatMessageViewHolder(view)
            }
            TYPE_USER_MESSAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_user_message, parent, false)
                UserMessageViewHolder(view)
            }
            TYPE_TYPING_MESSAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_typing_message, parent, false)
                TypingMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChatMessageViewHolder -> holder.bind(messages[position] as ChatMessage)
            is UserMessageViewHolder -> holder.bind(messages[position] as UserMessage)
            is TypingMessageViewHolder -> holder.bind(messages[position] as TypingMessage)
        }
    }
    
    override fun getItemCount(): Int = messages.size
    
    class ChatMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarImageView: ImageView = itemView.findViewById(R.id.chaAvatarImageView)
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        
        fun bind(message: ChatMessage) {
            avatarImageView.setImageResource(message.avatarResId)
            messageTextView.text = message.text
            
            // Make avatar clickable to show larger image
            avatarImageView.setOnClickListener {
                showAvatarDialog(itemView.context)
            }
        }
        
        private fun showAvatarDialog(context: android.content.Context) {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_avatar_preview, null)
            val dialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .create()
            
            // Make the dialog dismissible by clicking outside or on the image
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            
            // Get the image view and apply circular clip
            val imageView = dialogView.findViewById<ImageView>(R.id.avatarPreviewImageView)
            imageView.clipToOutline = true
            imageView.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    val size = Math.min(view.width, view.height)
                    val left = (view.width - size) / 2
                    val top = (view.height - size) / 2
                    outline.setOval(left, top, left + size, top + size)
                }
            }
            
            // Set a maximum size for the avatar (half screen)
            imageView.post {
                val screenWidth = context.resources.displayMetrics.widthPixels
                val maxSize = (screenWidth * 0.5).toInt()
                imageView.layoutParams.width = maxSize
                imageView.layoutParams.height = maxSize
                imageView.requestLayout()
            }
            
            // Make the image clickable to close the dialog
            imageView.setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
        }
    }
    
    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userMessageTextView: TextView = itemView.findViewById(R.id.userMessageTextView)

        fun bind(message: UserMessage) {
            userMessageTextView.text = message.text
        }
    }

    class TypingMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typingDots: TextView = itemView.findViewById(R.id.typingDots)
        private var dotAnimation: android.animation.ValueAnimator? = null

        fun bind(message: TypingMessage) {
            if (message.isTyping) {
                startTypingAnimation()
            } else {
                stopTypingAnimation()
            }
        }

        private fun startTypingAnimation() {
            stopTypingAnimation()
            
            dotAnimation = android.animation.ValueAnimator.ofInt(1, 4).apply {
                duration = 1000
                repeatCount = android.animation.ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    val dots = animator.animatedValue as Int
                    typingDots.text = ".".repeat(dots)
                }
                start()
            }
        }

        private fun stopTypingAnimation() {
            dotAnimation?.cancel()
            dotAnimation = null
            typingDots.text = "..."
        }
    }
}

