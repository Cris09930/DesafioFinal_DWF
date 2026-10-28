package sv.edu.dwf404.ventas.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Configuración de seguridad leída desde application.yml / variables de entorno.
 * Los secretos NO tienen valor por defecto: si JWT_SECRET no está definido la aplicación no arranca.
 */
@ConfigurationProperties(prefix = "app.security")
public record SeguridadProperties(Jwt jwt, Keycloak keycloak) {

    /** JWT emitidos por esta API (login con usuario y contraseña). */
    public record Jwt(String secret, String issuer, String audience, Duration expiration) {
        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException(
                        "app.security.jwt.secret (variable JWT_SECRET) es obligatorio y debe tener al menos 32 bytes (256 bits) para HS256");
            }
            if (expiration == null) {
                expiration = Duration.ofMinutes(20);
            }
        }
    }

    /** Proveedor de identidad OAuth2 externo (Keycloak). */
    public record Keycloak(String issuerUri, String jwkSetUri, String audience) {
    }
}
