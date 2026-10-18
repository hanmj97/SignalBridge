package dev.signalbridge.api.watches;

import dev.signalbridge.api.auth.AccountPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/watches/discord")
public class MemberDiscordController {
  private final MemberDiscordService service;
  public MemberDiscordController(MemberDiscordService service){this.service=service;}
  public record ConnectInput(String webhookUrl) {}

  @GetMapping public MemberDiscordService.Status status(@AuthenticationPrincipal AccountPrincipal user) {
    return service.get(user.id());
  }
  @PostMapping public MemberDiscordService.Status connect(
      @AuthenticationPrincipal AccountPrincipal user,@RequestBody ConnectInput input) {
    return service.connect(user.id(),input==null?null:input.webhookUrl());
  }
  @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disconnect(@AuthenticationPrincipal AccountPrincipal user) {
    service.disconnect(user.id());
  }
}
