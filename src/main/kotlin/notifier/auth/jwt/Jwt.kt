package notifier.auth.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT

class JwtTokenVerifier(secret: String) {
    private val verifier = JWT
        .require(Algorithm.HMAC512(secret))
        .withIssuer("notifier")
        .build()!!

    fun verify(token: String): DecodedJWT {
        return verifier.verify(token)
    }
}