package tn.bricocomptoir.identity.adapter.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.bricocomptoir.identity.application.port.out.AccountStore;

public class SessionVersionFilter extends OncePerRequestFilter {
    private final AccountStore accounts;

    public SessionVersionFilter(AccountStore accounts) { this.accounts = accounts; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SessionUser user) {
            boolean current = accounts.byId(user.id())
                    .filter(account -> account.active() && account.version() == user.version()).isPresent();
            if (!current) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
