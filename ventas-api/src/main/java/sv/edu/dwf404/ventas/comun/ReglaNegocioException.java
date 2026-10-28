package sv.edu.dwf404.ventas.comun;

/** Error de validación de negocio (400), p. ej. stock insuficiente o intento de auto-asignarse un rol. */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
