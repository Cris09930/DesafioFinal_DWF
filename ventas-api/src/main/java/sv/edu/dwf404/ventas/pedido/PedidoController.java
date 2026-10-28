package sv.edu.dwf404.ventas.pedido;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Recurso Pedidos. Reglas en SecurityConfig:
 * - POST -> pedido:crear (EMPLEADO)
 * - GET  -> pedido:leer-propios (EMPLEADO) o pedido:leer-todos (ADMIN)
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PedidoDtos.PedidoResponse> registrar(@Valid @RequestBody PedidoDtos.PedidoRequest req,
                                                               Authentication auth) {
        PedidoDtos.PedidoResponse creado = service.registrar(req, auth);
        return ResponseEntity.created(URI.create("/api/pedidos/" + creado.id())).body(creado);
    }

    @GetMapping
    public List<PedidoDtos.PedidoResponse> listar(Authentication auth) {
        return service.listar(auth);
    }

    @GetMapping("/{id}")
    public PedidoDtos.PedidoResponse obtener(@PathVariable Long id, Authentication auth) {
        return service.obtener(id, auth);
    }
}
