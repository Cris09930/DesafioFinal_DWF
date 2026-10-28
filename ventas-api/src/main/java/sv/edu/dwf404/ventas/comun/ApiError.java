package sv.edu.dwf404.ventas.comun;

import java.time.Instant;
import java.util.List;

/** Formato uniforme de error para todas las respuestas 4xx/5xx de la API. */
public record ApiError(Instant timestamp, int status, String error, String mensaje, String ruta, List<String> detalles) {

    public static ApiError de(int status, String error, String mensaje, String ruta) {
        return new ApiError(Instant.now(), status, error, mensaje, ruta, List.of());
    }

    public static ApiError de(int status, String error, String mensaje, String ruta, List<String> detalles) {
        return new ApiError(Instant.now(), status, error, mensaje, ruta, detalles);
    }
}
