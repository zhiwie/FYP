package com.example.fypdraft.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.CheckInState
import com.example.fypdraft.data.repository.DailyMood
import com.example.fypdraft.data.repository.MoodCheckInRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * MoodCheckInViewModel
 *
 * Bridges [MoodCheckInRepository] with the UI layer.
 * Exposed as a [StateFlow] so composables can `collectAsState()` with no
 * additional ceremony.
 *
 * Usage in HomeScreen:
 *   val vm: MoodCheckInViewModel = viewModel(factory = MoodCheckInViewModel.factory(context))
 *   val state by vm.state.collectAsState()
 */
class MoodCheckInViewModel(
    private val repository: MoodCheckInRepository
) : ViewModel() {

    /** Observable check-in state. Immediately emits the persisted value on first collection. */
    val state: StateFlow<CheckInState> = repository.todayCheckIn
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = CheckInState()
        )

    /**
     * Called when the user taps a mood in the check-in card.
     * No-op if they have already checked in today (enforced by the repository).
     */
    fun checkIn(mood: DailyMood) {
        viewModelScope.launch {
            repository.saveTodayCheckIn(mood.key)
        }
    }

    /**
     * For free-form "Others" mood — pass the raw key string e.g. "others:Grateful".
     * Repository treats same-day saves as emotion-only updates (streak is preserved).
     */
    fun checkInRaw(moodKey: String) {
        viewModelScope.launch {
            repository.saveTodayCheckIn(moodKey)
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repo = MoodCheckInRepository(context.applicationContext)
                    return MoodCheckInViewModel(repo) as T
                }
            }
    }
}