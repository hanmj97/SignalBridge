package dev.signalbridge.api.opportunities;

import dev.signalbridge.api.auth.AccountPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/opportunity-alerts")
public class OpportunityAlertController {
  public record CreateRule(String keyword, String kind, String region, Integer age) {}
  public record ToggleRule(Boolean enabled) {}
  public record Rule(long id, String keyword, String kind, String region, Integer age, boolean enabled, Instant createdAt) {}
  public record Event(long id, long ruleId, long catalogRecordId, String title, String sourceLabel,
                      String changeType, String status, Instant detectedAt, Instant deliveredAt) {}

  private final OpportunityAlertService service;
  public OpportunityAlertController(OpportunityAlertService service) { this.service=service; }
  private static long owner(AccountPrincipal member) {
    if(member==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
    return member.id();
  }
  @GetMapping public List<Rule> rules(@AuthenticationPrincipal AccountPrincipal member) {
    return service.rules(owner(member));
  }
  @GetMapping("/events") public List<Event> events(@AuthenticationPrincipal AccountPrincipal member) {
    return service.events(owner(member));
  }
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public Rule create(@AuthenticationPrincipal AccountPrincipal member,@RequestBody CreateRule input) {
    return service.create(owner(member),input);
  }
  @PatchMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void toggle(@AuthenticationPrincipal AccountPrincipal member,@PathVariable long id,
                     @RequestBody ToggleRule input) {
    service.toggle(owner(member),id,input);
  }
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AccountPrincipal member,@PathVariable long id) {
    service.delete(owner(member),id);
  }
}
