package dev.signalbridge.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SignupServiceTest {
  @Test void registrationOnlyCreatesPendingDataAndSendsEmail() {
    AccountService accounts=mock(AccountService.class);
    AccountRepository realAccounts=mock(AccountRepository.class);
    PendingSignupRepository pending=mock(PendingSignupRepository.class);
    ResendEmailSender sender=mock(ResendEmailSender.class);
    when(accounts.encodePendingPassword(anyString())).thenReturn("bcrypt-hash");
    when(realAccounts.findByEmail(anyString())).thenReturn(Optional.empty());
    when(pending.create(eq("user@example.com"),eq("Tester"),eq("bcrypt-hash"),anyString())).thenReturn(true);
    new SignupService(accounts,realAccounts,pending,sender).request("User@example.com","Tester","validpassword123");
    verify(pending).create(eq("user@example.com"),eq("Tester"),eq("bcrypt-hash"),matches("[a-f0-9]{64}"));
    verify(sender).sendVerification(eq("user@example.com"),matches("[A-Za-z0-9_-]{43}"));
    verify(realAccounts,never()).insert(anyString(),anyString(),anyString());
    verify(realAccounts,never()).insertVerified(anyString(),anyString(),anyString());
  }

  @Test void emailProviderFailureIsPropagatedNotReportedAsSuccessfulSignup() {
    AccountService accounts=mock(AccountService.class);
    AccountRepository realAccounts=mock(AccountRepository.class);
    PendingSignupRepository pending=mock(PendingSignupRepository.class);
    ResendEmailSender sender=mock(ResendEmailSender.class);
    when(accounts.encodePendingPassword(anyString())).thenReturn("bcrypt-hash");
    when(realAccounts.findByEmail(anyString())).thenReturn(Optional.empty());
    when(pending.create(anyString(),anyString(),anyString(),anyString())).thenReturn(true);
    doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
      .when(sender).sendVerification(anyString(),anyString());
    assertThrows(ResponseStatusException.class,()->
      new SignupService(accounts,realAccounts,pending,sender).request("user@example.com","Tester","validpassword123"));
    verify(realAccounts,never()).insert(anyString(),anyString(),anyString());
  }
  @Test void existingAccountBlocksNewPendingSignup() {
    AccountService accounts=mock(AccountService.class);
    AccountRepository realAccounts=mock(AccountRepository.class);
    PendingSignupRepository pending=mock(PendingSignupRepository.class);
    ResendEmailSender sender=mock(ResendEmailSender.class);
    when(accounts.encodePendingPassword(anyString())).thenReturn("bcrypt-hash");
    when(realAccounts.findByEmail(anyString())).thenReturn(Optional.of(mock(Account.class)));
    assertThrows(ResponseStatusException.class,()->
      new SignupService(accounts,realAccounts,pending,sender).request("user@example.com","Tester","validpassword123"));
    verifyNoInteractions(pending,sender);
  }
}
