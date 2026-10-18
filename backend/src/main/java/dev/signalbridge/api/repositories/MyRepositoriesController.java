package dev.signalbridge.api.repositories;

import dev.signalbridge.api.auth.AccountPrincipal;
import dev.signalbridge.api.dto.ApiPage;
import dev.signalbridge.api.dto.ChangeItem;
import dev.signalbridge.api.dto.RecordSummary;
import dev.signalbridge.api.dto.SourceItem;
import dev.signalbridge.api.service.CatalogService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/my-repositories")
public class MyRepositoriesController {
    private final RepositoryManageService manage;
    private final CatalogService catalog;
    public record Registration(String url) {}
    public MyRepositoriesController(RepositoryManageService manage,CatalogService catalog){this.manage=manage;this.catalog=catalog;}
    @GetMapping public List<UserRepositoryDao.Entry> list(@AuthenticationPrincipal AccountPrincipal member){return manage.list(member.id());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public List<UserRepositoryDao.Entry> add(@AuthenticationPrincipal AccountPrincipal member,@RequestBody Registration body){
        return manage.add(member.id(),body==null?null:body.url());
    }
    @DeleteMapping("/{sourceId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AccountPrincipal member,@PathVariable long sourceId){manage.delete(member.id(),sourceId);}
    @GetMapping("/sources") public List<SourceItem> sources(@AuthenticationPrincipal AccountPrincipal member){return catalog.mySources(member.id());}
    @GetMapping("/records") public ApiPage<RecordSummary> records(@AuthenticationPrincipal AccountPrincipal member,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,
      @RequestParam(required=false) String q,@RequestParam(required=false) Long sourceId){
        return catalog.myRecords(page,size,q,sourceId,member.id());
    }
    @GetMapping("/changes") public ApiPage<ChangeItem> changes(@AuthenticationPrincipal AccountPrincipal member,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,
      @RequestParam(required=false) Long sourceId){return catalog.myChanges(page,size,sourceId,member.id());}
}
