package sv.edu.dwf404.ventas.usuario;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sv.edu.dwf404.ventas.comun.RecursoNoEncontradoException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recurso Usuarios. Las reglas de acceso están en SecurityConfig:
 * - GET /api/usuarios/me        -> cualquier usuario autenticado (solo sus propios datos)
 * - GET /api/usuarios[/{id}]    -> permiso usuario:leer (ADMIN)
 * - PATCH /api/usuarios/{id}/rol -> permiso usuario:administrar (ADMIN)
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioRepository repo;

    public UsuarioController(UsuarioRepository repo) {
        this.repo = repo;
    }

    /** Información de identidad que la API extrajo del token validado (útil para la demo). */
    @GetMapping("/me")
    public Map<String, Object> yo(Authentication auth) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("usuario", auth.getName());
        datos.put("authorities", auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList());
        repo.findByEmailIgnoreCase(auth.getName())
                .ifPresent(u -> datos.put("perfil", UsuarioDtos.UsuarioResponse.de(u)));
        return datos;
    }

    @GetMapping
    public List<UsuarioDtos.UsuarioResponse> listar() {
        return repo.findAll().stream().map(UsuarioDtos.UsuarioResponse::de).toList();
    }

    @GetMapping("/{id}")
    public UsuarioDtos.UsuarioResponse obtener(@PathVariable Long id) {
        return repo.findById(id).map(UsuarioDtos.UsuarioResponse::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + id + " no existe"));
    }

    /** Permiso adicional justificado: la única vía para promover un usuario es un ADMIN autenticado. */
    @PatchMapping("/{id}/rol")
    @Transactional
    public UsuarioDtos.UsuarioResponse cambiarRol(@PathVariable Long id,
                                                   @Valid @RequestBody UsuarioDtos.CambioRolRequest req) {
        Usuario u = repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + id + " no existe"));
        u.setRol(req.rol());
        return UsuarioDtos.UsuarioResponse.de(u);
    }
}
