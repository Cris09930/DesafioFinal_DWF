package sv.edu.dwf404.ventas.auth;

/** Se lanza cuando alguien intenta asignarse un rol desde el registro público (HTTP 403). */
public class PrivilegioNoPermitidoException extends RuntimeException {
    public PrivilegioNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}
