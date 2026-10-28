package sv.edu.dwf404.ventas.auth;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import sv.edu.dwf404.ventas.seguridad.SeguridadProperties;
import sv.edu.dwf404.ventas.usuario.Usuario;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Emite los JWT propios de la API después de un login correcto. */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final SeguridadProperties.Jwt config;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, SeguridadProperties props) {
        this.encoder = encoder;
        this.config = props.jwt();
        this.clock = Clock.systemUTC();
    }

    public String generar(Usuario usuario) {
        Instant ahora = clock.instant();
        //Se firma el token
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(config.issuer())                         // iss: quién emite
                .audience(List.of(config.audience()))            // aud: para quién es
                .subject(usuario.getEmail())                     // sub: identidad
                .id(UUID.randomUUID().toString())                // jti
                .issuedAt(ahora)                                 // iat
                .expiresAt(ahora.plus(config.expiration()))      // exp
                .claim("nombre", usuario.getNombreCompleto())
                .claim("roles", List.of(usuario.getRol().name()))
                // Informativo para el cliente; la API recalcula los permisos desde el rol al validar.
                .claim("permisos", usuario.getRol().getPermisos().stream().sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long segundosDeVigencia() {
        return config.expiration().toSeconds();
    }
}
