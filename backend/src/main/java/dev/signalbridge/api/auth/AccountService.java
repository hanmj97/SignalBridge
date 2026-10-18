package dev.signalbridge.api.auth;

import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService implements UserDetailsService {
  private final AccountRepository repository;
  private final PasswordEncoder encoder;
  public AccountService(AccountRepository repo,PasswordEncoder encoder) {this.repository=repo;this.encoder=encoder;}

  static String normalizeEmail(String input) {
    if(input==null) throw bad("이메일을 확인해 주세요.");
    String email=input.strip().toLowerCase(Locale.ROOT);
    if(email.length()>254 || !email.matches("^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$")) throw bad("올바른 이메일이 아닙니다.");
    return email;
  }
  static String validateNickname(String input) {
    if(input==null) throw bad("닉네임을 확인해 주세요.");
    String nickname=input.strip();
    if(nickname.length()<2||nickname.length()>40||nickname.chars().anyMatch(Character::isISOControl)) throw bad("닉네임은 2~40자로 입력해 주세요.");
    return nickname;
  }
  static void validatePassword(String password) {
    if(password==null || password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || password.chars().anyMatch(Character::isISOControl))
      throw bad("비밀번호는 12~72자이며 제어 문자를 포함할 수 없습니다.");
  }
  // Passwords for pending registrations use the same BCrypt encoder as active accounts.
  String encodePendingPassword(String password) {
    validatePassword(password);
    return encoder.encode(password);
  }
  @Transactional
  public Account register(String email,String nickname,String password) {
    String normalized=normalizeEmail(email);String cleaned=validateNickname(nickname);validatePassword(password);
    try { return repository.insert(normalized,cleaned,encoder.encode(password)); }
    catch(DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 가입된 이메일입니다."); }
  }
  @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    String normalized=username==null?"":username.strip().toLowerCase(Locale.ROOT);
    return repository.findByEmail(normalized).map(AccountPrincipal::from)
      .orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));
  }
  private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
