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
import io.ktor.http.cio.websocket.*
import io.ktor.request.receive
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
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

    val verifier = JWT
        .require(Algorithm.HMAC512("secret"))
        .withIssuer("ktor")
        .build()

    embeddedServer(Netty, 8000) {
        install(WebSockets)
        install(Authentication) {
            jwt {
                verifier(verifier)
                realm = "ktor.io"
                validate { call ->
                    call.payload.getClaim("id").asString()?.let {
                        User(it)
                    }
                }
            }
        }
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
            }
        }

        routing {
            authenticate {
                get("/") {
                    val user = call.principal<User>()!!

                    call.respond(user)
                }
            }

            post("/broadcast") {
                val type = call.parameters["type"]
                val content = call.receiveText()

                if (null != type && content.isNotEmpty()) {
                    messenger.broadcast(
                        Message(MessageType.valueOf(type.toUpperCase()), content)
                    )
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
                                val jwt = verifier.verify(incomeText)
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
                    // return because user is never added if failed authentication
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
