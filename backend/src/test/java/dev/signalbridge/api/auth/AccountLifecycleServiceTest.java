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

class AccountLifecycleServiceTest {
  private AccountProfileRepository profiles;
  private AccountRepository accounts;
  private AccountLifecycleRepository lifecycle;
  private PasswordEncoder encoder;
  private ResendEmailSender sender;
  private AccountLifecycleService service;
  private String savedHash;

  @BeforeEach void setup(){
    profiles=mock(AccountProfileRepository.class);
    accounts=mock(AccountRepository.class);
    lifecycle=mock(AccountLifecycleRepository.class);
    encoder=new BCryptPasswordEncoder(4);
    sender=mock(ResendEmailSender.class);
    service=new AccountLifecycleService(profiles,accounts,lifecycle,encoder,sender);
    savedHash=encoder.encode("CurrentPassword#2026");
    when(profiles.findCredentials(8L)).thenReturn(Optional.of(new AccountProfileRepository.Credentials(savedHash)));
    when(profiles.findProfile(8L)).thenReturn(Optional.of(
      new AccountProfileRepository.Profile(8L,"old@example.com","Tester",true,Instant.now())));
  }

  @Test void emailChangeRequiresCurrentPassword(){
    ResponseStatusException ex=assertThrows(ResponseStatusException.class,()->
      service.requestEmailChange(8L,"new@example.com","wrong-pass"));
    assertEquals(HttpStatus.BAD_REQUEST,ex.getStatusCode());
    verifyNoInteractions(lifecycle,sender);
  }

  @Test void emailChangeOnlyRequestsMailAndDoesNotImmediatelyUpdateUser(){
    when(lifecycle.reserveEmailChange(eq(8L),eq("new@example.com"),anyString())).thenReturn(true);
    service.requestEmailChange(8L,"New@Example.com","CurrentPassword#2026");
    verify(sender).sendEmailChange(eq("new@example.com"),anyString());
    verify(lifecycle,never()).changeVerifiedEmail(anyLong(),anyString());
  }

  @Test void otherAccountsEmailRejected(){
    when(accounts.findByEmail("used@example.com")).thenReturn(Optional.of(new Account(99L,"used@example.com","Taken",savedHash,Instant.now(),true)));
    ResponseStatusException ex=assertThrows(ResponseStatusException.class,()->
      service.requestEmailChange(8L,"used@example.com","CurrentPassword#2026"));
    assertEquals(HttpStatus.CONFLICT,ex.getStatusCode());
    verifyNoInteractions(lifecycle,sender);
  }

  @Test void invalidEmailLinkDoesNotConsumeToken(){
    assertThrows(ResponseStatusException.class,()->service.confirmEmailChange("not-a-token"));
    verify(lifecycle,never()).consumeEmailChange(anyString());
  }

  @Test void validLinkUpdatesSameUserIdAndRevokesOldRecoveryTokens(){
    when(lifecycle.consumeEmailChange(anyString())).thenReturn(Optional.of(
      new AccountLifecycleRepository.EmailChange(8L,"new@example.com")));
    when(lifecycle.changeVerifiedEmail(8L,"new@example.com")).thenReturn(true);
    service.confirmEmailChange("A".repeat(43));
    verify(lifecycle).changeVerifiedEmail(8L,"new@example.com");
    verify(lifecycle).revokeOtherEmailTokens(8L);
  }

  @Test void deleteWrongPasswordDoesNotRemoveUser(){
    assertThrows(ResponseStatusException.class,()->service.deleteAccount(8L,"bad-password","DELETE"));
    verify(lifecycle,never()).deleteAccount(anyLong(),anyString());
  }

  @Test void deleteNeedsExplicitConfirmation(){
    assertThrows(ResponseStatusException.class,()->
      service.deleteAccount(8L,"CurrentPassword#2026","delete"));
    verify(lifecycle,never()).deleteAccount(anyLong(),anyString());
  }

  @Test void deleteCorrectCredentialsTargetsLoggedInUserOnly(){
    when(lifecycle.deleteAccount(8L,savedHash)).thenReturn(true);
    service.deleteAccount(8L,"CurrentPassword#2026","DELETE");
    verify(lifecycle).deleteAccount(8L,savedHash);
  }
}
