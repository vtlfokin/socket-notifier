package notifier

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.*
import io.ktor.auth.jwt.jwt
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.*
import io.ktor.request.receive
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import notifier.auth.jwt.verifyToken
import notifier.auth.token
import notifier.messenger.Message
import notifier.messenger.MessageType
import notifier.messenger.Messenger
import notifier.session.storage.SessionStorage
import java.util.*

data class User(val id: String): Principal

val sessionStorage = SessionStorage()
val messenger = Messenger(sessionStorage)

@ObsoleteCoroutinesApi
fun main(args: Array<String>) {
    println(
        JWT.create()
        .withSubject("Auth")
        .withIssuer("ktor")
        .withClaim("id", "1")
        .withExpiresAt(Date(System.currentTimeMillis() + 36000000))
        .sign(Algorithm.HMAC512("secret"))
    )

    embeddedServer(Netty, 8000) {
        install(WebSockets)
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
            }
        }
        install(Authentication) {
            token {
                tokenField = "token"
                token = "super_secret"
            }
        }

        routing {
            authenticate {
                post("/broadcast") {
                    try {
                        val type = call.parameters["type"]
                        val content = call.receiveText()

                        if (null != type && content.isNotEmpty()) {
                            messenger.broadcast(
                                Message(MessageType.valueOf(type.toUpperCase()), content)
                            )
                        }
                        call.respond(HttpStatusCode.OK)
                    } catch (exception: Exception) {
                        call.respondText(
                            text = exception.message ?: "",
                            status = HttpStatusCode.BadRequest
                        )
                    }
                }
            }

            webSocket("/subscribe") {
                var user: User? = null

                try {
                    // We starts receiving messages (frames).
                    // Since this is a coroutine. This coroutine is suspended until receiving frames.
                    // Once the connection is closed, this consumeEach will finish and the code will continue.
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val incomeText = frame.readText()
                            if (null == user) {
                                val jwt = verifyToken(incomeText)
                                user = User(jwt.getClaim("id").asString()).also {
                                    messenger.registerUserSession(it, this)
                                }
                            }
                        }
                    }
                } catch (e: JWTVerificationException) {
                    close(CloseReason(
                        CloseReason.Codes.VIOLATED_POLICY,
                        e.message ?: ""
                    ))
                    // return because user is has never added if fail authentication
                    return@webSocket
                } finally {
                    user?.let {
                        messenger.removeUserSession(it, this)
                    }
                    // Either if there was an error, of it the connection was closed gracefully.
                    // We notify the server that the member left.
                }
            }
        }
    }.start(true)
}
