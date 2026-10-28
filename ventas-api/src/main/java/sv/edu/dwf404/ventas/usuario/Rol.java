package sv.edu.dwf404.ventas.usuario;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Roles del sistema y los permisos que concede cada uno.
 * <p>
 * Es la ÚNICA fuente de verdad de la autorización: tanto para los tokens emitidos por la API
 * como para los emitidos por Keycloak, los permisos se calculan en el servidor a partir del rol,
 * nunca se aceptan permisos enviados por el cliente.
 */
public enum Rol {

    /** Administra productos y consulta usuarios. */
    ADMIN(Set.of(
            Permiso.PRODUCTO_LEER, Permiso.PRODUCTO_ESCRIBIR,
            Permiso.USUARIO_LEER, Permiso.USUARIO_ADMINISTRAR,
            Permiso.PEDIDO_LEER_TODOS)),

    /** Consulta productos y registra pedidos. */
    EMPLEADO(Set.of(
            Permiso.PRODUCTO_LEER,
            Permiso.PEDIDO_CREAR, Permiso.PEDIDO_LEER_PROPIOS)),

    /** Únicamente consulta los productos disponibles. */
    USUARIO(Set.of(Permiso.PRODUCTO_LEER));

    private final Set<String> permisos;

    Rol(Set<String> permisos) {
        this.permisos = permisos;
    }

    public Set<String> getPermisos() {
        return permisos;
    }

    /** Convierte un texto (p. ej. un rol de Keycloak) en Rol; ignora roles desconocidos. */
    public static Optional<Rol> desde(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(r -> r.name().equalsIgnoreCase(valor.trim()))
                .findFirst();
    }

    /** Nombres de permiso (authorities) usados en las reglas de acceso. */
    public static final class Permiso {
        public static final String PRODUCTO_LEER = "producto:leer";
        public static final String PRODUCTO_ESCRIBIR = "producto:escribir";
        public static final String USUARIO_LEER = "usuario:leer";
        public static final String USUARIO_ADMINISTRAR = "usuario:administrar";
        public static final String PEDIDO_CREAR = "pedido:crear";
        public static final String PEDIDO_LEER_PROPIOS = "pedido:leer-propios";
        public static final String PEDIDO_LEER_TODOS = "pedido:leer-todos";

        private Permiso() {
        }
    }
}
