package sv.edu.dwf404.ventas.pedido;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sv.edu.dwf404.ventas.comun.RecursoNoEncontradoException;
import sv.edu.dwf404.ventas.comun.ReglaNegocioException;
import sv.edu.dwf404.ventas.producto.Producto;
import sv.edu.dwf404.ventas.producto.ProductoRepository;
import sv.edu.dwf404.ventas.usuario.Rol;

import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository pedidos;
    private final ProductoRepository productos;

    public PedidoService(PedidoRepository pedidos, ProductoRepository productos) {
        this.pedidos = pedidos;
        this.productos = productos;
    }

    @Transactional
    public PedidoDtos.PedidoResponse registrar(PedidoDtos.PedidoRequest req, Authentication auth) {
        Pedido pedido = new Pedido(auth.getName(), req.cliente());
        for (PedidoDtos.ItemRequest item : req.items()) {
            Producto producto = productos.findById(item.productoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + item.productoId() + " no existe"));
            if (producto.getStock() < item.cantidad()) {
                throw new ReglaNegocioException("Stock insuficiente para '" + producto.getNombre()
                        + "' (disponible: " + producto.getStock() + ")");
            }
            producto.descontarStock(item.cantidad());
            pedido.agregarDetalle(new DetallePedido(producto, item.cantidad()));
        }
        return PedidoDtos.PedidoResponse.de(pedidos.save(pedido));
    }

    /** ADMIN ve todos los pedidos; EMPLEADO solo los que él registró. */
    @Transactional(readOnly = true)
    public List<PedidoDtos.PedidoResponse> listar(Authentication auth) {
        List<Pedido> lista = puedeVerTodos(auth)
                ? pedidos.findAllByOrderByFechaDesc()
                : pedidos.findByCreadoPorOrderByFechaDesc(auth.getName());
        return lista.stream().map(PedidoDtos.PedidoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public PedidoDtos.PedidoResponse obtener(Long id, Authentication auth) {
        Pedido p = pedidos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido " + id + " no existe"));
        if (!puedeVerTodos(auth) && !p.getCreadoPor().equals(auth.getName())) {
            throw new AccessDeniedException("El pedido pertenece a otro usuario");
        }
        return PedidoDtos.PedidoResponse.de(p);
    }

    private boolean puedeVerTodos(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> Rol.Permiso.PEDIDO_LEER_TODOS.equals(a.getAuthority()));
    }
}
