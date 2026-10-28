package sv.edu.dwf404.ventas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@Order(0)
public class MigracionRolUsuario implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigracionRolUsuario.class);

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public MigracionRolUsuario(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        if (!esMySqlOMariaDb()) {
            return;
        }
        try {
            jdbc.execute("ALTER TABLE usuarios MODIFY rol VARCHAR(20) NOT NULL");

            int roles = jdbc.update("UPDATE usuarios SET rol = 'USUARIO' WHERE rol = 'CONSULTA'");

            Integer nuevoExiste = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM usuarios WHERE email = 'usuario@ventas.com'", Integer.class);
            int demo = 0;
            if (nuevoExiste != null && nuevoExiste == 0) {
                demo = jdbc.update("UPDATE usuarios SET email = 'usuario@ventas.com', nombre_completo = 'Usuario Demo' "
                        + "WHERE email = 'consulta@ventas.sv'");
            }
            if (roles > 0 || demo > 0) {
                log.info("Migración aplicada: {} usuario(s) CONSULTA -> USUARIO; usuario demo renombrado: {}", roles, demo > 0);
            }
        } catch (Exception e) {
            log.warn("No se pudo aplicar la migración del rol USUARIO: {}", e.getMessage());
        }
    }

    private boolean esMySqlOMariaDb() {
        try (Connection c = dataSource.getConnection()) {
            String producto = c.getMetaData().getDatabaseProductName().toLowerCase();
            return producto.contains("mysql") || producto.contains("mariadb");
        } catch (Exception e) {
            return false;
        }
    }
}
