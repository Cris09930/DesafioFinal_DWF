package sv.edu.dwf404.ventas.pedido;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PedidoDtos {

    private PedidoDtos() {
    }

    public record ItemRequest(
            @NotNull(message = "Debe seleccionar un producto") Long productoId,
            @NotNull(message = "La cantidad es obligatoria")
            @Positive(message = "La cantidad debe ser mayor que cero")
            @Max(value = 1000, message = "La cantidad máxima por producto es 1,000")
            Integer cantidad) {
    }

    public record PedidoRequest(
            @Size(max = 120, message = "El nombre del cliente no puede superar 120 caracteres") String cliente,
            @NotEmpty(message = "El pedido debe tener al menos un producto")
            @Size(max = 50, message = "Un pedido admite máximo 50 líneas")
            List<@Valid ItemRequest> items) {
    }

    public record ItemResponse(Long productoId, String producto, Integer cantidad,
                               BigDecimal precioUnitario, BigDecimal subtotal) {
        static ItemResponse de(DetallePedido d) {
            return new ItemResponse(d.getProducto().getId(), d.getProducto().getNombre(), d.getCantidad(),
                    d.getPrecioUnitario(), d.getSubtotal());
        }
    }

    public record PedidoResponse(Long id, Instant fecha, String creadoPor, String cliente,
                                 BigDecimal total, List<ItemResponse> items) {
        static PedidoResponse de(Pedido p) {
            return new PedidoResponse(p.getId(), p.getFecha(), p.getCreadoPor(), p.getCliente(), p.getTotal(),
                    p.getDetalles().stream().map(ItemResponse::de).toList());
        }
    }
}
