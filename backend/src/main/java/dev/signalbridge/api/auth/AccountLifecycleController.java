package dev.signalbridge.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AccountLifecycleController {
  public record EmailRequest(String newEmail,String currentPassword) {}
  public record ConfirmRequest(String token) {}
  public record DeleteRequest(String currentPassword,String confirmation) {}
  private final AccountLifecycleService service;
  public AccountLifecycleController(AccountLifecycleService service){this.service=service;}
  private static long id(AccountPrincipal principal){
    if(principal==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
    return principal.id();
  }
  private static void signOutCurrentBrowser(HttpServletRequest request){
    HttpSession session=request.getSession(false);
    if(session!=null)session.invalidate();
    SecurityContextHolder.clearContext();
  }

  @PostMapping("/api/v1/account/email/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void requestEmail(@AuthenticationPrincipal AccountPrincipal principal,
      @RequestBody(required=false) EmailRequest body) {
    if(body==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"요청을 확인해 주세요.");
    service.requestEmailChange(id(principal),body.newEmail(),body.currentPassword());
  }

  @PostMapping("/api/v1/auth/email-change/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirm(@RequestBody(required=false) ConfirmRequest body,HttpServletRequest request){
    service.confirmEmailChange(body==null?null:body.token());
    // Invalidate the session in this browser; login must use the new email.
    signOutCurrentBrowser(request);
  }

  @DeleteMapping("/api/v1/account/profile")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AccountPrincipal principal,
      @RequestBody(required=false) DeleteRequest body,HttpServletRequest request){
    if(body==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"요청을 확인해 주세요.");
    service.deleteAccount(id(principal),body.currentPassword(),body.confirmation());
    signOutCurrentBrowser(request);
  }
}
