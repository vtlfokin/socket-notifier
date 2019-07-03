package notifier.auth

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.*
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.response.respond

/**
 * Represents a Simple token authentication provider
 * @param name is the name of the provider, or `null` for a default provider
 */
class SimpleTokenAuthenticationProvider(name: String?) : AuthenticationProvider(name) {
    internal var authenticationFunction: suspend ApplicationCall.(UserTokenCredential) -> Principal? = { credential ->
        if (credential.token == token) provider else null
    }

    /**
     * Specifies field name that contains token
     */
    var tokenField: String = "token"

    var token: String = "secret"
}

data class UserTokenCredential(val token: String) : Credential

val provider = object : Principal {
    override fun toString(): String {
        return "Provider"
    }
}

/**
 * Installs Basic Authentication mechanism
 */
fun Authentication.Configuration.token(name: String? = null, configure: SimpleTokenAuthenticationProvider.() -> Unit) {
    val provider = SimpleTokenAuthenticationProvider(name).apply(configure)
    val tokenField = provider.tokenField
    val authenticate = provider.authenticationFunction

    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val credentials = call.request.simpleTokenAuthenticationCredentials(tokenField)
        val principal = credentials?.let { authenticate(call, it) }

        val cause = when {
            credentials == null -> AuthenticationFailedCause.NoCredentials
            principal == null -> AuthenticationFailedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge(basicAuthenticationChallengeKey, cause) {
                call.respond(HttpStatusCode.Unauthorized)
                it.complete()
            }
        }
        if (principal != null) {
            context.principal(principal)
        }
    }

    register(provider)
}

/**
 * Retrieves Token credentials for this [ApplicationRequest]
 */
fun ApplicationRequest.simpleTokenAuthenticationCredentials(field: String): UserTokenCredential? {
    val parsed = call.parameters[field]
    when (parsed) {
        is String -> {
            if (parsed.isEmpty()) {
                return null
            }

            return UserTokenCredential(parsed)
        }
        else -> return null
    }
}

private val basicAuthenticationChallengeKey: Any = "TokenAuth"