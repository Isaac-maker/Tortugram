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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**

 * Manejo de Telegram.
 *
 * isaac-maker 2026
 */
data class ChatFolderItem(
    val id: Int,
    val title: String
)

object TelegramManager {


    private const val TAG = "TelegramManager"

    private const val API_ID =  36017199
    private const val API_HASH = "6ce83dcc204c5805c876cc6ddf6bc6b2"

    /*
     * Tamaño de página del historial.
     *
     * 50 es suficientemente grande para encontrar bastantes vídeos
     * sin intentar cargar miles de mensajes de golpe.
     */
    private const val MESSAGE_PAGE_SIZE = 50

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

    private val _folders =
        MutableStateFlow<List<ChatFolderItem>>(
            listOf(ChatFolderItem(0, "Todos"))
        )

    val folders: StateFlow<List<ChatFolderItem>> = _folders.asStateFlow()

    private val _selectedFolderId = MutableStateFlow(0)
    val selectedFolderId: StateFlow<Int> = _selectedFolderId.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    /*
     * Estado interno de paginación.
     *
     * currentChatId:
     * identifica el chat que estamos cargando.
     *
     * oldestMessageId:
     * ID del mensaje más antiguo que ya tenemos.
     *
     * hasMoreMessages:
     * indica si todavía debemos pedir páginas anteriores.
     *
     * loadingMessages:
     * evita dos llamadas simultáneas al historial.
     */
    private var currentChatId: Long? = null
    private var oldestMessageId: Long = 0L
    private var hasMoreMessages: Boolean = true
    private var loadingMessages: Boolean = false

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

                        val index =
                            currentList.indexOfFirst {
                                it.id == update.chat.id
                            }

                        if (index != -1) {
                            currentList[index] = update.chat
                        } else {
                            currentList.add(update.chat)
                        }

                        _chats.value = currentList
                    }

                    is UpdateChatTitle -> {

                        scope.launch(Dispatchers.IO) {

                            val res =
                                client?.getChat(update.chatId)

                            if (res is TdlResult.Success<*>) {

                                val updatedChat =
                                    res.result as? Chat

                                if (updatedChat != null) {

                                    val currentList =
                                        _chats.value.toMutableList()

                                    val index =
                                        currentList.indexOfFirst {
                                            it.id == update.chatId
                                        }

                                    if (index != -1) {

                                        currentList[index] =
                                            updatedChat

                                        _chats.value =
                                            currentList
                                    }
                                }
                            }
                        }
                    }

                    is UpdateChatFolders -> {

                        val folderList =
                            mutableListOf(
                                ChatFolderItem(0, "Todos")
                            )

                        update.chatFolders.forEach { folderInfo ->

                            val folderTitle =
                                folderInfo.name.text.text

                            folderList.add(
                                ChatFolderItem(
                                    folderInfo.id,
                                    folderTitle
                                )
                            )
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

            val res =
                client?.setTdlibParameters(

                    useTestDc = false,

                    databaseDirectory = dbPath,

                    filesDirectory = "$dbPath/files",

                    databaseEncryptionKey =
                        ByteArray(0),

                    useFileDatabase = true,

                    useChatInfoDatabase = true,

                    useMessageDatabase = true,

                    useSecretChats = false,

                    apiId = API_ID,

                    apiHash = API_HASH,

                    systemLanguageCode = "es",

                    deviceModel = Build.MODEL,

                    systemVersion =
                        Build.VERSION.RELEASE,

                    applicationVersion = "1.0"
                )

            Log.d(
                TAG,
                "Resultado setTdlibParameters: $res"
            )
        }
    }

    private fun handleAuthState(
        authState: AuthorizationState
    ) {

        Log.d(
            TAG,
            "Estado de Autorización: ${
                authState::class.java.simpleName
            }"
        )

        when (authState) {

            is AuthorizationStateWaitTdlibParameters -> {
                sendParameters()
            }

            is AuthorizationStateWaitPhoneNumber -> {

                scope.launch(Dispatchers.IO) {

                    Log.d(
                        TAG,
                        "Pidiendo QR..."
                    )

                    val res =
                        client?.requestQrCodeAuthentication(
                            otherUserIds = LongArray(0)
                        )

                    Log.d(
                        TAG,
                        "Resultado petición QR: $res"
                    )
                }
            }

            is AuthorizationStateWaitOtherDeviceConfirmation -> {

                Log.d(
                    TAG,
                    "QR Enlace listo: ${authState.link}"
                )

                _isPasswordRequired.value = false

                _qrCodeLink.value =
                    authState.link
            }

            is AuthorizationStateWaitPassword -> {

                Log.d(
                    TAG,
                    "Se requiere contraseña de 2FA"
                )

                _qrCodeLink.value = null

                _isPasswordRequired.value = true
            }

            is AuthorizationStateReady -> {

                Log.d(
                    TAG,
                    "Sesión lista!"
                )

                _qrCodeLink.value = null

                _isPasswordRequired.value = false

                _isLoggedIn.value = true

                loadChats()
            }

            is AuthorizationStateLoggingOut,
            is AuthorizationStateClosed -> {

                _isLoggedIn.value = false

                _isPasswordRequired.value = false

                _qrCodeLink.value = null

                client = null
            }

            else -> {}
        }
    }

    fun checkPassword(
        password: String,
        onResult: (Boolean) -> Unit
    ) {

        scope.launch(Dispatchers.IO) {

            val res =
                client?.checkAuthenticationPassword(
                    password = password
                )

            if (res is TdlResult.Success<*>) {

                _isPasswordRequired.value = false

                _isLoggedIn.value = true

                loadChats()

                withContext(Dispatchers.Main) {
                    onResult(true)
                }

            } else {

                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    fun downloadFile(
        fileId: Int,
        onComplete: (String) -> Unit
    ) {

        scope.launch(Dispatchers.IO) {

            val result =
                client?.downloadFile(

                    fileId = fileId,

                    priority = 3,

                    offset = 0,

                    limit = 0,

                    synchronous = true
                )

            if (result is TdlResult.Success<*>) {

                val file =
                    result.result
                            as? dev.g000sha256.tdl.dto.File

                val path =
                    file?.local?.path ?: ""

                if (path.isNotEmpty()) {

                    withContext(Dispatchers.Main) {
                        onComplete(path)
                    }
                }
            }

            // Cada archivo descargado puede acercarnos al límite de
            // almacenamiento. Se lo avisamos a StorageManager para que
            // decida si limpia o solo notifica. Esto NUNCA toca la
            // base de datos ni la sesión, solo archivos descargados.
            StorageManager.onFileDownloaded()
        }
    }

    // Job de la carga de chats en curso, para poder cancelarla si el
    // usuario cambia de carpeta antes de que termine.
    private var loadChatsJob: Job? = null

    fun selectFolder(folderId: Int) {

        _selectedFolderId.value = folderId

        _chats.value = emptyList()

        loadChats(folderId)
    }

    fun loadChats(
        folderId: Int = _selectedFolderId.value
    ) {

        loadChatsJob?.cancel()

        loadChatsJob = scope.launch(Dispatchers.IO) {

            val targetChatList: ChatList =
                if (folderId == 0) {
                    ChatListMain()
                } else {
                    ChatListFolder(
                        chatFolderId = folderId
                    )
                }

            var lastLoadedCount = -1

            while (true) {

                // IMPORTANTE: primero leemos lo que TDLib ya tiene en
                // caché para esta lista con getChats(). loadChats() se
                // usa solo para pedirle a TDLib que siga trayendo más
                // chats; que loadChats() falle (p. ej. TDLib respondiendo
                // que ya no quedan más por cargar) es un comportamiento
                // NORMAL en TDLib, no un error real, y no debe impedir
                // que mostremos lo que ya se obtuvo con getChats().
                val chatsResult =
                    client?.getChats(
                        chatList = targetChatList,
                        limit = 500
                    )

                if (chatsResult is TdlResult.Success<*>) {

                    val chatsObj =
                        chatsResult.result as? Chats

                    val ids =
                        chatsObj?.chatIds
                            ?: LongArray(0)

                    val deferredChats =
                        ids.map { id ->

                            async {

                                val c =
                                    client?.getChat(
                                        chatId = id
                                    )

                                if (
                                    c is TdlResult.Success<*>
                                ) {
                                    c.result as? Chat
                                } else {
                                    null
                                }
                            }
                        }

                    val fullChats =
                        deferredChats
                            .awaitAll()
                            .filterNotNull()

                    // Si el usuario ya cambió de carpeta mientras
                    // esperábamos esta respuesta, descartamos el
                    // resultado para no pisar la carpeta nueva.
                    if (_selectedFolderId.value != folderId) {
                        return@launch
                    }

                    _chats.value = fullChats

                    if (ids.size == lastLoadedCount) {
                        // No llegaron chats nuevos respecto a la
                        // vuelta anterior: ya tenemos todo.
                        break
                    }

                    lastLoadedCount = ids.size
                }

                val loadRes =
                    client?.loadChats(
                        chatList = targetChatList,
                        limit = 100
                    )

                if (loadRes !is TdlResult.Success<*>) {
                    // Fin normal: TDLib indica que no hay más chats
                    // que cargar en esta lista.
                    break
                }
            }
        }
    }

    /**
     * Inicia/reinicia la carga del historial de un chat.
     *
     * La primera página se carga inmediatamente.
     * Las siguientes páginas se solicitan mediante loadMoreMessages().
     */
    fun loadMessages(chatId: Long) {

        if (loadingMessages) {
            return
        }

        currentChatId = chatId

        oldestMessageId = 0L

        hasMoreMessages = true

        _messages.value = emptyList()

        loadMoreMessages(chatId)
    }


    fun loadMoreMessages(chatId: Long? = null) {


        val targetChatId =
            chatId ?: currentChatId ?: return

        if (loadingMessages) {
            return
        }

        if (!hasMoreMessages) {
            return
        }

        if (currentChatId != targetChatId) {
            return
        }

        loadingMessages = true

        scope.launch(Dispatchers.IO) {

            try {

                val result =
                    client?.getChatHistory(
                        chatId = targetChatId,
                        fromMessageId = oldestMessageId,
                        offset = 0,
                        limit = MESSAGE_PAGE_SIZE,
                        onlyLocal = false
                    )

                if (result is TdlResult.Success<*>) {

                    val messagesResult =
                        result.result as? Messages

                    val page =
                        messagesResult
                            ?.messages
                            ?.filterNotNull()
                            ?: emptyList()

                    if (page.isEmpty()) {

                        hasMoreMessages = false

                    } else {

                        val existingIds =
                            _messages.value
                                .asSequence()
                                .map { it.id }
                                .toHashSet()

                        val newMessages =
                            page.filter {
                                it.id !in existingIds
                            }

                        if (newMessages.isNotEmpty()) {

                            _messages.value =
                                _messages.value + newMessages
                        }

                        val newOldestId =
                            page.minOfOrNull {
                                it.id
                            } ?: 0L

                        if (
                            newOldestId <= 0L ||
                            newOldestId == oldestMessageId
                        ) {

                            hasMoreMessages = false

                        } else {

                            oldestMessageId =
                                newOldestId
                        }
                    }
                }

            } catch (exception: Exception) {

                Log.e(
                    TAG,
                    "Error cargando historial del chat $targetChatId",
                    exception
                )

            } finally {

                loadingMessages = false
            }
        }


    }

    fun hasMoreMessages(): Boolean {
        return hasMoreMessages
    }

    fun isLoadingMessages(): Boolean {
        return loadingMessages
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


    fun prefetchRange(fileId: Int, offset: Long, length: Long) {

        scope.launch(Dispatchers.IO) {

            try {

                client?.downloadFile(

                    fileId = fileId,

                    priority = 16,

                    offset = offset,

                    limit = length,

                    synchronous = false
                )

            } catch (exception: Exception) {

                Log.e(
                    TAG,
                    "Error en prefetch de fileId=$fileId offset=$offset",
                    exception
                )
            }
        }
    }

    suspend fun downloadRange(
        fileId: Int,
        offset: Long,
        length: Long
    ): dev.g000sha256.tdl.dto.File? {

        val result =
            client?.downloadFile(

                fileId = fileId,

                priority = 32,

                offset = offset,

                limit = length,

                synchronous = true
            )

        // El streaming de video (downloadRange) es la fuente principal
        // de crecimiento del almacenamiento, así que también avisamos
        // aquí a StorageManager.
        StorageManager.onFileDownloaded()

        return if (
            result is TdlResult.Success<*>
        ) {

            result.result
                    as? dev.g000sha256.tdl.dto.File

        } else {
            null
        }
    }

    // ------------------------------------------------------------------
    // Almacenamiento (StorageScreen / StorageManager)
    //
    // Estas dos funciones son las ÚNICAS que tocan el tema de espacio en
    // disco, y usan las funciones propias de TDLib para eso (nunca
    // File.deleteRecursively() a mano). No modifican databaseDirectory,
    // databaseEncryptionKey ni el estado de AuthorizationState, así que
    // la sesión nunca se ve afectada.
    // ------------------------------------------------------------------

    /**
     * Estadísticas rápidas de espacio usado por TDLib.
     * filesSize = archivos descargados (fotos, videos, miniaturas...).
     * databaseSize = base de datos de TDLib (mensajes, sesión, etc).
     */
    suspend fun getStorageStatisticsFast(): StorageInfo {

        val result = client?.getStorageStatisticsFast()

        val stats =
            (result as? TdlResult.Success<*>)
                ?.result as? dev.g000sha256.tdl.dto.StorageStatisticsFast

        return StorageInfo(
            filesSize = stats?.filesSize ?: 0L,
            databaseSize = stats?.databaseSize ?: 0L,
            fileCount = stats?.fileCount ?: 0
        )
    }

    /**
     * Le pide a TDLib que recorte los archivos descargados hasta que el
     * total pese como máximo [maxTotalSizeBytes] (0 = borrar todo lo
     * descargable). TDLib decide qué borrar (los más viejos primero) y
     * jamás toca la base de datos ni la autenticación.
     *
     * NOTA: si el nombre/orden de los parámetros de optimizeStorage no
     * coincide exactamente con esta versión de tdl-coroutines, el
     * autocompletado de Android Studio te va a mostrar la firma real;
     * es la única línea de todo esto que depende de la versión exacta
     * de la librería.
     */
    suspend fun optimizeStorage(maxTotalSizeBytes: Long): Long {

        val result = client?.optimizeStorage(
            size = maxTotalSizeBytes,
            ttl = 0,
            count = 0,
            immunityDelay = 0,
            fileTypes = emptyArray(),
            chatIds = LongArray(0),
            excludeChatIds = LongArray(0),
            returnDeletedFileStatistics = false,
            chatLimit = 0
        )

        val stats =
            (result as? TdlResult.Success<*>)
                ?.result as? dev.g000sha256.tdl.dto.StorageStatistics

        return stats?.size ?: 0L
    }
}

data class StorageInfo(
    val filesSize: Long = 0L,
    val databaseSize: Long = 0L,
    val fileCount: Int = 0
)
