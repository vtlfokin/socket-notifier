package notifier

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.*
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.*
import io.ktor.request.receiveParameters
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.cli.CommandLineInterface
import kotlinx.cli.flagValueArgument
import kotlinx.cli.parse
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import notifier.auth.jwt.JwtTokenVerifier
import notifier.auth.token
import notifier.messenger.Message
import notifier.messenger.MessageType
import notifier.messenger.Messenger
import notifier.session.storage.SessionStorage
import java.util.*
import kotlin.system.exitProcess

data class User(val id: String): Principal

class Config {
    var port = 8000
    var clientAuthSecret = "secret"
    var masterToken = "super_secret"
}

@ObsoleteCoroutinesApi
class Notifier(private val config: Config) {
    fun start() {
        val sessionStorage = SessionStorage()
        val messenger = Messenger(sessionStorage)

        val jwtTokenVerifier = JwtTokenVerifier(config.clientAuthSecret)

        println(
            JWT.create()
                .withSubject("Auth")
                .withIssuer("notifier")
                .withClaim("id", "1")
                .withExpiresAt(Date(System.currentTimeMillis() + 36000000))
                .sign(Algorithm.HMAC512(config.clientAuthSecret))
        )

        embeddedServer(Netty, config.port) {
            install(WebSockets)
            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                }
            }
            install(Authentication) {
                token {
                    tokenField = "token"
                    token = config.masterToken
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

                    post("/private/{id}") {
                        try {
                            val target = call.parameters["id"]!!
                            val parameters = call.receiveParameters()
                            val messageType = parameters["type"]!!
                            val content = parameters["content"]!!

                            messenger.notifyUsersByIds(
                                listOf(target),
                                Message(MessageType.valueOf(messageType.toUpperCase()), content)
                            )

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
                                    val jwt = jwtTokenVerifier.verify(incomeText)
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
}

fun registerConfigFromArgs(args: Array<String>): Config {
    val cli = CommandLineInterface("Notifier", printHelpByDefault = false)

    val portArg by cli.flagValueArgument(
        "-p",
        "port",
        "Port to listen",
        8000) { it.toInt() }
    val authSecretArg by cli.flagValueArgument(
        "-s",
        "secret",
        "Secret for jwt verify",
        "secret")
    val masterTokenArg by cli.flagValueArgument(
        "-m",
        "token",
        "Master token for authenticate master",
        "super_secret")

    // Parse arguments or exit
    try {
        cli.parse(args)
    } catch (e: Exception) {
        exitProcess(1)
    }

    return Config().apply {
        port = portArg
        clientAuthSecret = authSecretArg
        masterToken = masterTokenArg
    }
}

@ObsoleteCoroutinesApi
fun main(args: Array<String>) {
    println(args.asList())

    val config = registerConfigFromArgs(args)

    println(config.port)
    println(config.clientAuthSecret)
    println(config.masterToken)

    val notifier = Notifier(config)

    notifier.start()
}