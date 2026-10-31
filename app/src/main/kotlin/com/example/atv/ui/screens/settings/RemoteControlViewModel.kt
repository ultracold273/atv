package com.example.atv.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.example.atv.data.remotecontrol.RemoteControlManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RemoteControlViewModel @Inject constructor(private val control: RemoteControlManager) : ViewModel() {
    val state = control.state
    fun setEnabled(enabled: Boolean) = control.setEnabled(enabled)
    fun rotateCredential() = control.rotateCredential()
    fun retry() = control.retry()
}
