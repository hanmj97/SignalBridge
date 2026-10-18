package dev.signalbridge.api.repositories;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GitHubPublicVerifier {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper=new ObjectMapper();
    public void verify(String slug) {
        try {
            // Untrusted input is strictly validated in RepositoryManageService before URI construction.
            var request=HttpRequest.newBuilder(URI.create("https://api.github.com/repos/"+slug))
                .timeout(Duration.ofSeconds(10))
                .header("Accept","application/vnd.github+json")
                .header("User-Agent","SignalBridge-Public-Repo-Registry")
                .GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==404)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"공개 GitHub 저장소를 찾을 수 없습니다.");
            if(response.statusCode()==403 || response.statusCode()==429)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"GitHub 요청 한도가 초과됐습니다. 잠시 후 재시도해 주세요.");
            if(response.statusCode()!=200)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"GitHub 확인에 실패했습니다.");
            JsonNode data=mapper.readTree(response.body());
            if(data.path("private").asBoolean(true) || !slug.equalsIgnoreCase(data.path("full_name").asText()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"공개 저장소 주소를 확인해 주세요.");
        }catch(ResponseStatusException ex){throw ex;}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"GitHub 연결이 중단됐습니다.");}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"GitHub 연결에 실패했습니다.");}
    }
}
