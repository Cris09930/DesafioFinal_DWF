package sv.edu.dwf404.ventas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El frontend estático es público, pero los datos siguen protegidos; las validaciones Java devuelven mensajes claros. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FrontendYValidacionTest extends PruebaBase {

    @Test
    void frontendEsPublico() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @Test
    void losDatosSiguenProtegidos() throws Exception {
        mvc.perform(get("/api/productos")).andExpect(status().isUnauthorized());
    }

    @Test
    void validacionDeProductoConMensajesEnEspanol() throws Exception {
        mvc.perform(post("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer(login(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"precio\":0,\"stock\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("precio: El precio debe ser mayor que $0.00")))
                .andExpect(jsonPath("$.detalles", hasItem("stock: El stock no puede ser negativo")));
    }

    @Test
    void validacionDeRegistro() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreCompleto\":\"Ana\",\"email\":\"no-es-correo\",\"password\":\"solotexto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("email: El correo no tiene un formato válido")))
                .andExpect(jsonPath("$.detalles", hasItem("password: La contraseña debe contener al menos una letra y un número")));
    }

    @Test
    void pedidoSinItems() throws Exception {
        mvc.perform(post("/api/pedidos").header(HttpHeaders.AUTHORIZATION, bearer(login(EMPLEADO)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"X\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("items: El pedido debe tener al menos un producto")));
    }
}
