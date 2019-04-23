package notifier.auth

import io.ktor.application.ApplicationCall

//#todo create custom authentication method
//https://github.com/ktorio/ktor/tree/master/ktor-features/ktor-auth/jvm/src/io/ktor/auth

fun ApplicationCall.administratorAuthenticate() {
    val token: String = this.parameters["token"] ?: throw Exception("Lost administrator token")

    if (token != "secret") throw Exception("Invalid administrator token")
}