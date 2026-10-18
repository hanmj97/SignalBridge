package dev.signalbridge.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AccountProfileServiceTest {
  private AccountProfileRepository repository;
  private PasswordEncoder encoder;
  private AccountProfileService service;
  @BeforeEach void setup() {
    repository=mock(AccountProfileRepository.class);
    encoder=new BCryptPasswordEncoder(4); // Faster bcrypt for unit tests only.
    service=new AccountProfileService(repository,encoder);
  }

  @Test void wrongCurrentPasswordRejectsWithoutWrite() {
    when(repository.findCredentials(7)).thenReturn(Optional.of(
      new AccountProfileRepository.Credentials(encoder.encode("CorrectPassword2026!"))));
    ResponseStatusException ex=assertThrows(ResponseStatusException.class,
      ()->service.changePassword(7,"IncorrectPassword!","NewPassword2026!"));
    assertEquals(HttpStatus.BAD_REQUEST,ex.getStatusCode());
    verify(repository,never()).replacePassword(anyLong(),anyString(),anyString());
  }

  @Test void reusedPasswordRejectsWithoutWrite() {
    String original="CorrectPassword2026!";
    when(repository.findCredentials(7)).thenReturn(Optional.of(
      new AccountProfileRepository.Credentials(encoder.encode(original))));
    assertThrows(ResponseStatusException.class,()->service.changePassword(7,original,original));
    verify(repository,never()).replacePassword(anyLong(),anyString(),anyString());
  }

  @Test void newPasswordIsEncodedAndUpdatedAtomically() {
    String original="CorrectPassword2026!";
    String next="NewPassword2026!";
    String oldHash=encoder.encode(original);
    when(repository.findCredentials(7)).thenReturn(Optional.of(new AccountProfileRepository.Credentials(oldHash)));
    when(repository.replacePassword(eq(7L),eq(oldHash),anyString())).thenAnswer(call->{
      String hashed=(String)call.getArgument(2);
      assertNotEquals(next,hashed);
      assertTrue(encoder.matches(next,hashed));
      return true;
    });
    assertDoesNotThrow(()->service.changePassword(7,original,next));
    verify(repository).replacePassword(eq(7L),eq(oldHash),anyString());
  }

  @Test void invalidNicknameRejectedBeforeDatabaseWrite() {
    assertThrows(ResponseStatusException.class,()->service.updateNickname(7,"a"));
    verify(repository,never()).updateNickname(anyLong(),anyString());
  }
}
