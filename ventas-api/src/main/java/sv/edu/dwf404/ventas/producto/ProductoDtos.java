package sv.edu.dwf404.ventas.producto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class ProductoDtos {

    private ProductoDtos() {
    }

    public record ProductoRequest(
            @NotBlank(message = "El nombre del producto es obligatorio")
            @Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres")
            String nombre,

            @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
            String descripcion,

            @NotNull(message = "El precio es obligatorio")
            @DecimalMin(value = "0.01", message = "El precio debe ser mayor que $0.00")
            @DecimalMax(value = "99999.99", message = "El precio no puede superar $99,999.99")
            @Digits(integer = 5, fraction = 2, message = "El precio admite máximo 2 decimales")
            BigDecimal precio,

            @NotNull(message = "El stock es obligatorio")
            @PositiveOrZero(message = "El stock no puede ser negativo")
            @Max(value = 100000, message = "El stock no puede superar 100,000 unidades")
            Integer stock) {
    }

    public record ProductoResponse(Long id, String nombre, String descripcion, BigDecimal precio, Integer stock) {
        public static ProductoResponse de(Producto p) {
            return new ProductoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(), p.getStock());
        }
    }
}
