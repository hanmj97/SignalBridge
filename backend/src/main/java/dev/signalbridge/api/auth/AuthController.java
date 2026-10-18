package dev.signalbridge.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  public record RegisterInput(String email,String nickname,String password) {}
  public record LoginInput(String email,String password) {}
  public record EmailInput(String email) {}
  public record VerifyInput(String token) {}
  public record Message(String message) {}
  public record Member(long id,String email,String nickname) {}
  private final AccountService service;
  private final SignupService signup;
  private final EmailVerificationService verification;
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository contexts;
  public AuthController(AccountService service,SignupService signup,EmailVerificationService verification,
      AuthenticationManager manager,SecurityContextRepository contexts){
    this.service=service;this.signup=signup;this.verification=verification;this.authenticationManager=manager;this.contexts=contexts;
  }
  private static Member view(AccountPrincipal a){return new Member(a.id(),a.email(),a.nickname());}

  @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) {return Map.of("token",token.getToken());}
  @GetMapping("/me") public Member me(@AuthenticationPrincipal AccountPrincipal member) {
    if(member==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
    return view(member);
  }
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Message register(@RequestBody RegisterInput input) {
    if(input==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"요청 본문이 필요합니다.");
    signup.request(input.email(),input.nickname(),input.password());
    return new Message("인증 메일을 요청했습니다. 링크 확인 후 계정이 생성됩니다.");
  }
  @PostMapping("/resend-verification")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Message resend(@RequestBody EmailInput input) {
    verification.resendIfEligible(input==null?null:input.email());
    return new Message("계정이 미인증 상태이고 발송 제한에 해당하지 않으면 메일을 발송합니다.");
  }
  @PostMapping("/verify-email")
  public Message verify(@RequestBody VerifyInput input) {
    verification.verify(input==null?null:input.token());
    return new Message("이메일 인증이 완료되었습니다. 로그인해 주세요.");
  }
  @PostMapping("/login")
  public Member login(@RequestBody LoginInput input,HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response) {
    if(input==null || input.email()==null || input.password()==null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"이메일과 비밀번호를 입력해 주세요.");
    try {
      Authentication authentication=authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(input.email(),input.password()));
      HttpSession old=request.getSession(false);
      if(old!=null) request.changeSessionId();
      SecurityContext context=SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      contexts.saveContext(context,request,response);
      return view((AccountPrincipal)authentication.getPrincipal());
    } catch (DisabledException ex) {
      // No account-enumeration signal: same response as an incorrect password.
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"이메일·비밀번호 또는 이메일 인증 상태를 확인해 주세요.");
    } catch (BadCredentialsException ex) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호를 확인해 주세요.");
    }
  }
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request) {
    HttpSession session=request.getSession(false);
    if(session!=null) session.invalidate();
    SecurityContextHolder.clearContext();
  }
}
