package sv.edu.dwf404.ventas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import sv.edu.dwf404.ventas.producto.Producto;
import sv.edu.dwf404.ventas.producto.ProductoRepository;
import sv.edu.dwf404.ventas.usuario.Rol;
import sv.edu.dwf404.ventas.usuario.Usuario;
import sv.edu.dwf404.ventas.usuario.UsuarioRepository;

import java.math.BigDecimal;

/**
 * Datos de demostración.
 * Los usuarios de prueba SOLO se crean si se define la variable SEED_PASSWORD.
 * Es la única forma de que exista un ADMIN, ya que el registro público nunca lo permite.
 */
@Component
@Order(1)
public class DatosIniciales implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    private final UsuarioRepository usuarios;
    private final ProductoRepository productos;
    private final PasswordEncoder encoder;
    private final String seedPassword;

    public DatosIniciales(UsuarioRepository usuarios, ProductoRepository productos, PasswordEncoder encoder,
                          @Value("${app.seed.password:}") String seedPassword) {
        this.usuarios = usuarios;
        this.productos = productos;
        this.encoder = encoder;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(String... args) {
        if (!seedPassword.isBlank()) {
            crearSiNoExiste("Administrador Demo", "admin@ventas.sv", Rol.ADMIN);
            crearSiNoExiste("Empleado Demo", "empleado@ventas.sv", Rol.EMPLEADO);
            crearSiNoExiste("Usuario Demo", "usuario@ventas.com", Rol.USUARIO);
        } else {
            log.info("SEED_PASSWORD no definido: no se crean usuarios de demostración");
        }
        if (productos.count() == 0) {
            productos.save(new Producto("Café de altura 1 lb", "Café salvadoreño tostado", new BigDecimal("8.50"), 100));
            productos.save(new Producto("Pupusero eléctrico", "Plancha antiadherente", new BigDecimal("45.99"), 20));
            productos.save(new Producto("Hamaca artesanal", "Hecha a mano en Nahuizalco", new BigDecimal("32.00"), 15));
        }
    }

    private void crearSiNoExiste(String nombre, String email, Rol rol) {
        if (!usuarios.existsByEmailIgnoreCase(email)) {
            usuarios.save(new Usuario(nombre, email, encoder.encode(seedPassword), rol));
            log.info("Usuario de demostración creado: {} ({})", email, rol);
        }
    }
}
