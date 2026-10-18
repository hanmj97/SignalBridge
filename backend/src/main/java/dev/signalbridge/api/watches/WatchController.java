package dev.signalbridge.api.watches;

import dev.signalbridge.api.auth.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/watches")
public class WatchController {
  private final WatchService service;
  public WatchController(WatchService service){this.service=service;}
  @GetMapping public List<WatchDtos.WatchRule> all(@AuthenticationPrincipal AccountPrincipal member){return service.rules(member.id());}
  @GetMapping("/events") public List<WatchDtos.WatchEvent> events(@AuthenticationPrincipal AccountPrincipal member){return service.events(member.id());}
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public WatchDtos.WatchRule create(@AuthenticationPrincipal AccountPrincipal member,@RequestBody WatchDtos.CreateWatch body){return service.create(member.id(),body);}
  @PatchMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void enable(@AuthenticationPrincipal AccountPrincipal member,@PathVariable long id,@RequestBody WatchDtos.SetEnabled body){service.setEnabled(member.id(),id,body);}
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AccountPrincipal member,@PathVariable long id){service.delete(member.id(),id);}
}
