package com.example.fypdraft.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.MoodAnalytics
import com.example.fypdraft.data.repository.MoodHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AnalyticsTimeRange(val days: Int, val label: String) {
    WEEK(7, "7 days"),
    TWO_WEEKS(14, "14 days"),
    MONTH(30, "30 days"),
    THREE_MONTHS(90, "3 months")
}

class MoodHistoryViewModel : ViewModel() {
    private val TAG = "MoodHistoryVM"
    private val repository = MoodHistoryRepository()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _analytics = MutableStateFlow<MoodAnalytics?>(null)
    val analytics: StateFlow<MoodAnalytics?> = _analytics.asStateFlow()

    private val _selectedRange = MutableStateFlow(AnalyticsTimeRange.MONTH)
    val selectedRange: StateFlow<AnalyticsTimeRange> = _selectedRange.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { loadAnalytics() }

    fun setTimeRange(range: AnalyticsTimeRange) { _selectedRange.value = range; loadAnalytics() }

    fun loadAnalytics() {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try {
                val entries = repository.getMoodHistoryForDays(_selectedRange.value.days)
                _analytics.value = repository.computeAnalytics(entries)
                Log.d(TAG, "Loaded ${_analytics.value?.totalEntries} entries")
            } catch (e: Exception) {
                Log.e(TAG, "Failed", e); _error.value = "Failed: ${e.message}"
            } finally { _isLoading.value = false }
        }
    }

    fun refresh() = loadAnalytics()
}