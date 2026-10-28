package sv.edu.dwf404.ventas.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Respuestas JSON coherentes para los errores de seguridad que ocurren en la cadena de filtros
 * (antes de llegar a los controladores):
 * <ul>
 *   <li>401: no hay token, o el token es inválido, alterado, expirado o de un emisor no confiable.</li>
 *   <li>403: el token es válido pero el usuario no tiene el permiso requerido.</li>
 * </ul>
 */
public final class RespuestasSeguridad {

    private RespuestasSeguridad() {
    }

    public static AuthenticationEntryPoint entryPoint401() {
        return (HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) -> {
            String mensaje;
            String wwwAuthenticate;
            if (ex instanceof OAuth2AuthenticationException) {
                String detalle = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                mensaje = detalle.contains("expired")
                        ? "El token ha expirado"
                        : "Token inválido, alterado o emitido por un proveedor no confiable";
                wwwAuthenticate = "Bearer error=\"invalid_token\", error_description=\"" + mensaje + "\"";
            } else {
                mensaje = "Se requiere autenticación: envíe un token Bearer válido";
                wwwAuthenticate = "Bearer";
            }
            res.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);
            escribir(res, req, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", mensaje);
        };
    }

    public static AccessDeniedHandler accesoDenegado403() {
        return (req, res, ex) -> {
            res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
            escribir(res, req, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                    "No tiene permisos para realizar esta operación");
        };
    }

    private static void escribir(HttpServletResponse res, HttpServletRequest req, int status, String error, String mensaje)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String json = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status
                + ",\"error\":\"" + error + "\",\"mensaje\":\"" + escapar(mensaje)
                + "\",\"ruta\":\"" + escapar(req.getRequestURI()) + "\",\"detalles\":[]}";
        res.getWriter().write(json);
    }

    private static String escapar(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
