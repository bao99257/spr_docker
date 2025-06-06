
package com.example.web_security.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.web_security.Repo.UsersRepository; // Sửa package
import com.example.web_security.restapi.JwtAuthFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Autowired
        private UsersRepository userRepository;

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public UserDetailsService userDetailsService() {
                return username -> userRepository.findByUsername(username)
                                .map(user -> org.springframework.security.core.userdetails.User
                                                .withUsername(user.getUsername())
                                                .password(user.getPassword())
                                                .roles(user.getRole().replace("ROLE_", ""))
                                                .build())
                                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        }

        @Bean
        public AuthenticationManager authenticationManager(HttpSecurity http,
                        UserDetailsService userDetailsService,
                        PasswordEncoder passwordEncoder) throws Exception {
                AuthenticationManagerBuilder authenticationManagerBuilder = http
                                .getSharedObject(AuthenticationManagerBuilder.class);
                authenticationManagerBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
                return authenticationManagerBuilder.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000")); // Địa chỉ React app
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE",
                                "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("Authorization",
                                "Content-Type", "X-Requested-With"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        // SecurityFilterChain cho các endpoint /api/** (dùng JWT)
        @Bean
        @Order(1)
        public SecurityFilterChain apiFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
                http
                                .securityMatcher("/api/**") // Chỉ áp dụng cho các yêu cầu /api/**
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(csrf -> csrf.disable()) // Tắt CSRF cho API
                                .formLogin(form -> form.disable()) // Tắt form login cho /api/**
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/api/register", "/api/generateToken", "/api/welcome")
                                                .permitAll()
                                                .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                                .anyRequest().authenticated())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Stateless cho
                                // API
                                )
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        // SecurityFilterChain cho các endpoint còn lại (dùng form login)
        @Bean
        @Order(2)
        public SecurityFilterChain formLoginFilterChain(HttpSecurity http) throws Exception {
                http
                                .authorizeHttpRequests(auth -> auth
                                                // Admin có quyền truy cập vào các đường dẫn /admin/**
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                // User có quyền truy cập vào các đường dẫn /user/**
                                                .requestMatchers("/user/**").hasRole("USER")
                                                // Các trang đăng nhập, đăng ký, và H2 Console không yêu cầu xác thực
                                                .requestMatchers("/login", "/register", "/h2-console/**").permitAll()
                                                // Các yêu cầu khác yêu cầu người dùng phải xác thực
                                                .anyRequest().authenticated())
                                .formLogin(form -> form
                                                // Trang login tùy chỉnh
                                                .loginPage("/login")
                                                // Sau khi đăng nhập thành công, chuyển hướng về trang /home
                                                .defaultSuccessUrl("/home", true)
                                                // Cho phép truy cập không yêu cầu xác thực cho trang đăng nhập
                                                .permitAll())
                                .logout(logout -> logout
                                                .logoutUrl("/logout") // URL logout
                                                .logoutSuccessUrl("/login") // URL chuyển hướng sau khi logout thành
                                                // công
                                                .permitAll() // Cho phép logout mà không yêu cầu xác thực
                                                .invalidateHttpSession(true) // Hủy bỏ session khi logout
                                                .deleteCookies("JSESSIONID")) // Xóa cookie phiên
                                .csrf(csrf -> csrf
                                                // Bỏ qua CSRF cho H2 Console (nếu sử dụng H2 console)
                                                .ignoringRequestMatchers("/h2-console/**"))
                                .headers(headers -> headers
                                                // Cấu hình bảo mật để cho phép iframe từ cùng một nguồn
                                                .frameOptions(frameOptions -> frameOptions.sameOrigin()));

                return http.build();
        }
}