package sv.edu.dwf404.ventas;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escenarios 1-5 y 7 del Módulo 5 usando tokens REALES emitidos por /api/auth/login.
 * (El escenario 6, Keycloak, está en {@link KeycloakOAuth2Test}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadJwtTest extends PruebaBase {

    @Autowired
    JwtEncoder jwtEncoder;

    @Nested
    @DisplayName("1. Solicitud sin token -> 401")
    class SinToken {
        @Test
        void productosSinToken() throws Exception {
            mvc.perform(get("/api/productos"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        void pedidosSinToken() throws Exception {
            mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(pedidoJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void loginConClaveIncorrecta() throws Exception {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + ADMIN + "\",\"password\":\"incorrecta\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"));
        }
    }


    @Nested
    @DisplayName("2. Token válido con permisos suficientes -> 2xx")
    class PermisosSuficientes {
        @Test
        void consultaListaProductos() throws Exception {
            mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer(login(USUARIO))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].nombre").value("Café"));
        }

        @Test
        void adminAdministraProductos() throws Exception {
            String token = login(ADMIN);
            String body = "{\"nombre\":\"Hamaca\",\"descripcion\":\"Artesanal\",\"precio\":32.00,\"stock\":5}";
            mvc.perform(post("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
            mvc.perform(put("/api/productos/" + productoId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Café premium\",\"precio\":9.75,\"stock\":40}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Café premium"));
            mvc.perform(delete("/api/productos/" + productoId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNoContent());
        }

        @Test
        void adminConsultaUsuarios() throws Exception {
            mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(login(ADMIN))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        }

        @Test
        void empleadoRegistraYConsultaPedido() throws Exception {
            String token = login(EMPLEADO);
            mvc.perform(post("/api/pedidos").header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(pedidoJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.creadoPor").value(EMPLEADO))
                    .andExpect(jsonPath("$.total").value(17.00));
            mvc.perform(get("/api/pedidos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        void apiSinSesion() throws Exception {
            MvcResult r = mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer(login(USUARIO))))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                    .andReturn();
            assertThat(r.getRequest().getSession(false)).as("No debe crearse HttpSession").isNull();
        }
    }


    @Nested
    @DisplayName("3. Token válido sin permisos suficientes -> 403")
    class SinPermisos {
        @Test
        void consultaNoPuedeCrearProductos() throws Exception {
            mvc.perform(post("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer(login(USUARIO)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"X\",\"precio\":1.00,\"stock\":1}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        void consultaNoPuedeRegistrarPedidos() throws Exception {
            mvc.perform(post("/api/pedidos").header(HttpHeaders.AUTHORIZATION, bearer(login(USUARIO)))
                            .contentType(MediaType.APPLICATION_JSON).content(pedidoJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void empleadoNoPuedeEliminarProductos() throws Exception {
            mvc.perform(delete("/api/productos/" + productoId).header(HttpHeaders.AUTHORIZATION, bearer(login(EMPLEADO))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void empleadoNoPuedeConsultarUsuarios() throws Exception {
            mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(login(EMPLEADO))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void adminNoRegistraPedidos() throws Exception {
            mvc.perform(post("/api/pedidos").header(HttpHeaders.AUTHORIZATION, bearer(login(ADMIN)))
                            .contentType(MediaType.APPLICATION_JSON).content(pedidoJson()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("4. Token alterado o inválido -> 401")
    class TokenAlterado {
        @Test
        void payloadModificadoParaEscalarRol() throws Exception {
            String[] partes = login(USUARIO).split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                    .replace("USUARIO", "ADMIN");
            String alterado = partes[0] + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                    + "." + partes[2];
            mvc.perform(post("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer(alterado))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"X\",\"precio\":1.00,\"stock\":1}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
        }

        @Test
        void firmaModificada() throws Exception {
            String token = login(USUARIO);
            char ultimo = token.charAt(token.length() - 5);
            String alterado = token.substring(0, token.length() - 5) + (ultimo == 'A' ? 'B' : 'A') + token.substring(token.length() - 4);
            mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer(alterado)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void tokenSinFormatoJwt() throws Exception {
            mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer("esto-no-es-un-jwt")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void firmadoConOtraClave() throws Exception {
            JwtEncoder atacante = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                    "clave-del-atacante-con-mas-de-32-bytes-xx".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
            String token = firmar(atacante, "ventas-api-auth", "ventas-api", Instant.now(), Instant.now().plusSeconds(600));
            mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void emisorNoConfiable() throws Exception {
            String token = firmar(jwtEncoder, "https://emisor-desconocido.example", "ventas-api",
                    Instant.now(), Instant.now().plusSeconds(600));
            mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void audienciaIncorrecta() throws Exception {
            String token = firmar(jwtEncoder, "ventas-api-auth", "otra-api", Instant.now(), Instant.now().plusSeconds(600));
            mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized());
        }
    }


    @Nested
    @DisplayName("5. Token expirado -> 401")
    class TokenExpirado {
        @Test
        void tokenExpirado() throws Exception {
            Instant hace2h = Instant.now().minus(2, ChronoUnit.HOURS);
            String token = firmar(jwtEncoder, "ventas-api-auth", "ventas-api", hace2h, hace2h.plus(30, ChronoUnit.MINUTES));
            mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensaje").value("El token ha expirado"));
        }
    }


    @Nested
    @DisplayName("7. Intento de asignarse ADMIN en el registro público -> 403")
    class RegistroPublico {
        @Test
        void noPermiteAutoasignarAdmin() throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombreCompleto\":\"Intruso\",\"email\":\"intruso@test.sv\","
                                    + "\"password\":\"Clave-Segura-1\",\"rol\":\"ADMIN\"}"))
                    .andExpect(status().isForbidden());
            assertThat(usuarios.existsByEmailIgnoreCase("intruso@test.sv")).isFalse();
        }

        @Test
        void registroNormalQuedaComoUsuarioYConHash() throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombreCompleto\":\"Nuevo\",\"email\":\"nuevo@test.sv\",\"password\":\"Clave-Segura-1\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.rol").value("USUARIO"))
                    .andExpect(jsonPath("$.password").doesNotExist());
            String hash = usuarios.findByEmailIgnoreCase("nuevo@test.sv").orElseThrow().getPasswordHash();
            assertThat(hash).startsWith("$2").isNotEqualTo("Clave-Segura-1");
        }

        @Test
        void soloAdminPuedePromover() throws Exception {
            Long id = usuarios.findByEmailIgnoreCase(USUARIO).orElseThrow().getId();
            String body = "{\"rol\":\"ADMIN\"}";
            mvc.perform(patch("/api/usuarios/" + id + "/rol").header(HttpHeaders.AUTHORIZATION, bearer(login(USUARIO)))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/usuarios/" + id + "/rol").header(HttpHeaders.AUTHORIZATION, bearer(login(ADMIN)))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"EMPLEADO\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rol").value("EMPLEADO"));
        }
    }

    private static String firmar(JwtEncoder encoder, String issuer, String audience, Instant iat, Instant exp) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(ADMIN)
                .issuedAt(iat)
                .expiresAt(exp)
                .claim("roles", List.of("ADMIN"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
