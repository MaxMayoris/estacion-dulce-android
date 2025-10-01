package com.estaciondulce.app.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.estaciondulce.app.R
import com.estaciondulce.app.adapters.ChatMessageAdapter
import com.estaciondulce.app.models.ChatMessage
import com.estaciondulce.app.models.UserMessage
import com.estaciondulce.app.network.OpenAIClient
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.viewmodels.ChatViewModel
import com.estaciondulce.app.config.ChaPersonality
import com.estaciondulce.app.helpers.ContextSelector
import android.widget.ImageButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment for chat with Cha (virtual brother)
 */
class ChatFragment : Fragment() {
    
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatInputEditText: TextInputEditText
    private lateinit var sendButton: ImageButton
    private lateinit var chatAdapter: ChatMessageAdapter
    private lateinit var chatViewModel: ChatViewModel

    private var nicknameCounter = 0
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        ChaPersonality.initialize(requireContext())
        
        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        setupViewModel()
        
        addWelcomeMessage()
    }
    
    private fun initializeViews(view: View) {
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView)
        chatInputEditText = view.findViewById(R.id.chatInputEditText)
        sendButton = view.findViewById(R.id.sendButton)
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatMessageAdapter()
        chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatAdapter
        }
    }
    
    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            sendMessage()
        }
        
        chatInputEditText.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }
    
    private fun setupViewModel() {
        chatViewModel = ViewModelProvider(this)[ChatViewModel::class.java]
    }
    
    private fun addWelcomeMessage() {
        if (chatAdapter.itemCount == 0) {
            val welcomeMessage = ChatMessage(
                text = "¡Hola Aguito! Soy Cha, tu hermano virtual. ¿En qué puedo ayudarte hoy con Estación Dulce?"
            )
            chatAdapter.addChaMessage(welcomeMessage)
        }
    }
    
    private fun sendMessage() {
        val userInput = chatInputEditText.text?.toString()?.trim()
        if (userInput.isNullOrEmpty()) return
        
        val userMessage = UserMessage(text = userInput)
        chatAdapter.addUserMessage(userMessage)
        
        chatInputEditText.text?.clear()
        showLoading(true)
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val prompt = buildPrompt(userInput)
                val aiResponse = withContext(Dispatchers.IO) {
                    OpenAIClient.getAIResponse(prompt)
                }
                
                if (aiResponse != null) {
                    val chatMessage = ChatMessage(text = aiResponse)
                    chatAdapter.addChaMessage(chatMessage)
                    scrollToBottom()
                } else {
                    val errorMessage = ChatMessage(
                        text = "Lo siento ${getNextNickname()}, no pude procesar tu consulta en este momento. Inténtalo de nuevo."
                    )
                    chatAdapter.addChaMessage(errorMessage)
                }
            } catch (e: Exception) {
                Log.e("ChatFragment", "Error sending message", e)
                Log.e("ChatFragment", "Exception type: ${e.javaClass.simpleName}")
                Log.e("ChatFragment", "Exception message: ${e.message}")
                Log.e("ChatFragment", "User input was: $userInput")
                val errorMessage = ChatMessage(
                    text = "Ups ${getNextNickname()}, algo salió mal. Inténtalo de nuevo."
                )
                chatAdapter.addChaMessage(errorMessage)
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun buildPrompt(userQuery: String): String {
        val dataContext = buildDataContext(userQuery)
        val personalityConfig = ChaPersonality.getCompletePersonalityWithMemory()

        return """
            $personalityConfig

            Consulta del usuario: $userQuery

            Información de la base de datos:
            $dataContext
        """.trimIndent()
    }
    
    private fun buildDataContext(userQuery: String): String {
        val products = FirestoreRepository.productsLiveData.value
        val recipes = FirestoreRepository.recipesLiveData.value
        val persons = FirestoreRepository.personsLiveData.value
        val movements = FirestoreRepository.movementsLiveData.value
        val measures = FirestoreRepository.measuresLiveData.value

        return ContextSelector.selectContext(
            userQuery = userQuery,
            products = products,
            recipes = recipes,
            persons = persons,
            movements = movements,
            measures = measures
        )
    }
    
    private fun getNextNickname(): String {
        val nicknames = listOf("Aguito", "Goni")
        val nickname = nicknames[nicknameCounter % nicknames.size]
        nicknameCounter++
        return nickname
    }
    
    private fun showLoading(show: Boolean) {
        if (show) {
            chatAdapter.showTypingIndicator(true)
        } else {
            chatAdapter.showTypingIndicator(false)
        }
        sendButton.isEnabled = !show
        chatInputEditText.isEnabled = !show
    }
    
    private fun scrollToBottom() {
        chatRecyclerView.post {
            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }
}
