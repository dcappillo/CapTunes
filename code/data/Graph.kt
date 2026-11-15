package app.captunes.data

import android.content.Context
import app.captunes.playback.PlayerController

/** App-wide singletons: the signed-in session, its repository and the player. */
object Graph {
    @Volatile
    var session: Session? = null
        private set

    @Volatile
    var repo: Repository? = null
        private set

    /** Where songs, covers and stream addresses come from right now. */
    @Volatile
    var source: MusicSource? = null
        private set

    @Volatile
    private var player: PlayerController? = null

    fun init(context: Context) {
        Lookups.load(context.applicationContext)
        if (session == null) {
            SessionStore.load(context.applicationContext)?.let { install(context, it) }
        }
    }

    fun signIn(context: Context, newSession: Session) {
        SessionStore.save(context.applicationContext, newSession)
        install(context, newSession)
    }

    fun signOut(context: Context) {
        SessionStore.clear(context.applicationContext)
        session = null
        source = null
        repo = null
    }

    private fun install(context: Context, s: Session) {
        session = s
        val log = PlayLog.get(context)
        val src: MusicSource = if (s.isLocal) LocalSource(context, log) else SubsonicSource(SubsonicApi(s), s)
        source = src
        repo = Repository(src, log)
    }

    fun player(context: Context): PlayerController =
        player ?: synchronized(this) {
            player ?: PlayerController(context.applicationContext).also { player = it }
        }
}
