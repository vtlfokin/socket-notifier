package notifier.session.storage

import io.ktor.http.cio.websocket.WebSocketSession
import notifier.User
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SessionStorage {

    private val userSessions =
        ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    fun registerNewUserSession(userId: String, session: WebSocketSession) {
        val set = userSessions.computeIfAbsent(userId) {
            Collections.synchronizedSet(mutableSetOf())
        }

        set.add(session)
    }

    fun removeUserSession(userId: String, session: WebSocketSession) {
        userSessions[userId]?.also {
            it.remove(session)
        }

        if (null != userSessions[userId] && userSessions[userId]!!.isEmpty()) {
            userSessions.remove(userId)
        }
    }

    fun removeUserAllSessions(userId: String): MutableSet<WebSocketSession> {
        val sessionsSet = userSessions.remove(userId)

        return sessionsSet ?: mutableSetOf()
    }

    fun getUserSessions(userId: String): List<WebSocketSession> {
        return userSessions[userId]?.toList() ?: listOf()
    }

    fun allAvailableSessions(): List<WebSocketSession> {
        return userSessions.values.flatMap { it.toList() }
    }
}