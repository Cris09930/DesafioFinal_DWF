package sv.edu.dwf404.ventas.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTOs de autenticación con validaciones Bean Validation.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * Registro público. El campo "rol" existe SOLO para poder detectar y rechazar explícitamente
     * el intento de auto-asignarse privilegios; el rol real siempre lo decide el servidor (USUARIO).
     */
    public record RegistroRequest(
            @NotBlank(message = "El nombre completo es obligatorio")
            @Size(min = 6, max = 50, message = "El nombre debe tener entre 6 y 50 caracteres")
            String nombreCompleto,

            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato válido")
            @Size(max = 50, message = "El correo no puede superar 50 caracteres")
            String email,

            // BCrypt solo procesa los primeros 72 bytes
            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = 8, max = 14, message = "La contraseña debe tener entre 8 y 14 caracteres")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "La contraseña debe contener al menos una letra y un número")
            String password,

            String rol) {
    }

    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato válido")
            String email,

            @NotBlank(message = "La contraseña es obligatoria")
            String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String rol) {
    }
}
