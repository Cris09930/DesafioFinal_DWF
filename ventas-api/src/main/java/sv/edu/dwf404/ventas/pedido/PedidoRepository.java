package sv.edu.dwf404.ventas.pedido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByCreadoPorOrderByFechaDesc(String creadoPor);

    List<Pedido> findAllByOrderByFechaDesc();
}
