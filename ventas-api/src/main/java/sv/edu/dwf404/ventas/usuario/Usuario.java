package sv.edu.dwf404.ventas.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_completo", nullable = false, length = 120)
    private String nombreCompleto;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /** Hash BCrypt de la contraseña. Nunca se guarda ni se devuelve en texto plano. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Se guarda como VARCHAR (no como ENUM de MySQL) para poder agregar o renombrar roles sin romper la tabla. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Rol rol;

    protected Usuario() {
    }

    public Usuario(String nombreCompleto, String email, String passwordHash, Rol rol) {
        this.nombreCompleto = nombreCompleto;
        this.email = email;
        this.passwordHash = passwordHash;
        this.rol = rol;
    }

    public Long getId() { return id; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
}
