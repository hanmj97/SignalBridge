package dev.signalbridge.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountProfileService {
  private final AccountProfileRepository repository;
  private final PasswordEncoder encoder;
  public AccountProfileService(AccountProfileRepository repository, PasswordEncoder encoder) {
    this.repository=repository; this.encoder=encoder;
  }

  @Transactional(readOnly=true)
  public AccountProfileRepository.Profile profile(long userId) {
    return repository.findProfile(userId).orElseThrow(() ->
      new ResponseStatusException(HttpStatus.UNAUTHORIZED, "계정 정보를 찾을 수 없습니다. 다시 로그인해 주세요."));
  }

  @Transactional
  public AccountProfileRepository.Profile updateNickname(long userId, String rawNickname) {
    String nickname=AccountService.validateNickname(rawNickname);
    if(!repository.updateNickname(userId,nickname))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"계정 정보를 찾을 수 없습니다.");
    return profile(userId);
  }

  @Transactional
  public void changePassword(long userId, String currentPassword, String newPassword) {
    if(currentPassword==null || currentPassword.isBlank())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"현재 비밀번호를 입력해 주세요.");
    AccountService.validatePassword(newPassword);
    var existing=repository.findCredentials(userId).orElseThrow(() ->
      new ResponseStatusException(HttpStatus.UNAUTHORIZED,"계정 정보를 찾을 수 없습니다."));
    if(!encoder.matches(currentPassword,existing.passwordHash()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"현재 비밀번호가 일치하지 않습니다.");
    if(encoder.matches(newPassword,existing.passwordHash()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"새 비밀번호는 기존 비밀번호와 달라야 합니다.");
    if(!repository.replacePassword(userId,existing.passwordHash(),encoder.encode(newPassword)))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"계정 정보가 변경되었습니다. 다시 시도해 주세요.");
  }
}
