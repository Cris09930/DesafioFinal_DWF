package sv.edu.dwf404.ventas.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Codificador y decodificadores JWT.
 * <ul>
 *   <li>localJwtDecoder: valída los tokens que emite esta API (HS256, clave desde JWT_SECRET).</li>
 *   <li>keycloakJwtDecoder: valida los tokens de Keycloak (RS256, claves públicas del endpoint JWKS).</li>
 * </ul>
 * Ambos validan firma + vigencia (exp/nbf) + emisor (iss) + audiencia (aud).
 */
@Configuration
public class JwtConfig {

    //Clave HMAC
    @Bean
    SecretKey jwtSecretKey(SeguridadProperties props) {
        return new SecretKeySpec(props.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    //Firmador de tokens
    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    //Se valida el token
    @Bean
    JwtDecoder localJwtDecoder(SecretKey jwtSecretKey, SeguridadProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(validadores(props.jwt().issuer(), props.jwt().audience()));
        return decoder;
    }

    /**
     * El conjunto de claves (JWKS) se descarga de forma perezosa en la primera validación,
     * por lo que la API puede arrancar aunque Keycloak aún no esté levantado.
     */
    @Bean
    JwtDecoder keycloakJwtDecoder(SeguridadProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.keycloak().jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validadores(props.keycloak().issuerUri(), props.keycloak().audience()));
        return decoder;
    }

    /** Validador por defecto (exp, nbf con 60 s de tolerancia) + emisor exacto + audiencia obligatoria. */
    static OAuth2TokenValidator<Jwt> validadores(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> audienciaValida = new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienciaValida);
    }
}
