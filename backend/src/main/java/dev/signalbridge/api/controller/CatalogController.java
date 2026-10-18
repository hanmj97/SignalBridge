package dev.signalbridge.api.controller;

import dev.signalbridge.api.dto.ApiPage;
import dev.signalbridge.api.dto.ChangeItem;
import dev.signalbridge.api.dto.RecordDetail;
import dev.signalbridge.api.dto.RecordSummary;
import dev.signalbridge.api.dto.SourceItem;
import dev.signalbridge.api.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/records")
    public ApiPage<RecordSummary> records(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long sourceId
    ) {
        return service.records(page, size, q, sourceId);
    }

    @GetMapping("/records/{id}")
    public RecordDetail record(@PathVariable long id) {
        return service.record(id);
    }

    // Returns the recent version changes for one release. Read-only endpoint.
    @GetMapping("/records/{id}/history")
    public List<ChangeItem> recordHistory(@PathVariable long id) {
        return service.recordHistory(id);
    }

    @GetMapping("/changes")
    public ApiPage<ChangeItem> changes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long sourceId
    ) {
        return service.changes(page, size, sourceId);
    }

    @GetMapping("/sources")
    public List<SourceItem> sources() {
        return service.sources();
    }
}
