package dev.signalbridge.api.opportunities;

import dev.signalbridge.api.dto.ApiPage;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {
    private final OpportunityRepository repository;
    public OpportunityController(OpportunityRepository repository){ this.repository=repository; }

    @GetMapping
    public ApiPage<OpportunityItem> list(
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="12") int size,
            @RequestParam(required=false) String q,
            @RequestParam(required=false) String kind,
            @RequestParam(required=false) String region,
            @RequestParam(required=false) String scope,
            @RequestParam(defaultValue="false") boolean includeDemo) {
        return repository.list(page,size,q,kind,region,includeDemo,scope);
    }

    @GetMapping("/{id}")
    public OpportunityDetail detail(@PathVariable long id,
            @RequestParam(defaultValue="false") boolean includeDemo) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"invalid id");
        return repository.detail(id,includeDemo).orElseThrow(
            ()->new ResponseStatusException(HttpStatus.NOT_FOUND,"opportunity not found"));
    }
}
