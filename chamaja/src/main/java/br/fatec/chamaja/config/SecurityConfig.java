package br.fatec.chamaja.config;

import br.fatec.chamaja.repository.OperadorRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * RF09 / RNF05: o painel (/painel/**) exige login de operador; o lado do cliente é público.
 * Senhas dos operadores são guardadas com BCrypt. O CSRF fica ligado (os formulários do
 * Thymeleaf já enviam o token).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/painel/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(f -> f
                        .loginPage("/login")
                        .defaultSuccessUrl("/painel", true)
                        .permitAll())
                .logout(l -> l.logoutSuccessUrl("/login?saiu").permitAll())
                .headers(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService usuarios(OperadorRepository operadores) {
        return usuario -> operadores.findByUsuario(usuario)
                .map(o -> User.withUsername(o.getUsuario())
                        .password(o.getSenhaHash())
                        .roles("OPERADOR")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Operador não encontrado"));
    }
}
