package dev.signalbridge.api.auth;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Do not insert into app_users until a user proves ownership of the email. */
@Service
public class SignupService {
  private final AccountService accounts;
  private final AccountRepository accountRepository;
  private final PendingSignupRepository pending;
  private final ResendEmailSender sender;
  private final SecureRandom random=new SecureRandom();
  public SignupService(AccountService accounts,AccountRepository accountRepository,
      PendingSignupRepository pending,ResendEmailSender sender) {
    this.accounts=accounts;this.accountRepository=accountRepository;
    this.pending=pending;this.sender=sender;
  }

  @Transactional
  public void request(String rawEmail,String rawNickname,String rawPassword) {
    String email=AccountService.normalizeEmail(rawEmail);
    String nickname=AccountService.validateNickname(rawNickname);
    String passwordHash=accounts.encodePendingPassword(rawPassword);
    if(accountRepository.findByEmail(email).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 등록된 이메일입니다. 기존 계정이라면 인증 메일 재발송을 이용해 주세요.");

    byte[] bytes=new byte[32];random.nextBytes(bytes);
    String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    if(!pending.create(email,nickname,passwordHash,EmailVerificationService.sha256(token)))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 이메일 인증을 기다리고 있습니다. 인증 메일 재발송을 이용해 주세요.");

    // A Resend error is unchecked and rolls back the pending row as well.
    // app_users is not touched until successful one-time link verification.
    sender.sendVerification(email,token);
  }
}
