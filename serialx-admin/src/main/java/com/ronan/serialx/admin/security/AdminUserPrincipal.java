package com.ronan.serialx.admin.security;

import java.util.Collection;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 后台认证用户主体。
 */
@Getter
@RequiredArgsConstructor
public class AdminUserPrincipal implements UserDetails {

    /**
     * 后台用户ID。
     */
    private final Long userId;

    /**
     * 登录用户名。
     */
    private final String username;

    /**
     * 用户角色。
     */
    private final String roles;

    /**
     * 返回用户角色权限。
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return List.of(roles.split(",")).stream()
                .filter(role -> !role.isBlank())
                .map(String::trim)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    /**
     * JWT 认证场景不在主体中保存密码。
     */
    @Override
    public String getPassword() {
        return "";
    }

    /**
     * 后台账号不过期。
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 后台账号锁定由状态字段控制。
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * JWT 认证场景不维护凭证过期状态。
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 主体启用状态在过滤器中校验。
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
