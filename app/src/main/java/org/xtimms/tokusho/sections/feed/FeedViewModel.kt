package org.xtimms.tokusho.sections.feed

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.xtimms.tokusho.core.base.viewmodel.KotatsuBaseViewModel
import org.xtimms.tokusho.data.repository.TrackingRepository
import org.xtimms.tokusho.utils.lang.MutableEventFlow
import org.xtimms.tokusho.utils.lang.call
import org.xtimms.tokusho.utils.lang.insertSeparators
import org.xtimms.tokusho.work.tracker.TrackWorker
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

private const val PAGE_SIZE = 20

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: TrackingRepository,
    private val trackScheduler: TrackWorker.Scheduler,
) : KotatsuBaseViewModel() {

    private val limit = MutableStateFlow(PAGE_SIZE)
    private val isReady = AtomicBoolean(false)

    val isRunning = trackScheduler.observeIsRunning()
        .stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, false)

    val onFeedCleared = MutableEventFlow<Unit>()

    val content = repository.observeTrackingLog(limit)
            .stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

    init {
        launchJob(Dispatchers.Default) {
            repository.gc()
        }
    }

    fun clearFeed(clearCounters: Boolean) {
        launchLoadingJob(Dispatchers.Default) {
            repository.clearLogs()
            if (clearCounters) {
                repository.clearCounters()
            }
            onFeedCleared.call(Unit)
        }
    }

    fun updateFeed() {
        trackScheduler.startNow()
    }

    fun getUiModel(): List<FeedUiModel> {
        return content.value
            .map { FeedUiModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.createdAt
                val afterDate = after?.item?.createdAt
                when {
                    beforeDate != afterDate && afterDate != null -> FeedUiModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
    }
}