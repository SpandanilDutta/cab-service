package com.cabservice.security.jwt;

import com.cabservice.security.config.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Runs on every request. If a valid access token is present, puts an AuthenticatedUser
 * into the security context. No database query is made.
 *
 * Deliberately NOT annotated with @Component: Spring Boot would also register a
 * @Component filter as a plain servlet filter, so it would run twice and in the wrong order.
 * SecurityConfig creates it manually instead.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtService.parse(header.substring(BEARER_PREFIX.length())).ifPresent(claims -> {
                UUID userId = UUID.fromString(claims.getSubject());
                String username = claims.get("username", String.class);

                Object rawRoles = claims.get("roles");
                List<?> roles = rawRoles instanceof List<?> list ? list : List.of();
                var authorities = roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                var authentication = new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(userId, username), null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        // An invalid or missing token simply leaves the request unauthenticated;
        // the security rules then answer 401 for protected endpoints.
        filterChain.doFilter(request, response);
    }
}