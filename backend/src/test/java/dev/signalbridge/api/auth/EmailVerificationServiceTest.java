package dev.signalbridge.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EmailVerificationServiceTest {
  @Test void tokenIsHashedAndNeverGivenToRepository() {
    EmailVerificationRepository repo=mock(EmailVerificationRepository.class);
    ResendEmailSender sender=mock(ResendEmailSender.class);
    PendingSignupRepository pending=mock(PendingSignupRepository.class);
    // New registration uses SignupService; this verifies compatibility for old accounts.
    when(repo.reserve(eq("member@example.com"),anyString()))
      .thenReturn(Optional.of(new EmailVerificationRepository.Delivery(7,"member@example.com")));
    var service=new EmailVerificationService(repo,sender,pending,mock(AccountRepository.class));
    service.sendForNewAccount("member@example.com");
    var hash=org.mockito.ArgumentCaptor.forClass(String.class);
    verify(repo).reserve(eq("member@example.com"),hash.capture());
    assertTrue(hash.getValue().matches("[a-f0-9]{64}"));
    verify(sender).sendVerification(eq("member@example.com"),matches("[A-Za-z0-9_-]{43}"));
  }
  @Test void malformedTokenIsRejectedBeforeDatabase() {
    var repo=mock(EmailVerificationRepository.class);
    var service=new EmailVerificationService(repo,mock(ResendEmailSender.class),mock(PendingSignupRepository.class),mock(AccountRepository.class));
    assertThrows(ResponseStatusException.class,()->service.verify("bad"));
    verifyNoInteractions(repo);
  }
  @Test void expiredTokenIsRejected() {
    var repo=mock(EmailVerificationRepository.class);
    when(repo.consumeAndVerify(anyString())).thenReturn(false);
    var service=new EmailVerificationService(repo,mock(ResendEmailSender.class),mock(PendingSignupRepository.class),mock(AccountRepository.class));
    assertThrows(ResponseStatusException.class,()->service.verify("A".repeat(43)));
  }
  @Test void unknownAddressUsesGenericPath() {
    var repo=mock(EmailVerificationRepository.class);
    var service=new EmailVerificationService(repo,mock(ResendEmailSender.class),mock(PendingSignupRepository.class),mock(AccountRepository.class));
    assertDoesNotThrow(()->service.resendIfEligible(null));
    assertDoesNotThrow(()->service.resendIfEligible("not-an-email"));
    verifyNoInteractions(repo);
  }
  @Test void verifiedPendingTokenCreatesARealAccount() {
    var repo=mock(EmailVerificationRepository.class);
    var pending=mock(PendingSignupRepository.class);
    var accounts=mock(AccountRepository.class);
    var service=new EmailVerificationService(repo,mock(ResendEmailSender.class),pending,accounts);
    when(pending.consume(anyString())).thenReturn(Optional.of(
      new PendingSignupRepository.Confirmed("person@example.com","Tester","bcrypt-hash")));
    service.verify("A".repeat(43));
    verify(accounts).insertVerified("person@example.com","Tester","bcrypt-hash");
    verify(repo,never()).consumeAndVerify(anyString());
  }

}
