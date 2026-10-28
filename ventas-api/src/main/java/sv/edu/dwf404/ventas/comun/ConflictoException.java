package sv.edu.dwf404.ventas.comun;

/** Conflicto con el estado actual (409), p. ej. correo ya registrado. */
public class ConflictoException extends RuntimeException {
    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
