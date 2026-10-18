package dev.signalbridge.api.repositories;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RepositoryManageServiceTest {
    @Test void validPublicGitHubInputFormats() {
        assertEquals("grafana/grafana",RepositoryManageService.parseRepo("https://github.com/grafana/grafana"));
        assertEquals("microsoft/vscode",RepositoryManageService.parseRepo("microsoft/vscode"));
    }
    @Test void rejectsUnexpectedUrlForms() {
        for(String input:new String[]{"http://github.com/a/b","https://evil.com/a/b","https://github.com.evil.com/a/b",
            "https://github.com/a/b?x=1","https://github.com/a/b#tag","https://github.com/a/b/c","../other", "a/..", ""})
            assertThrows(ResponseStatusException.class,()->RepositoryManageService.parseRepo(input),input);
    }
    @Test void validatedBeforeNetworkAndThenStored() {
        UserRepositoryDao dao=mock(UserRepositoryDao.class);
        GitHubPublicVerifier verifier=mock(GitHubPublicVerifier.class);
        when(dao.count(7L)).thenReturn(0);
        when(dao.list(7L)).thenReturn(java.util.List.of());
        var svc=new RepositoryManageService(dao,verifier);
        svc.add(7,"https://github.com/grafana/grafana");
        var ordered=inOrder(dao,verifier);
        ordered.verify(dao).count(7L);
        ordered.verify(verifier).verify("grafana/grafana");
        ordered.verify(dao).add(7L,"grafana/grafana");
    }
}
