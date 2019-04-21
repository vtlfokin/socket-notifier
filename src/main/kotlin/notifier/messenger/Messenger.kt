package notifier.messenger

import com.google.gson.Gson
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.WebSocketSession
import io.ktor.http.cio.websocket.close
import notifier.User
import notifier.session.storage.SessionStorage

val gson = Gson()

class Messenger(private val sessionStorage: SessionStorage) {
    fun registerUserSession(user: User, session: WebSocketSession) {
        sessionStorage.registerNewUserSession(user.id, session)

        println("Registered user ${user.id} new session $session")
    }

    fun removeUserSession(user: User, session: WebSocketSession) {
        sessionStorage.removeUserSession(user.id, session)

        println("Removed user ${user.id} session $session")
    }

    suspend fun kickUserById(userId: String) {
        sessionStorage.removeUserAllSessions(userId).forEach {
            it.close(
                CloseReason(
                    CloseReason.Codes.NORMAL,
                    "You are kicked"
                )
            )
        }
    }

    suspend fun notifyUsersByIds(usersIds: List<String>, message: Message) {
        for (userId in usersIds) {
            sessionStorage.getUserSessions(userId).forEach {
                it.send(Frame.Text(
                    gson.toJson(message)
                ))
            }
        }
    }

    suspend fun broadcast(message: Message) {
        sessionStorage.allAvailableSessions().forEach {
            it.send(Frame.Text(
                gson.toJson(message)
            ))
        }
    }
}

enum class MessageType {
    ALERT, WARNING, SUCCESS
}

data class Message(val type: MessageType, val text: String)