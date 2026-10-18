package dev.signalbridge.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/account")
public class AccountProfileController {
  public record NicknameInput(String nickname) {}
  public record PasswordInput(String currentPassword,String newPassword) {}
  private final AccountProfileService service;
  public AccountProfileController(AccountProfileService service) { this.service=service; }

  private static long memberId(AccountPrincipal principal) {
    if(principal==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
    return principal.id();
  }
  @GetMapping("/profile")
  public AccountProfileRepository.Profile profile(@AuthenticationPrincipal AccountPrincipal principal) {
    return service.profile(memberId(principal));
  }
  @PatchMapping("/nickname")
  public AccountProfileRepository.Profile nickname(@AuthenticationPrincipal AccountPrincipal principal,
      @RequestBody NicknameInput input) {
    return service.updateNickname(memberId(principal),input==null?null:input.nickname());
  }
  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void password(@AuthenticationPrincipal AccountPrincipal principal,
      @RequestBody PasswordInput input, HttpServletRequest request) {
    long id=memberId(principal);
    if(input==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"요청 본문을 확인해 주세요.");
    service.changePassword(id,input.currentPassword(),input.newPassword());
    // Force a fresh login in this browser after a successful password change.
    HttpSession session=request.getSession(false);
    if(session!=null) session.invalidate();
    SecurityContextHolder.clearContext();
  }
}
