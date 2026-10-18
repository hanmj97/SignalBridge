package dev.signalbridge.api.watches;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** v1: Base64(iv[12] || AES-256-GCM ciphertext-with-tag), authenticated encryption. */
@Component
public class DiscordWebhookCipher {
  private final String keyEncoded;
  private final SecureRandom random = new SecureRandom();
  public DiscordWebhookCipher(@Value("${signalbridge.discord.encryption-key:}") String keyEncoded) {
    this.keyEncoded = keyEncoded;
  }
  private byte[] key() {
    try {
      byte[] raw=Base64.getDecoder().decode(keyEncoded);
      if(raw.length!=32) throw new IllegalArgumentException("32 bytes required");
      return raw;
    } catch(IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Discord 암호화 키가 올바르게 설정되지 않았습니다.");
    }
  }
  public String encrypt(String plaintext) {
    try {
      byte[] nonce = new byte[12]; random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key(),"AES"),new GCMParameterSpec(128,nonce));
      byte[] payload=cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] joined=new byte[nonce.length+payload.length];
      System.arraycopy(nonce,0,joined,0,nonce.length);
      System.arraycopy(payload,0,joined,nonce.length,payload.length);
      return "v1:"+Base64.getEncoder().encodeToString(joined);
    } catch(GeneralSecurityException ex) {
      throw new IllegalStateException("Discord 채널 암호화에 실패했습니다.");
    }
  }
}
