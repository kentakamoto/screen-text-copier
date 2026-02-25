package com.kentakamoto.screentextcopier.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kentakamoto.screentextcopier.data.AppPreferences
import com.kentakamoto.screentextcopier.data.CopyMode
import com.kentakamoto.screentextcopier.service.ScreenTextAccessibilityService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    val isServiceRunning: StateFlow<Boolean> =
        ScreenTextAccessibilityService.isRunning

    val copyMode: StateFlow<CopyMode> = AppPreferences.copyModeFlow(ctx)
        .stateIn(viewModelScope, SharingStarted.Eagerly, CopyMode.CLIPBOARD)

    val buttonOpacity: StateFlow<Float> = AppPreferences.buttonOpacityFlow(ctx)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences.DEFAULT_OPACITY)

    val buttonSizeDp: StateFlow<Int> = AppPreferences.buttonSizeFlow(ctx)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences.DEFAULT_SIZE_DP)

    fun setCopyMode(mode: CopyMode) {
        viewModelScope.launch { AppPreferences.saveCopyMode(ctx, mode) }
    }

    fun setButtonOpacity(opacity: Float) {
        viewModelScope.launch { AppPreferences.saveButtonOpacity(ctx, opacity) }
    }

    fun setButtonSizeDp(sizeDp: Int) {
        viewModelScope.launch { AppPreferences.saveButtonSize(ctx, sizeDp) }
    }
}
