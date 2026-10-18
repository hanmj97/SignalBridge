package dev.signalbridge.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Unauthenticated endpoints with normal session CSRF protection. */
@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {
  public record RequestInput(String email) {}
  public record ConfirmInput(String token,String newPassword) {}
  public record Message(String message) {}
  private final PasswordResetService service;
  public PasswordResetController(PasswordResetService service){this.service=service;}

  @PostMapping("/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Message request(@RequestBody(required=false) RequestInput body) {
    try {
      // Catch ONLY response errors: no account enumeration or provider status leak.
      service.request(body==null?null:body.email());
    } catch (org.springframework.web.server.ResponseStatusException ignored) {
      // A failed provider request has already rolled back the token reservation.
    }
    return new Message("해당 이메일로 인증된 계정이 있다면 재설정 메일을 보내드립니다.");
  }

  @PostMapping("/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirm(@RequestBody(required=false) ConfirmInput body) {
    service.confirm(body==null?null:body.token(),body==null?null:body.newPassword());
  }
}
