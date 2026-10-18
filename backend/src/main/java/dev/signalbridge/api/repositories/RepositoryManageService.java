package dev.signalbridge.api.repositories;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepositoryManageService {
    private static final Pattern SLUG=Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9_.-]{1,100}");
    private final UserRepositoryDao dao;
    private final GitHubPublicVerifier github;
    public RepositoryManageService(UserRepositoryDao dao,GitHubPublicVerifier github){this.dao=dao;this.github=github;}
    public static String parseRepo(String raw) {
        if(raw==null || raw.length()>240)throw bad("GitHub 저장소 주소를 입력해 주세요.");
        String v=raw.strip();
        if(v.startsWith("https://")) {
            URI u;
            try {u=URI.create(v);}catch(IllegalArgumentException ex){throw bad("올바른 GitHub URL을 입력해 주세요.");}
            if(!"github.com".equalsIgnoreCase(u.getHost()) || u.getUserInfo()!=null || u.getPort()!=-1 || u.getRawQuery()!=null || u.getRawFragment()!=null)
                throw bad("github.com의 공개 저장소 URL만 사용할 수 있습니다.");
            if(u.getPath()==null) throw bad("저장소 경로가 없습니다.");
            v=u.getPath().replaceFirst("^/", "").replaceFirst("/$", "");
        }
        if(!SLUG.matcher(v).matches() || v.contains("..") || v.contains("--/") || v.split("/",-1)[1].startsWith("."))
            throw bad("저장소 주소는 https://github.com/owner/repo 형식이어야 합니다.");
        return v;
    }
    public List<UserRepositoryDao.Entry> list(long uid){return dao.list(uid);}
    public List<UserRepositoryDao.Entry> add(long uid,String input){
        String repo=parseRepo(input);
        if(dao.count(uid)>=20)throw new ResponseStatusException(HttpStatus.CONFLICT,"저장소는 최대 20개까지 등록할 수 있습니다.");
        github.verify(repo); // Public access is checked before any persistent write.
        dao.add(uid,repo);
        return dao.list(uid);
    }
    public void delete(long uid,long sourceId){
        if(sourceId<=0||!dao.delete(uid,sourceId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"등록한 저장소가 없습니다.");
    }
    private static ResponseStatusException bad(String msg){return new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
}
