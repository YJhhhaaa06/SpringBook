package io.github.yjhhhaaa06.springbook.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Bearer 令牌解析过滤器：有效则把 AuthPrincipal 写入 SecurityContext，无效则不设置（由后续规则返回 401）。
 * 不注册为独立 Servlet Filter，仅挂在 Security 过滤链内。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            DecodedJWT jwt = jwtService.verify(header.substring(7));
            if (jwt != null) {
                Long readerId = jwt.getClaim("readerId").isNull() ? null : jwt.getClaim("readerId").asLong();
                AuthPrincipal principal = new AuthPrincipal(
                        Long.valueOf(jwt.getSubject()),
                        jwt.getClaim("username").asString(),
                        jwt.getClaim("role").asString(),
                        readerId);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
