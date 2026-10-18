package dev.signalbridge.api.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AccountPrincipal(long id, String email, String nickname, String passwordHash,
                               boolean emailVerified) implements UserDetails {
  public static AccountPrincipal from(Account a) {
    return new AccountPrincipal(a.id(),a.email(),a.nickname(),a.passwordHash(),a.emailVerified());
  }
  @Override public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
  }
  @Override public String getPassword() { return passwordHash; }
  @Override public String getUsername() { return email; }
  @Override public boolean isAccountNonExpired() { return true; }
  @Override public boolean isAccountNonLocked() { return true; }
  @Override public boolean isCredentialsNonExpired() { return true; }
  @Override public boolean isEnabled() { return emailVerified; }
}
