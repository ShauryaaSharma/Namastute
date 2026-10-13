package com.namastute.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

// Short-lived access tokens: signed JWTs that say "this is user X until time T"
@Service
class AccessTokens {

    static final Duration LIFETIME = Duration.ofMinutes(15);
    static final String ISSUER = "namastute-api";

    private final JwtEncoder encoder;

    AccessTokens(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    String issue(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())   // only the id: anyone can read a JWT's payload
                .issuedAt(now)
                .expiresAt(now.plus(LIFETIME))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
