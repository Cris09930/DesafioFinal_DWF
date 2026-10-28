# Ventas API segura — Proyecto final DWF404 G01L

API REST para administrar **usuarios, productos y pedidos** de una empresa salvadoreña, protegida con
**Spring Security**, **JWT** propios y **OAuth2 con Keycloak** como proveedor de identidad externo.

![Arquitectura](docs/arquitectura.png)

---

## 1. Tecnologías y versiones

| Componente | Versión | Motivo |
|---|---|---|
| Java | 21 (LTS) | Requisito del proyecto; compatible con Spring Boot 4 |
| Spring Boot | 4.0.8 | Versión estable actual ofrecida por Spring Initializr |
| Spring Security | 7.0.x (gestionada por Boot) | Resource Server JWT, autorización por URL |
| spring-boot-starter-security-oauth2-resource-server | (Boot) | Valida JWT; trae Nimbus JOSE para firmar y verificar |
| Spring Data JPA / Hibernate | (Boot) | Persistencia |
| MariaDB / MySQL | la incluida en XAMPP | Base de datos relacional (driver `mariadb-java-client`) |
| H2 | (Boot, solo `test`) | BD en memoria para las pruebas automatizadas |
| Keycloak | 26.4 (Docker) | Proveedor de identidad OAuth2 / OpenID Connect |
| Maven | 3.9+ (el incluido en IntelliJ sirve) | Construcción |
| JUnit 5 + MockMvc | (Boot) | Pruebas automatizadas de seguridad |
| Postman | cualquiera reciente | Colección de pruebas manuales |

Todas las versiones de librerías las fija el `spring-boot-starter-parent`, por eso son compatibles entre sí.

---

## 2. Crear / abrir el proyecto en IntelliJ

**Opción A — usar este código directamente (recomendado):** `File → Open…` → seleccionar la carpeta `ventas-api`
(la que contiene `pom.xml`) → *Open as Project* → esperar a que Maven descargue dependencias.

**Opción B — generarlo desde Spring Initializr** (`File → New → Project → Spring Boot`, o https://start.spring.io):

- Project: **Maven** · Language: **Java** · Spring Boot: **4.0.x** · Java: **21** · Packaging: **Jar**
- Group: `sv.edu.dwf404` · Artifact: `ventas-api` · Package: `sv.edu.dwf404.ventas`
- Dependencias: **Spring Web**, **Spring Security**, **OAuth2 Resource Server**, **Spring Data JPA**,
  **Validation**, **MariaDB Driver**, **H2 Database**
- Luego copiar `src/`, `keycloak/`, `postman/`, `docs/`, `docker-compose.yml`, `.env.example`, `.gitignore`
  sobre el proyecto generado y reemplazar el `pom.xml` (H2 debe quedar con `<scope>test</scope>`).

---

## 3. Puesta en marcha

### 3.1 Requisitos
- JDK 21 configurado en IntelliJ (`File → Project Structure → SDK`).
- XAMPP con **MySQL** iniciado (Panel de control → MySQL → Start).
- Docker Desktop (solo para Keycloak).

### 3.1.1 Crear la base de datos
Abra http://localhost/phpmyadmin → **Nueva** → nombre `ventas_db`, cotejamiento `utf8mb4_general_ci` → **Crear**.
Las tablas las crea Hibernate al arrancar la API (`ddl-auto: update`).

### 3.2 Variables de entorno (secretos)
```bash
cp .env.example .env      # Windows: copy .env.example .env
```
Edite `.env` y reemplace **todos** los valores `cambiar-…`. Para generar valores aleatorios:
`openssl rand -base64 48`. `JWT_SECRET` debe tener **mínimo 32 caracteres** (si no, la API no arranca).

> `.env` está en `.gitignore`. Spring Boot lo lee automáticamente (`spring.config.import: optional:file:.env[.properties]`)
> y Docker Compose también, así que no hay que configurar variables a mano en IntelliJ.
> Si prefiere, puede definirlas en *Run → Edit Configurations → Environment variables*.

### 3.3 Levantar Keycloak
```bash
docker compose up -d
```
- Keycloak en http://localhost:8180 — importa automáticamente `keycloak/dwf404-realm.json` (realm `dwf404`).
  Consola de administración: usuario `KC_ADMIN_USER` / contraseña `KC_ADMIN_PASSWORD` del `.env`.

### 3.4 Ejecutar la API
En IntelliJ: abrir `VentasApiApplication` → ▶ *Run*. (o en terminal: `mvn spring-boot:run`)

La API queda en http://localhost:8080. Si `SEED_PASSWORD` está definido se crean:

| Usuario | Rol | Contraseña |
|---|---|---|
| admin@ventas.sv | ADMIN | `SEED_PASSWORD` |
| empleado@ventas.sv | EMPLEADO | `SEED_PASSWORD` |
| usuario@ventas.com | USUARIO | `SEED_PASSWORD` |

y 3 productos de ejemplo (ids 1, 2, 3).

### 3.5 Ejecutar las pruebas automatizadas
IntelliJ: clic derecho sobre `src/test/java` → *Run 'All Tests'*. Terminal: `mvn test`.
No necesitan Docker (usan H2 en memoria y un secreto de prueba en `application-test.yml`).

---

## 4. Endpoints y permisos

| Método | Ruta | Permiso requerido | ADMIN | EMPLEADO | USUARIO |
|---|---|---|:-:|:-:|:-:|
| POST | `/api/auth/register` | público | – | – | – |
| POST | `/api/auth/login` | público | – | – | – |
| GET | `/api/productos`, `/api/productos/{id}` | `producto:leer` | ✅ | ✅ | ✅ |
| POST / PUT / DELETE | `/api/productos[/{id}]` | `producto:escribir` | ✅ | ❌ 403 | ❌ 403 |
| GET | `/api/usuarios/me` | autenticado | ✅ | ✅ | ✅ |
| GET | `/api/usuarios`, `/api/usuarios/{id}` | `usuario:leer` | ✅ | ❌ 403 | ❌ 403 |
| PATCH | `/api/usuarios/{id}/rol` | `usuario:administrar` | ✅ | ❌ 403 | ❌ 403 |
| POST | `/api/pedidos` | `pedido:crear` | ❌ 403 | ✅ | ❌ 403 |
| GET | `/api/pedidos`, `/api/pedidos/{id}` | `pedido:leer-propios` / `pedido:leer-todos` | ✅ todos | ✅ propios | ❌ 403 |

Cualquier otra ruta: `denyAll()`.

**Permisos adicionales (justificación):**
- `usuario:administrar` (ADMIN): el registro público siempre crea `USUARIO`; hace falta una vía controlada para
  promover usuarios a EMPLEADO/ADMIN.
- `pedido:leer-todos` (ADMIN) y `pedido:leer-propios` (EMPLEADO): “consultar pedidos” sin que un empleado vea
  las ventas de otros (mínimo privilegio). El ADMIN supervisa todo pero **no** registra pedidos.

---

## 5. Decisiones principales de configuración

1. **Arquitectura stateless.** `SessionCreationPolicy.STATELESS`, sin `formLogin`, `httpBasic`, `logout` ni
   `requestCache`. Cada solicitud se autentica solo con el header `Authorization: Bearer`. CSRF se desactiva porque
   no hay cookies de sesión (la credencial no la envía el navegador automáticamente). Una prueba verifica que no se
   crea `HttpSession` ni se envía `Set-Cookie`.

2. **Dos emisores confiables, un solo Resource Server.** `JwtIssuerAuthenticationManagerResolver` lee el claim
   `iss` y delega:
   - `ventas-api-auth` → `localJwtDecoder`: **HS256**, clave desde `JWT_SECRET`.
   - `http://localhost:8180/realms/dwf404` → `keycloakJwtDecoder`: **RS256**, clave pública descargada del
     endpoint **JWKS** de Keycloak (la API nunca conoce la clave privada).
   - cualquier otro emisor → **401**.

3. **Validaciones de cada token:** firma, `exp`/`nbf` (60 s de tolerancia), **emisor (`iss`)** exacto y
   **audiencia (`aud`) = `ventas-api`**. Para Keycloak la audiencia se agrega con un *audience mapper* en los
   clientes `ventas-postman` y `ventas-servicio`. Un token de Keycloak emitido para otra aplicación es rechazado.

4. **No se confía en roles enviados por el cliente.** El cuerpo del registro no puede fijar el rol; los permisos
   no se leen del token sino que se **calculan en el servidor** desde el rol firmado (`enum Rol`), igual para tokens
   propios y de Keycloak (`RolesJwtAuthenticationConverter`). Alterar el rol en el token invalida la firma → 401.

5. **Contraseñas:** `BCryptPasswordEncoder` (hash adaptativo con sal). Nunca se devuelven en las respuestas.
   Login con mensaje genérico “Credenciales inválidas” para no revelar si el correo existe.

6. **Errores coherentes:** `AuthenticationEntryPoint` (401 + `WWW-Authenticate: Bearer …`) y
   `AccessDeniedHandler` (403) devuelven JSON; `GlobalExceptionHandler` cubre 400/404/409/500.

7. **Secretos:** ninguno en el repositorio. `JWT_SECRET` y `DB_PASSWORD` no tienen valor por defecto; el realm de
   Keycloak usa placeholders `${KC_SERVICE_CLIENT_SECRET}` y `${KC_DEMO_PASSWORD}` que Keycloak resuelve desde
   variables de entorno al importar.

---

## 6. Integración OAuth2 (Módulo 4)

### Roles en el flujo
| Rol OAuth2 | En este proyecto |
|---|---|
| **Resource Owner** | Usuario humano (`kc-admin`, `kc-empleado`, `kc-usuario`) |
| **Cliente** | `ventas-postman` (público, Authorization Code + PKCE) · `ventas-servicio` (confidencial, Client Credentials) |
| **Authorization Server / IdP** | Keycloak, realm `dwf404` |
| **Resource Server** | Esta API (`ventas-api`) |
| **Access token** | JWT firmado RS256 por Keycloak con `iss`, `aud=ventas-api`, `exp`, `realm_access.roles` |

### Relación entre las piezas
1. El **cliente** pide un token a **Keycloak** (no a la API).
2. Keycloak autentica (al usuario, o al cliente con su secreto) y emite un **access token** firmado con su clave privada.
3. El cliente llama a la **API** con `Authorization: Bearer <access_token>`.
4. La API ve `iss = …/realms/dwf404`, obtiene la **clave pública del JWKS** de Keycloak (una vez, en caché),
   verifica firma, vigencia, emisor y audiencia, y convierte `realm_access.roles` en permisos.
5. Si el rol permite la operación → 2xx; si no → 403; si el token no es válido → 401.

La API **no recibe contraseñas** en este flujo: esa es la diferencia con el login JWT propio.

### Flujo A — Authorization Code + PKCE (usuario humano, Postman)
1. En Postman abra la solicitud *6. OAuth2 con Keycloak → Usuario Keycloak (Authorization Code + PKCE)*.
2. Pestaña **Authorization** (ya configurada: Grant type *Authorization Code (With PKCE)*, Client ID `ventas-postman`,
   Callback `https://oauth.pstmn.io/v1/callback`) → **Get New Access Token**.
3. Inicie sesión en la página de Keycloak como `kc-empleado` (contraseña = `KC_DEMO_PASSWORD`) → **Use Token** → **Send**.
4. Pegue el token en https://jwt.io para mostrar `iss`, `aud`, `exp` y `realm_access.roles`.

### Flujo B — Client Credentials (sistema a sistema)
```bash
curl -s -X POST http://localhost:8180/realms/dwf404/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=ventas-servicio -d client_secret=$KC_SERVICE_CLIENT_SECRET
# -> { "access_token": "eyJ...", "expires_in": 300, ... }

curl -i http://localhost:8080/api/productos -H "Authorization: Bearer <access_token>"   # 200
curl -i -X DELETE http://localhost:8080/api/productos/1 -H "Authorization: Bearer <access_token>"  # 403
```
La cuenta de servicio de `ventas-servicio` tiene el rol de realm `EMPLEADO` (un POS que registra pedidos).

> **Importante:** obtenga los tokens usando `localhost:8180` (no `127.0.0.1`), porque el `iss` del token depende de la
> URL usada y la API exige exactamente `http://localhost:8180/realms/dwf404`.

---

## 7. Pruebas de seguridad (Módulo 5)

| # | Escenario | Esperado | Prueba automatizada | Postman |
|---|---|---|---|---|
| 1 | Sin token | 401 | `SeguridadJwtTest.SinToken` | carpeta 1 |
| 2 | Token válido + permisos | 200/201/204 | `SeguridadJwtTest.PermisosSuficientes` | carpeta 2 |
| 3 | Token válido sin permisos | 403 | `SeguridadJwtTest.SinPermisos` | carpeta 3 |
| 4 | Token alterado / inválido / otra clave / otro emisor / otra audiencia | 401 | `SeguridadJwtTest.TokenAlterado` | carpeta 4 |
| 5 | Token expirado | 401 “El token ha expirado” | `SeguridadJwtTest.TokenExpirado` | carpeta 5 |
| 6 | Token emitido por Keycloak | 200/201, 403 si no hay permiso | `KeycloakOAuth2Test` (decodificador sustituido) | carpeta 6 (Keycloak real) |
| 7 | Registro público con `"rol":"ADMIN"` | 403, usuario no creado | `SeguridadJwtTest.RegistroPublico` | carpeta 7 |

**Colección Postman:** importe `postman/DWF404-ventas-api.postman_collection.json`, en *Variables* de la colección
llene `seedPassword` y `kcServiceSecret` (solo localmente) y ejecute en orden con *Run collection*.
Para el escenario 5 manual: arranque la API con `JWT_EXPIRATION=1m`, haga login, copie el token a la variable
`tokenExpirado` y espere ~2 minutos (o use un token de Keycloak de más de 5 minutos).

Evidencias: ver `docs/EVIDENCIAS.md`.

---

## Frontend web (VentasSV)

La API incluye una interfaz web hecha con **Bootstrap 5**, **Bootstrap Icons** y **SweetAlert2**, servida por el
mismo Spring Boot desde `src/main/resources/static`. No requiere instalación adicional:

1. Ejecute la API (`VentasApiApplication`).
2. Abra **http://localhost:8080** en el navegador.

| Pantalla | Qué hace | Quién la ve |
|---|---|---|
| Acceso | Login, registro con medidor de contraseña, botón "Iniciar sesión con Keycloak" (Authorization Code + PKCE) | Todos |
| Inicio | Resumen con contadores animados, permisos del usuario, productos con poco stock | Autenticados |
| Productos | Catálogo con búsqueda y orden; crear, editar y eliminar | Ver: todos · Editar: ADMIN |
| Pedidos | Carrito para registrar pedidos y listado con detalle | EMPLEADO registra · ADMIN ve todos |
| Usuarios | Lista de usuarios y cambio de rol | ADMIN |
| Mi sesión | Datos del usuario y contenido (claims) del token | Autenticados |
| Laboratorio de seguridad | Ejecuta los escenarios del Módulo 5 contra la API y muestra esperado vs. obtenido | Autenticados |

**Validaciones:** se validan en dos capas. En el navegador (JavaScript) para dar respuesta inmediata, y en el
servidor con **Bean Validation de Java** (`@NotBlank`, `@Email`, `@Size`, `@Pattern`, `@DecimalMin`, `@Digits`…),
que es la validación que realmente protege la API. Los mensajes de Java están en español y el frontend los muestra
debajo de cada campo.

**Seguridad del frontend:** el token se guarda en `sessionStorage` (se borra al cerrar la pestaña), nunca se guarda la
contraseña, todo dato mostrado se escapa para evitar XSS y los botones se ocultan según los permisos que devuelve
`/api/usuarios/me`. Ocultar botones es solo comodidad: la API vuelve a validar el permiso en cada solicitud.

**Keycloak desde el frontend:** usa el cliente público `ventas-web` del realm (redirect `http://localhost:8080/*`).
Si Keycloak ya había importado el realm antes de esta versión, cree ese cliente en la consola o borre la carpeta
`data` de Keycloak para que vuelva a importar `dwf404-realm.json`.

---

## 8. Estructura
```
ventas-api/
├── pom.xml
├── docker-compose.yml          Keycloak
├── .env.example                plantilla de secretos (copiar a .env)
├── keycloak/dwf404-realm.json  realm, roles, clientes, usuarios (importado al arrancar)
├── postman/                    colección de pruebas
├── docs/                       diagrama de arquitectura y evidencias
└── src/
    ├── main/java/sv/edu/dwf404/ventas/
    │   ├── auth/        registro, login, emisión de JWT
    │   ├── seguridad/   SecurityConfig, JwtConfig, conversor de roles, respuestas 401/403
    │   ├── usuario/     Usuario, Rol (roles→permisos), recurso usuarios
    │   ├── producto/    CRUD de productos
    │   ├── pedido/      registro y consulta de pedidos
    │   ├── comun/       manejo global de errores
    │   └── config/      datos de demostración
    └── test/java/...    SeguridadJwtTest, KeycloakOAuth2Test
```
