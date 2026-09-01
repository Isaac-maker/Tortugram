package com.example.tortugram

import android.content.Context
import android.os.Build
import android.util.Log
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.withContext

/**
 * Manejo de Telegram

 * Modelo ligero para representar las carpetas en la UI.
 *
 * * isaac-maker 2026
 *
 */
data class ChatFolderItem(
    val id: Int,
    val title: String
)

object TelegramManager {

    private const val TAG = "TelegramManager"
    private const val API_ID =   00000000  /**  ---> Aqui va API ID      esto se obtiene en https://my.telegram.org/auth   */
    private const val API_HASH = "Aqui va API HASH" /**  ---> Aqui va API hash     esto se obtiene en https://my.telegram.org/auth   */

    private var client: TdlClient? = null
    private var dbPath: String = ""
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _qrCodeLink = MutableStateFlow<String?>(null)
    val qrCodeLink: StateFlow<String?> = _qrCodeLink.asStateFlow()

    private val _isPasswordRequired = MutableStateFlow(false)
    val isPasswordRequired: StateFlow<Boolean> = _isPasswordRequired.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _folders = MutableStateFlow<List<ChatFolderItem>>(listOf(ChatFolderItem(0, "Todos")))
    val folders: StateFlow<List<ChatFolderItem>> = _folders.asStateFlow()

    private val _selectedFolderId = MutableStateFlow(0) // 0 = Todos los chats (ChatListMain)
    val selectedFolderId: StateFlow<Int> = _selectedFolderId.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    fun initClient(context: Context) {
        if (client != null) return

        scope.launch(Dispatchers.IO) {
            dbPath = context.filesDir.absolutePath + "/tdlib"
            File(dbPath).mkdirs()

            Log.d(TAG, "Iniciando cliente TDLib...")
            val newClient = TdlClient.create()
            client = newClient

            sendParameters()

            newClient.allUpdates.collect { update ->
                when (update) {
                    is UpdateAuthorizationState -> {
                        handleAuthState(update.authorizationState)
                    }
                    is UpdateNewChat -> {
                        val currentList = _chats.value.toMutableList()
                        val index = currentList.indexOfFirst { it.id == update.chat.id }
                        if (index != -1) {
                            currentList[index] = update.chat
                        } else {
                            currentList.add(update.chat)
                        }
                        _chats.value = currentList
                    }
                    is UpdateChatTitle -> {
                        scope.launch(Dispatchers.IO) {
                            val res = client?.getChat(update.chatId)
                            if (res is TdlResult.Success<*>) {
                                val updatedChat = res.result as? Chat
                                if (updatedChat != null) {
                                    val currentList = _chats.value.toMutableList()
                                    val index = currentList.indexOfFirst { it.id == update.chatId }
                                    if (index != -1) {
                                        currentList[index] = updatedChat
                                        _chats.value = currentList
                                    }
                                }
                            }
                        }
                    }
                    is UpdateChatFolders -> {

                        val folderList = mutableListOf(ChatFolderItem(0, "Todos"))
                        update.chatFolders.forEach { folderInfo ->
                            val folderTitle = folderInfo.name.text.text
                            folderList.add(ChatFolderItem(folderInfo.id, folderTitle))
                        }
                        _folders.value = folderList
                    }
                    else -> {}
                }
            }
        }
    }

    private fun sendParameters() {
        scope.launch(Dispatchers.IO) {
            val res = client?.setTdlibParameters(
                useTestDc = false,
                databaseDirectory = dbPath,
                filesDirectory = "$dbPath/files",
                databaseEncryptionKey = ByteArray(0),
                useFileDatabase = true,
                useChatInfoDatabase = true,
                useMessageDatabase = true,
                useSecretChats = false,
                apiId = API_ID,
                apiHash = API_HASH,
                systemLanguageCode = "es",
                deviceModel = Build.MODEL,
                systemVersion = Build.VERSION.RELEASE,
                applicationVersion = "1.0"
            )
            Log.d(TAG, "Resultado setTdlibParameters: $res")
        }
    }

    private fun handleAuthState(authState: AuthorizationState) {
        Log.d(TAG, "Estado de Autorización: ${authState::class.java.simpleName}")

        when (authState) {
            is AuthorizationStateWaitTdlibParameters -> {
                sendParameters()
            }

            is AuthorizationStateWaitPhoneNumber -> {
                scope.launch(Dispatchers.IO) {
                    Log.d(TAG, "Pidiendo QR...")
                    val res = client?.requestQrCodeAuthentication(otherUserIds = LongArray(0))
                    Log.d(TAG, "Resultado petición QR: $res")
                }
            }

            is AuthorizationStateWaitOtherDeviceConfirmation -> {
                Log.d(TAG, "QR Enlace listo: ${authState.link}")
                _isPasswordRequired.value = false
                _qrCodeLink.value = authState.link
            }

            is AuthorizationStateWaitPassword -> {
                Log.d(TAG, "Se requiere contraseña de 2FA")
                _qrCodeLink.value = null
                _isPasswordRequired.value = true
            }

            is AuthorizationStateReady -> {
                Log.d(TAG, "Sesión lista!")
                _qrCodeLink.value = null
                _isPasswordRequired.value = false
                _isLoggedIn.value = true
                loadChats()
            }

            is AuthorizationStateLoggingOut, is AuthorizationStateClosed -> {
                _isLoggedIn.value = false
                _isPasswordRequired.value = false
                _qrCodeLink.value = null
                client = null
            }

            else -> {}
        }
    }

    fun checkPassword(password: String, onResult: (Boolean) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val res = client?.checkAuthenticationPassword(password = password)
            if (res is TdlResult.Success<*>) {
                _isPasswordRequired.value = false
                _isLoggedIn.value = true
                loadChats()
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun downloadFile(fileId: Int, onComplete: (String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val result = client?.downloadFile(
                fileId = fileId,
                priority = 3,
                offset = 0,
                limit = 0,
                synchronous = true
            )
            if (result is TdlResult.Success<*>) {
                val file = result.result as? dev.g000sha256.tdl.dto.File
                val path = file?.local?.path ?: ""
                if (path.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onComplete(path)
                    }
                }
            }
        }
    }

    /**
     * Selecciona una carpeta y recarga los chats pertenecientes a ella.
     */
    fun selectFolder(folderId: Int) {
        _selectedFolderId.value = folderId
        _chats.value = emptyList()
        loadChats(folderId)
    }

    /**
     * Carga los chats según la carpeta activa.
     */
    fun loadChats(folderId: Int = _selectedFolderId.value) {
        scope.launch(Dispatchers.IO) {
            val targetChatList: ChatList = if (folderId == 0) {
                ChatListMain()
            } else {
                ChatListFolder(chatFolderId = folderId)
            }

            var lastLoadedCount = -1
            while (true) {
                val loadRes = client?.loadChats(chatList = targetChatList, limit = 100)
                if (loadRes !is TdlResult.Success<*>) {
                    break
                }

                val chatsResult = client?.getChats(chatList = targetChatList, limit = 500)
                if (chatsResult is TdlResult.Success<*>) {
                    val chatsObj = chatsResult.result as? Chats
                    val ids = chatsObj?.chatIds ?: LongArray(0)
                    if (ids.size == lastLoadedCount) {
                        break
                    }
                    lastLoadedCount = ids.size

                    val deferredChats = ids.map { id ->
                        async {
                            val c = client?.getChat(chatId = id)
                            if (c is TdlResult.Success<*>) c.result as? Chat else null
                        }
                    }

                    val fullChats = deferredChats.awaitAll().filterNotNull()
                    _chats.value = fullChats
                } else {
                    break
                }
            }
        }
    }

    fun loadMessages(chatId: Long) {
        scope.launch(Dispatchers.IO) {
            _messages.value = emptyList()
            val result = client?.getChatHistory(
                chatId = chatId,
                fromMessageId = 0,
                offset = 0,
                limit = 30,
                onlyLocal = false
            )

            if (result is TdlResult.Success<*>) {
                val msgsObj = result.result as? Messages
                _messages.value = msgsObj?.messages?.filterNotNull() ?: emptyList()
            }
        }
    }

    fun streamVideo(fileId: Int) {
        scope.launch(Dispatchers.IO) {
            client?.downloadFile(
                fileId = fileId,
                priority = 1,
                offset = 0,
                limit = 0,
                synchronous = false
            )
        }
    }

    suspend fun downloadRange(fileId: Int, offset: Long, length: Long): dev.g000sha256.tdl.dto.File? {
        val result = client?.downloadFile(
            fileId = fileId,
            priority = 32,
            offset = offset,
            limit = length,
            synchronous = true
        )
        return if (result is TdlResult.Success<*>) result.result as? dev.g000sha256.tdl.dto.File else null
    }
}