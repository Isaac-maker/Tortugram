package com.example.tortugram

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Gestiona el almacenamiento local que usa TDLib (archivos descargados +
 * base de datos), apoyándose SIEMPRE en las funciones propias de TDLib
 * (getStorageStatisticsFast / optimizeStorage, en TelegramManager.kt).
 *
 * Este archivo NUNCA borra carpetas a mano ni toca databaseDirectory,
 * databaseEncryptionKey ni el AuthorizationState: la sesión (login/QR)
 * queda completamente intacta pase lo que pase aquí.
 *
 * isaac-maker 2026
 */
object StorageManager {

    private const val TAG = "StorageManager"

    private const val PREFS_NAME = "tortugram_storage"
    private const val KEY_LIMIT_BYTES = "limit_bytes"
    private const val KEY_AUTO_CLEAN = "auto_clean_enabled"

    // Límite inicial recomendado para un dispositivo con poco espacio
    // (por ejemplo el Fire TV Lite). El usuario lo puede cambiar en
    // StorageScreen.
    const val DEFAULT_LIMIT_BYTES: Long = 500L * 1024 * 1024 // 500 MB

    // Opciones que se muestran en StorageScreen como botones de límite.
    val LIMIT_OPTIONS_BYTES: List<Long> = listOf(
        250L * 1024 * 1024,
        500L * 1024 * 1024,
        1024L * 1024 * 1024,
        2048L * 1024 * 1024
    )

    private val scope = CoroutineScope(Dispatchers.IO)

    private var prefs: SharedPreferences? = null

    private val _filesSize = MutableStateFlow(0L)
    val filesSize: StateFlow<Long> = _filesSize.asStateFlow()

    private val _databaseSize = MutableStateFlow(0L)
    val databaseSize: StateFlow<Long> = _databaseSize.asStateFlow()

    private val _fileCount = MutableStateFlow(0)
    val fileCount: StateFlow<Int> = _fileCount.asStateFlow()

    private val _limitBytes = MutableStateFlow(DEFAULT_LIMIT_BYTES)
    val limitBytes: StateFlow<Long> = _limitBytes.asStateFlow()

    private val _autoCleanEnabled = MutableStateFlow(true)
    val autoCleanEnabled: StateFlow<Boolean> = _autoCleanEnabled.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // Aviso puntual para la UI (limpieza automática hecha, o "llegaste
    // al límite pero la limpieza automática está apagada"). La pantalla
    // lo lee y lo borra con clearNotice() después de mostrarlo.
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Llamar una vez, por ejemplo desde MainActivity.onCreate(). */
    fun init(context: Context) {

        if (prefs != null) return

        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        _limitBytes.value =
            prefs?.getLong(KEY_LIMIT_BYTES, DEFAULT_LIMIT_BYTES)
                ?: DEFAULT_LIMIT_BYTES

        _autoCleanEnabled.value =
            prefs?.getBoolean(KEY_AUTO_CLEAN, true) ?: true

        refreshStats()
    }

    fun setLimitBytes(bytes: Long) {

        _limitBytes.value = bytes

        prefs?.edit()
            ?.putLong(KEY_LIMIT_BYTES, bytes)
            ?.apply()

        // Si ya estamos por encima del nuevo límite, actuamos ya mismo.
        checkAgainstLimit()
    }

    fun setAutoCleanEnabled(enabled: Boolean) {

        _autoCleanEnabled.value = enabled

        prefs?.edit()
            ?.putBoolean(KEY_AUTO_CLEAN, enabled)
            ?.apply()
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun refreshStats() {

        scope.launch {

            _isBusy.value = true

            val info = TelegramManager.getStorageStatisticsFast()

            _filesSize.value = info.filesSize
            _databaseSize.value = info.databaseSize
            _fileCount.value = info.fileCount

            _isBusy.value = false
        }
    }

    /**
     * Botón "Limpiar archivos" de StorageScreen: borra TODOS los
     * archivos descargados (fotos, miniaturas, fragmentos de video)
     * que TDLib puede volver a descargar cuando haga falta. La base de
     * datos y la sesión quedan intactas.
     */
    fun cleanNow(onDone: (freedBytes: Long) -> Unit = {}) {

        scope.launch {

            _isBusy.value = true

            val before = _filesSize.value

            TelegramManager.optimizeStorage(maxTotalSizeBytes = 0L)

            val info = TelegramManager.getStorageStatisticsFast()

            _filesSize.value = info.filesSize
            _databaseSize.value = info.databaseSize
            _fileCount.value = info.fileCount

            _isBusy.value = false

            val freed = (before - info.filesSize).coerceAtLeast(0L)

            Log.d(TAG, "Limpieza manual: liberados ${formatBytes(freed)}")

            onDone(freed)
        }
    }

    /**
     * TelegramManager llama a esto después de cada descarga (foto,
     * miniatura o fragmento de video vía streaming). Si superamos el
     * límite: con limpieza automática ON, le pedimos a TDLib que
     * recorte; con OFF, solo dejamos un aviso para que la UI lo
     * muestre (por ejemplo un Toast/Snackbar).
     */
    fun onFileDownloaded() {

        scope.launch {

            val info = TelegramManager.getStorageStatisticsFast()

            _filesSize.value = info.filesSize
            _databaseSize.value = info.databaseSize
            _fileCount.value = info.fileCount

            checkAgainstLimitLocked(info.filesSize)
        }
    }

    private fun checkAgainstLimit() {

        scope.launch {
            checkAgainstLimitLocked(_filesSize.value)
        }
    }

    private suspend fun checkAgainstLimitLocked(currentFilesSize: Long) {

        if (currentFilesSize <= _limitBytes.value) {
            return
        }

        if (_autoCleanEnabled.value) {

            TelegramManager.optimizeStorage(
                maxTotalSizeBytes = _limitBytes.value
            )

            val after = TelegramManager.getStorageStatisticsFast()

            _filesSize.value = after.filesSize
            _databaseSize.value = after.databaseSize
            _fileCount.value = after.fileCount

            val freed =
                (currentFilesSize - after.filesSize)
                    .coerceAtLeast(0L)

            _notice.value =
                "Limpieza automática: se liberaron ${formatBytes(freed)}"

        } else {

            _notice.value =
                "Los archivos descargados llegaron a " +
                        "${formatBytes(currentFilesSize)}, por encima " +
                        "de tu límite de ${formatBytes(_limitBytes.value)}"
        }
    }

    fun formatBytes(bytes: Long): String {

        if (bytes < 1024) return "$bytes B"

        val kb = bytes / 1024.0
        if (kb < 1024) return "${kb.roundToInt()} KB"

        val mb = kb / 1024.0
        if (mb < 1024) return "${mb.roundToInt()} MB"

        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }
}
