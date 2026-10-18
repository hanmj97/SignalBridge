package dev.signalbridge.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {
  private final AccountRepository accounts;
  private final AccountProfileRepository profiles;
  private final PasswordResetRepository tokens;
  private final PasswordEncoder encoder;
  private final ResendEmailSender sender;
  private final SecureRandom random=new SecureRandom();

  public PasswordResetService(AccountRepository accounts, AccountProfileRepository profiles,
      PasswordResetRepository tokens, PasswordEncoder encoder, ResendEmailSender sender) {
    this.accounts=accounts;this.profiles=profiles;this.tokens=tokens;
    this.encoder=encoder;this.sender=sender;
  }

  /** Caller always returns 202, regardless of whether an account exists.
   * Throws on delivery failure so the token reservation is rolled back. */
  @Transactional
  public void request(String suppliedEmail) {
    String email=AccountService.normalizeEmail(suppliedEmail);
    Optional<Account> candidate=accounts.findByEmail(email);
    if(candidate.isEmpty() || !candidate.get().emailVerified()) return;
    byte[] raw=new byte[32];random.nextBytes(raw);
    String token=Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    java.util.Arrays.fill(raw,(byte)0);
    if(tokens.reserve(candidate.get().id(),EmailVerificationService.sha256(token)))
      sender.sendPasswordReset(email,token);
  }

  /** The token is consumed exactly once in the same transaction as the update. */
  @Transactional
  public void confirm(String rawToken, String newPassword) {
    if(rawToken==null || !rawToken.matches("[A-Za-z0-9_-]{43}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"유효하지 않은 재설정 링크입니다.");
    AccountService.validatePassword(newPassword);
    String tokenHash=EmailVerificationService.sha256(rawToken);
    long userId=tokens.consume(tokenHash).orElseThrow(() ->
      new ResponseStatusException(HttpStatus.BAD_REQUEST,"링크가 만료되었거나 이미 사용됐습니다."));
    var existing=profiles.findCredentials(userId).orElseThrow(() ->
      new ResponseStatusException(HttpStatus.BAD_REQUEST,"링크가 만료되었거나 이미 사용됐습니다."));
    if(encoder.matches(newPassword,existing.passwordHash()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"기존 비밀번호와 다른 비밀번호를 입력해 주세요.");
    if(!profiles.replacePassword(userId,existing.passwordHash(),encoder.encode(newPassword)))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"계정이 변경됐습니다. 다시 요청해 주세요.");
  }
}
