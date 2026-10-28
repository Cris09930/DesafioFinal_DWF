package sv.edu.dwf404.ventas.auth;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sv.edu.dwf404.ventas.comun.ConflictoException;
import sv.edu.dwf404.ventas.usuario.Rol;
import sv.edu.dwf404.ventas.usuario.Usuario;
import sv.edu.dwf404.ventas.usuario.UsuarioDtos;
import sv.edu.dwf404.ventas.usuario.UsuarioRepository;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager loginAuthenticationManager;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
                          @Qualifier("loginAuthenticationManager") AuthenticationManager loginAuthenticationManager,
                          JwtService jwtService) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.loginAuthenticationManager = loginAuthenticationManager;
        this.jwtService = jwtService;
    }

    /** Registro público: siempre crea usuarios con rol USUARIO. */
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<UsuarioDtos.UsuarioResponse> registrar(@Valid @RequestBody AuthDtos.RegistroRequest req) {
        if (req.rol() != null && !Rol.USUARIO.name().equalsIgnoreCase(req.rol().trim())) {
            throw new PrivilegioNoPermitidoException(
                    "El registro público no permite asignar roles; solo un ADMIN puede cambiar el rol de un usuario");
        }
        if (usuarios.existsByEmailIgnoreCase(req.email())) {
            throw new ConflictoException("El correo ya está registrado");
        }
        Usuario u = usuarios.save(new Usuario(
                req.nombreCompleto().trim(),
                req.email().trim().toLowerCase(),
                passwordEncoder.encode(req.password()),
                Rol.USUARIO));
        return ResponseEntity.created(URI.create("/api/usuarios/" + u.getId()))
                .body(UsuarioDtos.UsuarioResponse.de(u));
    }

    /** Verifica usuario + contraseña y emite un JWT firmado. No se crea sesión en el servidor. */
    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        loginAuthenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.email().trim(), req.password()));
        Usuario u = usuarios.findByEmailIgnoreCase(req.email().trim()).orElseThrow();
        return new AuthDtos.TokenResponse(jwtService.generar(u), "Bearer", jwtService.segundosDeVigencia(), u.getRol().name());
    }
}
