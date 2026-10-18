package dev.signalbridge.api.watches;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemberDiscordService {
  // An exact HTTPS Discord webhook URL; reject arbitrary hosts (SSRF prevention).
  private static final Pattern DISCORD_URL=Pattern.compile(
    "https://discord\\.com/api/webhooks/[0-9]{15,25}/[A-Za-z0-9_-]{30,180}");
  private final MemberDiscordRepository repository;
  private final DiscordWebhookCipher cipher;
  private final HttpClient http;
  public MemberDiscordService(MemberDiscordRepository repository,DiscordWebhookCipher cipher) {
    this.repository=repository;this.cipher=cipher;
    this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
      .followRedirects(HttpClient.Redirect.NEVER).build();
  }
  public record Status(boolean configured, Instant connectedAt) {}
  public Status get(long userId) {
    Instant at=repository.activatedAt(userId);
    return new Status(at!=null,at);
  }
  public Status connect(long userId,String raw) {
    String url=raw==null?"":raw.strip();
    if(!DISCORD_URL.matcher(url).matches())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Discord 공식 웹훅 주소를 확인해 주세요.");
    // Fail closed when encryption key is missing; never store a plaintext URL.
    String encrypted=cipher.encrypt(url);
    String testPayload="{\"content\":\"SignalBridge Discord 채널 연결 테스트가 완료되었습니다.\",\"allowed_mentions\":{\"parse\":[]}}";
    try {
      HttpRequest request=HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(10)).header("Content-Type","application/json")
        .POST(HttpRequest.BodyPublishers.ofString(testPayload)).build();
      HttpResponse<Void> response=http.send(request,HttpResponse.BodyHandlers.discarding());
      if(response.statusCode()<200||response.statusCode()>=300)
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Discord 테스트 전송에 실패했습니다. 웹훅을 확인해 주세요.");
    } catch(IOException ex) {
      // Never expose an exception message: it may include the secret webhook URL.
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Discord에 연결할 수 없습니다.");
    } catch(InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Discord 연결이 중단되었습니다.");
    }
    repository.upsert(userId,encrypted);
    return get(userId);
  }
  public void disconnect(long userId) { repository.delete(userId); }
}
