package com.example.ui.scanner

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RiadAiPersona
import com.example.data.RiadAiRepository
import com.example.data.ScanDepth
import com.example.data.ScannerUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "VulnScannerVM"

data class VulnScannerScreenState(
    val sourceInput: String = RiadAiPersona.SCANNER_PRESETS.first().code,
    val selectedDepth: ScanDepth = ScanDepth.DEEP_AUDIT,
    val scanState: ScannerUiState = ScannerUiState.Idle
)

class VulnScannerViewModel(
    private val repository: RiadAiRepository = RiadAiRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(VulnScannerScreenState())
    val uiState: StateFlow<VulnScannerScreenState> = _uiState.asStateFlow()

    fun updateSourceInput(code: String) {
        _uiState.update { it.copy(sourceInput = code) }
    }

    fun selectDepth(depth: ScanDepth) {
        _uiState.update { it.copy(selectedDepth = depth) }
    }

    fun loadPreset(code: String) {
        _uiState.update { it.copy(sourceInput = code, scanState = ScannerUiState.Idle) }
    }

    fun runStaticAnalysis() {
        val target = _uiState.value.sourceInput.trim()
        if (target.isEmpty()) {
            _uiState.update {
                it.copy(scanState = ScannerUiState.Error("Target payload is empty. Paste source code or HTTP headers."))
            }
            return
        }

        val depth = _uiState.value.selectedDepth
        viewModelScope.launch {
            _uiState.update { it.copy(scanState = ScannerUiState.Scanning) }
            try {
                val report = repository.analyzeVulnerability(target, depth)
                _uiState.update { it.copy(scanState = ScannerUiState.Success(report)) }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Vulnerability scan timed out", e)
                _uiState.update {
                    it.copy(scanState = ScannerUiState.Error("Static analysis timed out after 60 seconds."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Vulnerability scan failed", e)
                _uiState.update {
                    it.copy(
                        scanState = ScannerUiState.Error(
                            e.localizedMessage ?: "Static security analysis failed."
                        )
                    )
                }
            }
        }
    }
}
