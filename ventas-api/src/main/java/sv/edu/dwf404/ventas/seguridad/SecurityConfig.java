package sv.edu.dwf404.ventas.seguridad;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import sv.edu.dwf404.ventas.usuario.Rol.Permiso;
import sv.edu.dwf404.ventas.usuario.UsuarioRepository;

import java.util.Map;

/**
 * Configuración central de Spring Security.
 * <p>
 * La API es un Resource Server OAuth2 que acepta tokens Bearer de DOS emisores confiables:
 * <ol>
 *   <li>La propia API (POST /api/auth/login) — JWT HS256.</li>
 *   <li>Keycloak (proveedor de identidad OAuth2 externo) — JWT RS256.</li>
 * </ol>
 * {@link JwtIssuerAuthenticationManagerResolver} lee el claim "iss" y delega en el validador correspondiente;
 * un token de cualquier otro emisor se rechaza con 401.
 */
@Configuration
//Activa Srping Security
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SeguridadProperties props,
                                            @Qualifier("localJwtDecoder") JwtDecoder localJwtDecoder,
                                            @Qualifier("keycloakJwtDecoder") JwtDecoder keycloakJwtDecoder) throws Exception {

        AuthenticationEntryPoint entryPoint = RespuestasSeguridad.entryPoint401();
        AccessDeniedHandler deniedHandler = RespuestasSeguridad.accesoDenegado403();

        // Un AuthenticationManager por emisor confiable
        Map<String, AuthenticationManager> porEmisor = Map.of(
                props.jwt().issuer(), manager(localJwtDecoder, RolesJwtAuthenticationConverter.paraTokensLocales()),
                props.keycloak().issuerUri(), manager(keycloakJwtDecoder, RolesJwtAuthenticationConverter.paraKeycloak()));
        JwtIssuerAuthenticationManagerResolver resolver = new JwtIssuerAuthenticationManagerResolver(porEmisor::get);

        http
                // API REST sin estado: no se crea ni se usa HttpSession para la autenticación
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF no aplica: no hay cookies de sesión, la credencial viaja en el header Authorization
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Frontend estático (HTML/CSS/JS). No contiene datos: los datos se piden a /api con token.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.svg",
                                "/css/**", "/js/**", "/img/**").permitAll()

                        // Productos
                        .requestMatchers(HttpMethod.GET, "/api/productos", "/api/productos/**").hasAuthority(Permiso.PRODUCTO_LEER)
                        .requestMatchers(HttpMethod.POST, "/api/productos").hasAuthority(Permiso.PRODUCTO_ESCRIBIR)
                        .requestMatchers(HttpMethod.PUT, "/api/productos/**").hasAuthority(Permiso.PRODUCTO_ESCRIBIR)
                        .requestMatchers(HttpMethod.DELETE, "/api/productos/**").hasAuthority(Permiso.PRODUCTO_ESCRIBIR)

                        // Usuarios
                        .requestMatchers(HttpMethod.GET, "/api/usuarios/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/usuarios", "/api/usuarios/**").hasAuthority(Permiso.USUARIO_LEER)
                        .requestMatchers(HttpMethod.PATCH, "/api/usuarios/*/rol").hasAuthority(Permiso.USUARIO_ADMINISTRAR)

                        // Pedidos
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAuthority(Permiso.PEDIDO_CREAR)
                        .requestMatchers(HttpMethod.GET, "/api/pedidos", "/api/pedidos/**")
                        .hasAnyAuthority(Permiso.PEDIDO_LEER_PROPIOS, Permiso.PEDIDO_LEER_TODOS)

                        //Todo lo demas queda cerrado (principio de minimo privilegio)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(resolver)
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler));

        return http.build();
    }

    private static AuthenticationManager manager(JwtDecoder decoder, RolesJwtAuthenticationConverter converter) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return new ProviderManager(provider);
    }

    /** Hash las contraseñas nunca se guardan en texto plano. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UsuarioRepository usuarios) {
        return email -> usuarios.findByEmailIgnoreCase(email)
                .map(u -> User.withUsername(u.getEmail())
                        .password(u.getPasswordHash())
                        .roles(u.getRol().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    /** Usado solo por /api/auth/login para verificar usuario y contraseña contra la base de datos. */
    @Bean
    AuthenticationManager loginAuthenticationManager(UserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
