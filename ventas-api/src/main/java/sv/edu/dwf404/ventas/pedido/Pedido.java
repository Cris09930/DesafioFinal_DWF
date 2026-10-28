package sv.edu.dwf404.ventas.pedido;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant fecha;

    /** Identidad tomada del token validado (nunca del cuerpo de la solicitud). */
    @Column(name = "creado_por", nullable = false, length = 150)
    private String creadoPor;

    @Column(length = 120)
    private String cliente;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePedido> detalles = new ArrayList<>();

    protected Pedido() {
    }

    public Pedido(String creadoPor, String cliente) {
        this.creadoPor = creadoPor;
        this.cliente = cliente;
        this.fecha = Instant.now();
    }

    public void agregarDetalle(DetallePedido d) {
        d.setPedido(this);
        detalles.add(d);
        total = total.add(d.getSubtotal());
    }

    public Long getId() { return id; }
    public Instant getFecha() { return fecha; }
    public String getCreadoPor() { return creadoPor; }
    public String getCliente() { return cliente; }
    public BigDecimal getTotal() { return total; }
    public List<DetallePedido> getDetalles() { return detalles; }
}
