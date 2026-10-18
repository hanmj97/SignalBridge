package dev.signalbridge.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class PasswordResetServiceTest {
  AccountRepository accounts;
  AccountProfileRepository profiles;
  PasswordResetRepository tokens;
  ResendEmailSender sender;
  PasswordEncoder encoder;
  PasswordResetService service;
  @BeforeEach void setup() {
    accounts=mock(AccountRepository.class);
    profiles=mock(AccountProfileRepository.class);
    tokens=mock(PasswordResetRepository.class);
    sender=mock(ResendEmailSender.class);
    encoder=new BCryptPasswordEncoder(4);
    service=new PasswordResetService(accounts,profiles,tokens,encoder,sender);
  }
  private Account account(boolean verified) {
    return new Account(7,"member@example.com","Tester","hash",Instant.now(),verified);
  }
  @Test void unknownEmailDoesNotCreateTokenOrSendEmail() {
    when(accounts.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
    service.request("UNKNOWN@example.com");
    verifyNoInteractions(tokens,sender);
  }
  @Test void unverifiedAccountDoesNotSend() {
    when(accounts.findByEmail("member@example.com")).thenReturn(Optional.of(account(false)));
    service.request("member@example.com");
    verifyNoInteractions(tokens,sender);
  }
  @Test void rateLimitedAccountDoesNotSend() {
    when(accounts.findByEmail("member@example.com")).thenReturn(Optional.of(account(true)));
    when(tokens.reserve(eq(7L),anyString())).thenReturn(false);
    service.request("member@example.com");
    verify(sender,never()).sendPasswordReset(anyString(),anyString());
  }
  @Test void validRequestStoresHashNotRawToken() {
    when(accounts.findByEmail("member@example.com")).thenReturn(Optional.of(account(true)));
    when(tokens.reserve(eq(7L),anyString())).thenReturn(true);
    service.request("member@example.com");
    verify(tokens).reserve(eq(7L),argThat(v->v.matches("[a-f0-9]{64}")));
    verify(sender).sendPasswordReset(eq("member@example.com"),argThat(v->v.matches("[A-Za-z0-9_-]{43}")));
  }
  @Test void badTokenDoesNotChangePassword() {
    assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
      ()->service.confirm("bad","NewPassword#2026")).getStatusCode());
    verifyNoInteractions(tokens,profiles);
  }
  @Test void expiredOrConsumedTokenDoesNotChangePassword() {
    String token="A".repeat(43);
    when(tokens.consume(anyString())).thenReturn(Optional.empty());
    assertThrows(ResponseStatusException.class,()->service.confirm(token,"NewPassword#2026"));
    verify(profiles,never()).replacePassword(anyLong(),anyString(),anyString());
  }
  @Test void tokenOwnerOnlyGetsNewPassword() {
    String old=encoder.encode("OldPassword#2026");
    when(tokens.consume(anyString())).thenReturn(Optional.of(7L));
    when(profiles.findCredentials(7)).thenReturn(Optional.of(new AccountProfileRepository.Credentials(old)));
    when(profiles.replacePassword(eq(7L),eq(old),anyString())).thenAnswer(invocation->{
      String next=(String) invocation.getArgument(2);
      assertTrue(encoder.matches("NewPassword#2026",next));
      return true;
    });
    service.confirm("B".repeat(43),"NewPassword#2026");
    verify(profiles).replacePassword(eq(7L),eq(old),anyString());
    verify(profiles,never()).replacePassword(eq(8L),anyString(),anyString());
  }
  @Test void existingPasswordCannotBeReused() {
    String old=encoder.encode("SamePassword#2026");
    when(tokens.consume(anyString())).thenReturn(Optional.of(7L));
    when(profiles.findCredentials(7)).thenReturn(Optional.of(new AccountProfileRepository.Credentials(old)));
    assertThrows(ResponseStatusException.class,()->service.confirm("B".repeat(43),"SamePassword#2026"));
    verify(profiles,never()).replacePassword(anyLong(),anyString(),anyString());
  }
}
