package dev.signalbridge.api.watches;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberDiscordServiceTest {
  @Test void requiresOfficialDiscordWebhookHost() {
    MemberDiscordRepository repo=mock(MemberDiscordRepository.class);
    DiscordWebhookCipher cipher=mock(DiscordWebhookCipher.class);
    MemberDiscordService service=new MemberDiscordService(repo,cipher);
    assertThrows(ResponseStatusException.class,()->service.connect(9L,
      "https://discord.com.evil.example/api/webhooks/123456789012345678/"+"x".repeat(70)));
    verifyNoInteractions(repo,cipher);
  }
  @Test void noPlaintextStorageAndRandomizedEncryption() {
    String key=Base64.getEncoder().encodeToString(new byte[32]);
    DiscordWebhookCipher cipher=new DiscordWebhookCipher(key);
    String secret="https://discord.com/api/webhooks/123456789012345678/"+"x".repeat(70);
    String a=cipher.encrypt(secret), b=cipher.encrypt(secret);
    assertTrue(a.startsWith("v1:"));
    assertNotEquals(a,b);
    assertFalse(a.contains(secret));
  }
  @Test void refusesMissingEncryptionKey() {
    assertThrows(ResponseStatusException.class,
      ()->new DiscordWebhookCipher("").encrypt("https://discord.com/api/webhooks/123/xyz"));
  }
}
