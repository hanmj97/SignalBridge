package dev.signalbridge.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailVerificationService {
  private final EmailVerificationRepository repository;
  private final ResendEmailSender sender;
  private final PendingSignupRepository pending;
  private final AccountRepository accounts;
  private final SecureRandom random=new SecureRandom();
  public EmailVerificationService(EmailVerificationRepository repository,ResendEmailSender sender,
      PendingSignupRepository pending,AccountRepository accounts) {
    this.repository=repository;this.sender=sender;this.pending=pending;this.accounts=accounts;
  }
  public void sendForNewAccount(String email) { issue(email); }
  @Transactional
  public void resendIfEligible(String email) {
    // Deliberately identical response for unknown, verified and cooldown-blocked addresses.
    if(email==null || email.length()>254) return;
    try { issue(AccountService.normalizeEmail(email)); }
    catch (ResponseStatusException ignored) {
      // Do not reveal account existence or Resend/provider failures.
      // A failed delivery MUST NOT commit an unusable token or send cooldown.
      if(TransactionSynchronizationManager.isActualTransactionActive())
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
  }
  private void issue(String email) {
    byte[] bytes=new byte[32];random.nextBytes(bytes);
    String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String hash=sha256(token);
    var pendingDelivery=pending.reserveResend(email,hash);
    if(pendingDelivery.isPresent()) {
      sender.sendVerification(pendingDelivery.get(),token);
      return;
    }
    // Backward compatibility: already existing, unverified Phase 9/10 accounts.
    repository.reserve(email,hash).ifPresent(delivery->sender.sendVerification(delivery.email(),token));
  }
  @Transactional
  public void verify(String token) {
    // 32 random bytes in URL-safe Base64 without padding = 43 characters.
    if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"유효하지 않은 인증 링크입니다.");
    String hash=sha256(token);
    var completed=pending.consume(hash);
    if(completed.isPresent()) {
      var signup=completed.get();
      accounts.insertVerified(signup.email(),signup.nickname(),signup.passwordHash());
      return;
    }
    if(!repository.consumeAndVerify(hash))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"인증 링크가 만료되었거나 이미 사용됐습니다.");
  }
  static String sha256(String token) {
    try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
      .digest(token.getBytes(StandardCharsets.UTF_8)));}
    catch(java.security.NoSuchAlgorithmException ex) {throw new IllegalStateException(ex);}
  }
}
