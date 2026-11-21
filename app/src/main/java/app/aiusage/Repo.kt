package app.aiusage

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex

object Repo {
    val refreshing = MutableStateFlow(false)
    private val lock = Mutex()

    /** Asks every connected service, stores the answers, redraws the widget. Overlapping calls are skipped. */
    suspend fun refreshAll(context: Context) {
        if (!lock.tryLock()) return
        refreshing.value = true
        val requested = Store.refreshRequestedAt(context)
        try {
            for (p in Provider.ALL) {
                if (!p.signedIn()) {
                    Store.clear(context, p)
                    continue
                }
                try {
                    Store.save(context, p, p.fetch(context))
                } catch (e: NotSignedIn) {
                    Store.clear(context, p)
                    p.signOut()
                    Store.saveError(context, p, "Signed out. Connect again.")
                } catch (e: CancellationException) {
                    throw e // a newer refresh replaced this one: not an error to show
                } catch (e: Exception) {
                    // Keep the old numbers on screen; just say what went wrong.
                    Diag.log("refresh ${p.id} failed: ${e.message}")
                    Store.saveError(context, p, e.message ?: "Failed")
                }
            }
            if (requested != 0L) {
                // Keep "Refreshing..." on screen long enough to be noticed, then show a green tick for a few seconds.
                val wait = 1500 - (System.currentTimeMillis() - requested)
                if (wait > 0) delay(wait)
                Store.setRefreshRequestedAt(context, 0L)
                Store.setRefreshDoneAt(context, System.currentTimeMillis())
                UsageWidget.updateAll(context)
                delay(3000)
                Store.setRefreshDoneAt(context, 0L)
            } else {
                Store.setRefreshRequestedAt(context, 0L)
            }
            UsageWidget.updateAll(context)
        } finally {
            refreshing.value = false
            lock.unlock()
        }
    }
}
