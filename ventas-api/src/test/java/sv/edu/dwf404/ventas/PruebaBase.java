package sv.edu.dwf404.ventas;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import sv.edu.dwf404.ventas.pedido.PedidoRepository;
import sv.edu.dwf404.ventas.producto.Producto;
import sv.edu.dwf404.ventas.producto.ProductoRepository;
import sv.edu.dwf404.ventas.usuario.Rol;
import sv.edu.dwf404.ventas.usuario.Usuario;
import sv.edu.dwf404.ventas.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Utilidades comunes: datos limpios por prueba y login real contra /api/auth/login. */
public abstract class PruebaBase {

    protected static final String CLAVE = "Clave-Prueba-123";
    protected static final String ADMIN = "admin@test.sv";
    protected static final String EMPLEADO = "empleado@test.sv";
    protected static final String USUARIO = "usuario@test.sv";

    @Autowired protected MockMvc mvc;
    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected ProductoRepository productos;
    @Autowired protected PedidoRepository pedidos;
    @Autowired protected PasswordEncoder encoder;

    protected Long productoId;

    @BeforeEach
    void prepararDatos() {
        pedidos.deleteAll();
        productos.deleteAll();
        usuarios.deleteAll();
        usuarios.save(new Usuario("Admin", ADMIN, encoder.encode(CLAVE), Rol.ADMIN));
        usuarios.save(new Usuario("Empleado", EMPLEADO, encoder.encode(CLAVE), Rol.EMPLEADO));
        usuarios.save(new Usuario("Usuario", USUARIO, encoder.encode(CLAVE), Rol.USUARIO));
        productoId = productos.save(new Producto("Café", "1 lb", new BigDecimal("8.50"), 50)).getId();
    }

    protected String login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Matcher m = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(r.getResponse().getContentAsString());
        if (!m.find()) {
            throw new IllegalStateException("La respuesta de login no contiene accessToken");
        }
        return m.group(1);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected String pedidoJson() {
        return "{\"cliente\":\"Cliente Demo\",\"items\":[{\"productoId\":" + productoId + ",\"cantidad\":2}]}";
    }
}
