package dev.signalbridge.api.service;

import dev.signalbridge.api.dto.ApiPage;
import dev.signalbridge.api.dto.ChangeItem;
import dev.signalbridge.api.dto.RecordDetail;
import dev.signalbridge.api.dto.RecordSummary;
import dev.signalbridge.api.dto.SourceItem;
import dev.signalbridge.api.repository.CatalogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CatalogService {
    private final CatalogRepository repository;

    public CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    public ApiPage<RecordSummary> records(int page, int size, String q, Long sourceId) {
        validatePagination(page, size);
        validateSourceId(sourceId);
        String normalizedQuery = q == null ? null : q.trim();
        if (normalizedQuery != null && normalizedQuery.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q must be at most 100 characters");
        }
        return repository.findRecords(page, size, normalizedQuery, sourceId);
    }

    public ApiPage<RecordSummary> myRecords(int page, int size, String q, Long sourceId, long userId) {
        validatePagination(page, size);
        validateSourceId(sourceId);
        String query = q == null ? null : q.trim();
        if (query != null && query.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q is too long");
        return repository.findMyRecords(page, size, query, sourceId, userId);
    }

    public ApiPage<ChangeItem> myChanges(int page, int size, Long sourceId, long userId) {
        validatePagination(page, size);
        validateSourceId(sourceId);
        return repository.findMyChanges(page, size, sourceId, userId);
    }

    public List<SourceItem> mySources(long userId) {return repository.findMySources(userId);}

    public RecordDetail record(long id) {
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "record id must be positive");
        }
        return repository.findRecord(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
    }

    public List<ChangeItem> recordHistory(long id) {
        // Reuse record validation to return HTTP 400 or 404 consistently.
        record(id);
        return repository.findRecordHistory(id);
    }

    public ApiPage<ChangeItem> changes(int page, int size, Long sourceId) {
        validatePagination(page, size);
        validateSourceId(sourceId);
        return repository.findChanges(page, size, sourceId);
    }

    public List<SourceItem> sources() {
        return repository.findSources();
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page must be >= 0 and size must be between 1 and 100");
        }
    }

    private void validateSourceId(Long sourceId) {
        if (sourceId != null && sourceId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceId must be positive");
        }
    }
}
