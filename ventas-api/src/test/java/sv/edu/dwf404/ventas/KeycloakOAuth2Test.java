package sv.edu.dwf404.ventas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escenario 6 (parte automatizada): un token cuyo emisor es Keycloak se enruta al decodificador de Keycloak
 * y sus roles de realm (realm_access.roles) se convierten en los permisos de la API.
 * <p>
 * Aquí se sustituye SOLO el decodificador de Keycloak (no hay Keycloak en CI). La validación real de firma RS256
 * contra el JWKS de Keycloak se demuestra end-to-end con la colección de Postman (carpeta "6. OAuth2 Keycloak").
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KeycloakOAuth2Test extends PruebaBase {

    private static final String ISSUER_KC = "http://keycloak.test/realms/dwf404";

    @MockitoBean(name = "keycloakJwtDecoder")
    JwtDecoder keycloakJwtDecoder;

    @Test
    void tokenKeycloakEmpleadoPuedeRegistrarPedido() throws Exception {
        String token = tokenConEmisor(ISSUER_KC, "valido");
        when(keycloakJwtDecoder.decode(token)).thenReturn(jwtKeycloak(token, "EMPLEADO", "kc-empleado"));

        mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(pedidoJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creadoPor").value("kc-empleado"));
        // Mismo token, operación de ADMIN -> 403
        mvc.perform(delete("/api/productos/" + productoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenKeycloakAdminConsultaUsuarios() throws Exception {
        String token = tokenConEmisor(ISSUER_KC, "admin");
        when(keycloakJwtDecoder.decode(token)).thenReturn(jwtKeycloak(token, "ADMIN", "kc-admin"));

        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tokenKeycloakRechazadoPorElValidador() throws Exception {
        String token = tokenConEmisor(ISSUER_KC, "rechazado");
        when(keycloakJwtDecoder.decode(eq(token))).thenThrow(
                new JwtValidationException("Jwt expired", List.of(new OAuth2Error("invalid_token"))));

        mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenKeycloakSinRolesConocidosNoTienePermisos() throws Exception {
        String token = tokenConEmisor(ISSUER_KC, "sinrol");
        when(keycloakJwtDecoder.decode(anyString())).thenReturn(jwtKeycloak(token, "offline_access", "kc-nadie"));

        mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /** JWT con formato válido y claim iss (la firma la "valida" el decodificador sustituido). */
    private static String tokenConEmisor(String issuer, String jti) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64.encodeToString(("{\"iss\":\"" + issuer + "\",\"jti\":\"" + jti + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + b64.encodeToString("firma".getBytes(StandardCharsets.UTF_8));
    }

    private static Jwt jwtKeycloak(String token, String rolRealm, String usuario) {
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .issuer(ISSUER_KC)
                .audience(List.of("ventas-api", "account"))
                .subject("f2a1c0de-0000-4000-8000-000000000001")
                .claim("preferred_username", usuario)
                .claim("realm_access", Map.of("roles", List.of(rolRealm, "default-roles-dwf404")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
