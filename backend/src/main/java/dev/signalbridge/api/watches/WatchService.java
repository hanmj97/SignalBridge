package dev.signalbridge.api.watches;

import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WatchService {
  private final WatchRepository repository;
  public WatchService(WatchRepository repository){this.repository=repository;}
  public List<WatchDtos.WatchRule> rules(long uid){return repository.all(uid);}
  public List<WatchDtos.WatchEvent> events(long uid){return repository.recentEvents(uid);}
  @Transactional
  public WatchDtos.WatchRule create(long uid,WatchDtos.CreateWatch input){
    if(input==null) throw bad("입력 값이 없습니다.");
    String keyword=cleanKeyword(input.keyword());
    if(input.sourceId()!=null && (input.sourceId()<=0||!repository.sourceExists(uid,input.sourceId())))
      throw bad("존재하지 않거나 비활성화된 GitHub 소스입니다.");
    if(repository.countForUser(uid)>=30)throw new ResponseStatusException(HttpStatus.CONFLICT,"관심 규칙은 최대 30개입니다.");
    try {return repository.create(uid,keyword,input.sourceId());}
    catch(DuplicateKeyException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"같은 키워드와 소스 조합이 이미 있습니다.");}
  }
  public void setEnabled(long uid,long id,WatchDtos.SetEnabled input){
    if(input==null||input.enabled()==null) throw bad("enabled 값이 필요합니다.");
    if(id<=0||!repository.setEnabled(uid,id,input.enabled()))throw missing();
  }
  public void delete(long uid,long id){if(id<=0||!repository.delete(uid,id))throw missing();}
  static String cleanKeyword(String raw){
    if(raw==null)throw bad("키워드를 입력해 주세요.");String value=raw.strip();
    if(value.length()<2||value.length()>80||value.chars().anyMatch(Character::isISOControl))
      throw bad("키워드는 2~80자이며 제어 문자를 포함할 수 없습니다.");
    return value;
  }
  private static ResponseStatusException bad(String msg){return new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
  private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"관심 규칙을 찾지 못했습니다.");}
}
