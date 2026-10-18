package dev.signalbridge.api.opportunities;

import dev.signalbridge.api.dto.ApiPage;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpportunityControllerTest {
    @Test void controllerDoesNotExposeMissingRecord() {
        OpportunityRepository repo=mock(OpportunityRepository.class);
        when(repo.detail(777,true)).thenReturn(Optional.empty());
        OpportunityController controller=new OpportunityController(repo);
        assertEquals(404,assertThrows(ResponseStatusException.class,
                ()->controller.detail(777,true)).getStatusCode().value());
    }

    @Test void controllerRejectsInvalidId() {
        OpportunityController controller=new OpportunityController(mock(OpportunityRepository.class));
        assertEquals(400,assertThrows(ResponseStatusException.class,
                ()->controller.detail(-1,true)).getStatusCode().value());
    }

    @Test void noV2ConfigFailsExplicitlyWithoutTouchingLegacyDb() {
        OpportunityRepository repo=new OpportunityRepository("", "test_user", "test_pass");
        assertEquals(503,assertThrows(ResponseStatusException.class,
                ()->repo.list(0,12,null,null,null,true)).getStatusCode().value());
    }

    @Test void invalidTwoCategoryScopeIsRejectedBeforeQueryingAnyDatabase() {
        OpportunityRepository repo=new OpportunityRepository("", "test_user", "test_pass");
        assertEquals(400,assertThrows(ResponseStatusException.class,
                ()->repo.list(0,12,null,null,null,true,"BUSINESS_GRANTS")).getStatusCode().value());
    }

    @Test void invalidFilterIsRejected() {
        OpportunityRepository repo=new OpportunityRepository("", "test_user", "test_pass");
        assertEquals(400,assertThrows(ResponseStatusException.class,
                ()->repo.list(0,12,null,"INVALID_KIND",null,true)).getStatusCode().value());
    }
}
