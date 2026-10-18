package dev.signalbridge.api.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountLifecycleService {
  private final AccountProfileRepository profiles;
  private final AccountRepository accounts;
  private final AccountLifecycleRepository lifecycle;
  private final PasswordEncoder encoder;
  private final ResendEmailSender sender;
  private final SecureRandom random=new SecureRandom();
  public AccountLifecycleService(AccountProfileRepository profiles,AccountRepository accounts,
      AccountLifecycleRepository lifecycle,PasswordEncoder encoder,ResendEmailSender sender){
    this.profiles=profiles;this.accounts=accounts;this.lifecycle=lifecycle;
    this.encoder=encoder;this.sender=sender;
  }
  private String requirePassword(long userId,String password){
    if(password==null||password.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"비밀번호를 입력해 주세요.");
    var credentials=profiles.findCredentials(userId).orElseThrow(()->
      new ResponseStatusException(HttpStatus.UNAUTHORIZED,"회원 정보가 없습니다."));
    if(!encoder.matches(password,credentials.passwordHash()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"비밀번호가 일치하지 않습니다.");
    return credentials.passwordHash();
  }

  /** Require authentication AND current password before sending a change link. */
  @Transactional
  public void requestEmailChange(long userId,String newRawEmail,String currentPassword){
    String newEmail=AccountService.normalizeEmail(newRawEmail);
    requirePassword(userId,currentPassword);
    var profile=profiles.findProfile(userId).orElseThrow(()->
      new ResponseStatusException(HttpStatus.UNAUTHORIZED,"회원 정보가 없습니다."));
    if(!profile.emailVerified()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"기존 이메일 인증이 필요합니다.");
    if(profile.email().equalsIgnoreCase(newEmail))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"현재 주소와 다른 이메일을 입력해 주세요.");
    if(accounts.findByEmail(newEmail).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 등록된 이메일입니다.");
    byte[] bytes=new byte[32];random.nextBytes(bytes);
    String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    java.util.Arrays.fill(bytes,(byte)0);
    if(!lifecycle.reserveEmailChange(userId,newEmail,EmailVerificationService.sha256(token)))
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"2분 후 다시 요청해 주세요.");
    // A failed Resend request rolls back this token reservation.
    sender.sendEmailChange(newEmail,token);
  }

  /** This endpoint is public but requires possession of the random token. */
  @Transactional
  public void confirmEmailChange(String token){
    if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"유효하지 않은 인증 링크입니다.");
    var entry=lifecycle.consumeEmailChange(EmailVerificationService.sha256(token)).orElseThrow(()->
      new ResponseStatusException(HttpStatus.BAD_REQUEST,"링크가 만료되었거나 이미 사용됐습니다."));
    try {
      if(!lifecycle.changeVerifiedEmail(entry.userId(),entry.newEmail()))
        throw new ResponseStatusException(HttpStatus.CONFLICT,"변경할 수 없는 계정 상태입니다.");
    } catch(DuplicateKeyException ex){
      throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 사용 중인 이메일입니다.");
    }
    lifecycle.revokeOtherEmailTokens(entry.userId());
  }

  /** Never trust a user ID from the request body; use the session principal. */
  @Transactional
  public void deleteAccount(long userId,String currentPassword,String confirmation){
    if(!"DELETE".equals(confirmation))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"확인 문구 DELETE를 정확히 입력해 주세요.");
    String existingHash=requirePassword(userId,currentPassword);
    if(!lifecycle.deleteAccount(userId,existingHash))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"회원 상태가 변경되었습니다. 다시 시도해 주세요.");
  }
}
