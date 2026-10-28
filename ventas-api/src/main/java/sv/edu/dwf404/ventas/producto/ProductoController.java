package sv.edu.dwf404.ventas.producto;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sv.edu.dwf404.ventas.comun.RecursoNoEncontradoException;

import java.net.URI;
import java.util.List;

/**
 * Recurso Productos. Reglas en SecurityConfig:
 * - GET              -> producto:leer      (ADMIN, EMPLEADO, USUARIO)
 * - POST/PUT/DELETE  -> producto:escribir  (ADMIN)
 */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoRepository repo;

    public ProductoController(ProductoRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<ProductoDtos.ProductoResponse> listar() {
        return repo.findAll().stream().map(ProductoDtos.ProductoResponse::de).toList();
    }

    @GetMapping("/{id}")
    public ProductoDtos.ProductoResponse obtener(@PathVariable Long id) {
        return ProductoDtos.ProductoResponse.de(buscar(id));
    }

    @PostMapping
    public ResponseEntity<ProductoDtos.ProductoResponse> crear(@Valid @RequestBody ProductoDtos.ProductoRequest req) {
        Producto p = repo.save(new Producto(req.nombre(), req.descripcion(), req.precio(), req.stock()));
        return ResponseEntity.created(URI.create("/api/productos/" + p.getId()))
                .body(ProductoDtos.ProductoResponse.de(p));
    }

    @PutMapping("/{id}")
    @Transactional
    public ProductoDtos.ProductoResponse modificar(@PathVariable Long id, @Valid @RequestBody ProductoDtos.ProductoRequest req) {
        Producto p = buscar(id);
        p.actualizar(req.nombre(), req.descripcion(), req.precio(), req.stock());
        return ProductoDtos.ProductoResponse.de(p);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        repo.delete(buscar(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private Producto buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no existe"));
    }
}
