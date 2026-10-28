package sv.edu.dwf404.ventas.usuario;

import jakarta.validation.constraints.NotNull;

/** DTOs del módulo de usuarios. La contraseña nunca forma parte de una respuesta. */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    public record UsuarioResponse(Long id, String nombreCompleto, String email, Rol rol) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNombreCompleto(), u.getEmail(), u.getRol());
        }
    }

    public record CambioRolRequest(@NotNull(message = "Debe indicar el nuevo rol") Rol rol) {
    }
}
