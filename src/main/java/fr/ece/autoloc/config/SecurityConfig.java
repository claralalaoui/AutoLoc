package fr.ece.autoloc.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails user = User.withUsername("student")
                .password(encoder.encode("student123"))
                .roles("USER").build();
        UserDetails admin = User.withUsername("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN").build();
        return new InMemoryUserDetailsManager(user, admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {
        http
                // The API is authenticated with the Authorization header on every request (no session cookie),
                // so CSRF protection is not needed on /api/**.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        // Static files of the web interface (the data itself stays protected under /api/**)
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Customer space: the logged-in user's own profile and reservations
                        .requestMatchers("/api/me", "/api/me/**").hasAnyRole("USER", "ADMIN")
                        // Vehicle catalogue and agencies can be read by customers
                        .requestMatchers(HttpMethod.GET, "/api/vehicules/**", "/api/agences/**").hasAnyRole("USER", "ADMIN")
                        // Personal and financial data of all customers: agency staff only
                        .requestMatchers("/api/dashboard", "/api/clients/**", "/api/reservations/**", "/api/contrats/**")
                        .hasRole("ADMIN")
                        // Other reads (e.g. /api/hello): USER and ADMIN. Any other write: ADMIN only.
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint()));
        return http.build();
    }

    /**
     * Calls from the web interface (header X-Requested-With) get a plain 401, so the browser does not open
     * its own login pop-up. Other clients (Postman, curl, browser address bar) keep the standard Basic challenge.
     */
    private static AuthenticationEntryPoint entryPoint() {
        BasicAuthenticationEntryPoint basic = new BasicAuthenticationEntryPoint();
        basic.setRealmName("AutoLoc");
        return (request, response, authException) -> {
            if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                basic.commence(request, response, authException);
            }
        };
    }
}
