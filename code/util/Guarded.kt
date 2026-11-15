package app.captunes.util

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * Like runCatching, with two differences:
 *  - a failure is written to the log (tag "CapTunes") instead of vanishing, so problems can be traced;
 *  - cancellation is passed on, so a cancelled job stops instead of carrying on with a fallback value.
 *
 * [where] says which part of the app this is, e.g. the file name.
 */
inline fun <T> guarded(where: String, block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logFailure(where, e)
        Result.failure(e)
    }

/** Logs the kind of error and its message with any web address cut down, so no sign-in token is logged. */
fun logFailure(where: String, e: Throwable) {
    val message = e.message.orEmpty().replace(ADDRESS, "$1…").take(300)
    runCatching { Log.w("CapTunes", "$where: ${e.javaClass.simpleName}: $message") }
}

/** Keeps "http://host" and drops the path and query, which is where the token lives. */
private val ADDRESS = Regex("""(https?://[^/\s?]+)\S*""")
