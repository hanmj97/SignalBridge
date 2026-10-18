package dev.signalbridge.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class AccountServiceTest {
  @Test void normalizesEmails(){assertEquals("user@example.com",AccountService.normalizeEmail(" User@EXAMPLE.COM "));}
  @Test void invalidEmailsRejected(){assertThrows(ResponseStatusException.class,()->AccountService.normalizeEmail("not an email"));}
  @Test void passwordMinimum(){
    assertThrows(ResponseStatusException.class,()->AccountService.validatePassword("short"));
    assertDoesNotThrow(()->AccountService.validatePassword("aVeryLongPassword123!"));
  }
  @Test void nicknameLimits(){assertThrows(ResponseStatusException.class,()->AccountService.validateNickname("a"));}
}
