package notifier.auth.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT

val verifier = JWT
    .require(Algorithm.HMAC512("secret"))
    .withIssuer("ktor")
    .build()

fun verifyToken(token: String): DecodedJWT {
    return verifier.verify(token)
}