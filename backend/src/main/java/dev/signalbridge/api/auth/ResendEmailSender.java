package dev.signalbridge.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ResendEmailSender {
  private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper mapper;
  private final String apiKey;
  private final String from;
  private final String publicUrl;
  public ResendEmailSender(ObjectMapper mapper,
      @Value("${signalbridge.email.resend-api-key:}") String apiKey,
      @Value("${signalbridge.email.from:}") String from,
      @Value("${signalbridge.email.public-url:http://localhost:5173}") String publicUrl) {
    this.mapper=mapper;this.apiKey=apiKey;this.from=from;this.publicUrl=publicUrl;
  }
  public void sendVerification(String recipient,String token) {
    if(apiKey.isBlank() || from.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
      "인증 메일 서비스가 아직 설정되지 않았습니다.");
    String url=publicUrl.replaceAll("/+$", "")+"/?verifyEmail="+URLEncoder.encode(token,StandardCharsets.UTF_8);
    // Email-client-safe layout: separate table rows prevent inline CTA padding
    // from overlapping adjacent text (notably in Naver Mail and Outlook).
    String html="""
      <!DOCTYPE html>
      <html lang="ko">
      <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
      <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,'Malgun Gothic',sans-serif;color:#22324a">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;background-color:#f4f7fb">
          <tr><td align="center" style="padding:32px 16px">
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;border-collapse:collapse;background-color:#ffffff;border:1px solid #e4ebf5">
              <tr><td style="padding:32px 30px 0;text-align:left">
                <p style="margin:0 0 12px;font-size:12px;font-weight:bold;letter-spacing:2px;color:#3978ef">SIGNALBRIDGE</p>
                <h1 style="margin:0 0 16px;font-size:24px;line-height:1.4;color:#182b49">이메일 주소 인증</h1>
                <p style="margin:0;font-size:14px;line-height:1.8;color:#44546b">회원가입을 완료하려면 아래 버튼을 눌러 주세요.</p>
              </td></tr>
              <tr><td style="padding:26px 30px 28px;text-align:left">
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="border-collapse:separate">
                  <tr><td bgcolor="#3978ef" style="background-color:#3978ef;border-radius:8px;text-align:center">
                    <a href="%s" style="display:inline-block;padding:14px 24px;font-size:14px;font-weight:bold;line-height:20px;color:#ffffff;text-decoration:none">이메일 인증하기</a>
                  </td></tr>
                </table>
              </td></tr>
              <tr><td style="padding:0 30px 30px;text-align:left">
                <p style="margin:0 0 10px;font-size:13px;line-height:1.8;color:#64748b">인증 링크는 30분 동안 유효하며 한 번만 사용할 수 있습니다.</p>
                <p style="margin:0;font-size:13px;line-height:1.8;color:#64748b">가입을 요청하지 않았다면 이 메일을 무시해 주세요.</p>
              </td></tr>
            </table>
          </td></tr>
        </table>
      </body>
      </html>
      """.formatted(url);
    try {
      String body=mapper.writeValueAsString(Map.of(
        "from",from,"to",List.of(recipient),"subject","[SignalBridge] 이메일 주소 인증",
        "html",html,"text","SignalBridge 이메일 인증 링크 (30분 유효): "+url));
      HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
        .timeout(Duration.ofSeconds(12))
        .header("Authorization","Bearer "+apiKey)
        .header("Content-Type","application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
      HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()<200 || response.statusCode()>=300)
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "인증 메일 발송에 실패했습니다. 잠시 후 다시 요청해 주세요.");
    } catch (ResponseStatusException ex) {throw ex;}
      catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"메일 발송이 중단되었습니다.");
      } catch (Exception ex) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"메일 서비스 연결에 실패했습니다.");
      }
  }

  // Phase 13: recovery mail reuses the verified Resend sender credentials.
  // Keep the existing verification email HTML completely unchanged.
  public void sendPasswordReset(String recipient,String token) {
    if(apiKey.isBlank() || from.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
      "메일 발송 서비스가 설정되지 않았습니다.");
    String url=publicUrl.replaceAll("/+$", "")+"/?resetPassword="+URLEncoder.encode(token,StandardCharsets.UTF_8);
    String html="""
      <!DOCTYPE html>
      <html lang="ko"><head><meta charset="UTF-8"></head>
      <body style="margin:0;padding:0;background:#f4f7fb;font-family:Arial,sans-serif;color:#22324a">
      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse">
        <tr><td align="center" style="padding:32px 16px">
          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;background:#ffffff;border:1px solid #e4ebf5">
            <tr><td style="padding:32px 30px 0">
              <p style="margin:0 0 12px;font-size:12px;font-weight:bold;letter-spacing:2px;color:#3978ef">SIGNALBRIDGE</p>
              <h1 style="margin:0 0 16px;font-size:24px;line-height:1.4;color:#182b49">비밀번호 재설정</h1>
              <p style="margin:0;font-size:14px;line-height:1.8;color:#44546b">비밀번호 재설정을 요청하셨다면 아래 버튼을 눌러 주세요.</p>
            </td></tr>
            <tr><td style="padding:26px 30px 28px">
              <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr><td bgcolor="#3978ef" style="background:#3978ef;border-radius:8px">
                <a href="%s" style="display:inline-block;padding:14px 24px;line-height:20px;font-weight:bold;color:#ffffff;text-decoration:none;font-size:14px">비밀번호 재설정하기</a>
              </td></tr></table>
            </td></tr>
            <tr><td style="padding:0 30px 30px">
              <p style="margin:0 0 10px;font-size:13px;line-height:1.8;color:#64748b">이 링크는 30분 동안 유효하고 한 번만 사용할 수 있습니다.</p>
              <p style="margin:0;font-size:13px;line-height:1.8;color:#64748b">요청하지 않으셨다면 메일을 무시해 주세요. 비밀번호는 변경되지 않습니다.</p>
            </td></tr>
          </table>
        </td></tr>
      </table></body></html>
      """.formatted(url);
    try {
      String body=mapper.writeValueAsString(Map.of(
        "from",from,"to",List.of(recipient),"subject","[SignalBridge] 비밀번호 재설정",
        "html",html,"text","SignalBridge 비밀번호 재설정 링크 (30분 유효): "+url));
      HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
        .timeout(Duration.ofSeconds(12)).header("Authorization","Bearer "+apiKey)
        .header("Content-Type","application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
      HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()<200 || response.statusCode()>=300)
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"재설정 메일 발송에 실패했습니다.");
    } catch(ResponseStatusException ex) {throw ex;}
      catch(InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"메일 발송이 중단되었습니다.");
      } catch(Exception ex) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"메일 서비스 연결에 실패했습니다.");
      }
  }

  // Phase 14: verify ownership of a NEW address before updating the existing member.
  public void sendEmailChange(String recipient,String token) {
    if(apiKey.isBlank() || from.isBlank())
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"메일 발송 서비스가 설정되지 않았습니다.");
    String url=publicUrl.replaceAll("/+$", "")+"/?changeEmail="+URLEncoder.encode(token,StandardCharsets.UTF_8);
    // Table-cell spacing is intentional for Naver/Outlook rendering compatibility.
    String html="""
      <!DOCTYPE html><html lang="ko"><head><meta charset="UTF-8"></head>
      <body style="margin:0;padding:0;background:#f4f7fb;font-family:Arial,sans-serif;color:#22324a">
      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse">
      <tr><td align="center" style="padding:32px 16px">
      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;background:#fff;border:1px solid #e4ebf5">
      <tr><td style="padding:32px 30px 0">
      <p style="margin:0 0 12px;font-size:12px;font-weight:bold;letter-spacing:2px;color:#3978ef">SIGNALBRIDGE</p>
      <h1 style="margin:0 0 16px;font-size:24px;line-height:1.4;color:#182b49">이메일 주소 변경 확인</h1>
      <p style="margin:0;font-size:14px;line-height:1.8;color:#44546b">새 이메일 주소로 변경하려면 아래 버튼을 눌러 주세요.</p>
      </td></tr>
      <tr><td style="padding:26px 30px 28px"><table role="presentation" cellpadding="0" cellspacing="0" border="0">
      <tr><td bgcolor="#3978ef" style="background:#3978ef;border-radius:8px">
      <a href="%s" style="display:inline-block;padding:14px 24px;line-height:20px;font-weight:bold;color:#fff;text-decoration:none;font-size:14px">새 이메일 인증하기</a>
      </td></tr></table></td></tr>
      <tr><td style="padding:0 30px 30px">
      <p style="margin:0 0 10px;font-size:13px;line-height:1.8;color:#64748b">링크 유효시간은 30분이며 한 번만 사용할 수 있습니다.</p>
      <p style="margin:0;font-size:13px;line-height:1.8;color:#64748b">변경을 요청하지 않았다면 메일을 무시해 주세요. 기존 주소는 유지됩니다.</p>
      </td></tr></table></td></tr></table></body></html>
      """.formatted(url);
    try {
      String body=mapper.writeValueAsString(Map.of(
        "from",from,"to",List.of(recipient),"subject","[SignalBridge] 새 이메일 주소 확인",
        "html",html,"text","새 이메일 변경 확인 링크 (30분 유효): "+url));
      HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
        .timeout(Duration.ofSeconds(12)).header("Authorization","Bearer "+apiKey)
        .header("Content-Type","application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
      HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()<200||response.statusCode()>=300)
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"새 주소 인증 메일 발송에 실패했습니다.");
    } catch(ResponseStatusException ex){throw ex;}
      catch(InterruptedException ex){Thread.currentThread().interrupt();
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"메일 발송이 중단되었습니다.");}
      catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"메일 서비스 연결에 실패했습니다.");}
  }
}
