package com.revosleap.text.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.revosleap.text.FarakhvanApp
import com.revosleap.text.R
import com.revosleap.text.data.Recipient
import com.revosleap.text.sending.SmsSendService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FarakhvanApp
    val repository = app.repository
    val dao = repository.dao
    val preferences = app.preferences
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableStartupError = MutableStateFlow(false)
    val startupError = mutableStartupError.asStateFlow()
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = mutableMessages.asSharedFlow()
    init {
        viewModelScope.launch {
            try {
                if (!SmsSendService.alive) repository.recover()
                mutableReady.value = true
            } catch (_: Exception) { mutableStartupError.value = true; notify(R.string.error_database) }
        }
    }
    fun notify(string: Int, vararg args: Any) {
        mutableMessages.tryEmit(app.getString(string, *args))
    }
    fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) { notify(R.string.error_permission) }
            catch (_: Exception) { notify(R.string.error_general) }
        }
    }
    suspend fun enrich(rows: List<Recipient>, canReadContacts: Boolean): List<Recipient> =
        if (canReadContacts) repository.withContactNames(rows) else rows
}
