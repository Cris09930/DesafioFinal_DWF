package sv.edu.dwf404.ventas.comun;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sv.edu.dwf404.ventas.auth.PrivilegioNoPermitidoException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos", req, detalles);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> jsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido", req, List.of());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ApiError> negocio(ReglaNegocioException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ApiError> conflicto(ConflictoException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), req, List.of());
    }

    /** Credenciales incorrectas en /api/auth/login: mensaje genérico para no revelar si el correo existe. */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ApiError> credenciales(AuthenticationException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, "Credenciales inválidas", req, List.of());
    }

    @ExceptionHandler(PrivilegioNoPermitidoException.class)
    public ResponseEntity<ApiError> privilegio(PrivilegioNoPermitidoException ex, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, ex.getMessage(), req, List.of());
    }

    /** Denegaciones lanzadas desde la capa de servicio / @PreAuthorize: deben seguir siendo 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denegado(AccessDeniedException ex, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta operación", req, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", req, List.of());
    }

    private ResponseEntity<ApiError> responder(HttpStatus status, String mensaje, HttpServletRequest req, List<String> detalles) {
        return ResponseEntity.status(status)
                .body(ApiError.de(status.value(), status.getReasonPhrase(), mensaje, req.getRequestURI(), detalles));
    }
}
