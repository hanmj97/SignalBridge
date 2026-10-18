package dev.signalbridge.api.operations;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public read-only observation of ingestion outcomes, not Airflow authentication or task control. */
@RestController
@RequestMapping("/api/v1/operations")
public class IngestionController {
    private final IngestionService service;

    public IngestionController(IngestionService service) {
        this.service = service;
    }

    @GetMapping("/ingestions")
    public IngestionSnapshot ingestions() {
        return service.snapshot();
    }
}
