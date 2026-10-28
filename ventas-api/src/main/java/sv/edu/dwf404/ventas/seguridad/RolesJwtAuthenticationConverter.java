package sv.edu.dwf404.ventas.seguridad;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import sv.edu.dwf404.ventas.usuario.Rol;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Convierte un JWT YA VALIDADO (firma, emisor, audiencia y vigencia) en una autenticación de Spring Security.
 * <p>
 * Solo se lee el ROL desde el token firmado; los permisos se calculan aquí, en el servidor, a partir del
 * enum {@link Rol}. Así un cliente no puede "inventarse" permisos y el mismo modelo de autorización aplica
 * a los tokens propios y a los emitidos por Keycloak.
 */
public class RolesJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final Function<Jwt, Collection<String>> extractorRoles;
    private final String claimNombre;

    private RolesJwtAuthenticationConverter(Function<Jwt, Collection<String>> extractorRoles, String claimNombre) {
        this.extractorRoles = extractorRoles;
        this.claimNombre = claimNombre;
    }

    /** Tokens propios: claim "roles" y sujeto = correo del usuario. */
    public static RolesJwtAuthenticationConverter paraTokensLocales() {
        return new RolesJwtAuthenticationConverter(
                jwt -> Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of()),
                "sub");
    }

    /** Tokens de Keycloak: roles de realm en realm_access.roles, nombre en preferred_username. */
    public static RolesJwtAuthenticationConverter paraKeycloak() {
        return new RolesJwtAuthenticationConverter(RolesJwtAuthenticationConverter::rolesRealmKeycloak, "preferred_username");
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> rolesRealmKeycloak(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return ((Collection<Object>) roles).stream().map(String::valueOf).toList();
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String nombreRol : extractorRoles.apply(jwt)) {
            Rol.desde(nombreRol).ifPresent(rol -> {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.name()));
                rol.getPermisos().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
            });
        }
        String nombre = Optional.ofNullable(jwt.getClaimAsString(claimNombre)).orElse(jwt.getSubject());
        return new JwtAuthenticationToken(jwt, authorities, nombre);
    }
}
