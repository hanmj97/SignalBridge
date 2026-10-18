package dev.signalbridge.api.opportunities;

import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OpportunityAlertService {
  private final OpportunityAlertRepository repo;
  public OpportunityAlertService(OpportunityAlertRepository repo){this.repo=repo;}
  public List<OpportunityAlertController.Rule> rules(long userId){return repo.rules(userId);}
  public List<OpportunityAlertController.Event> events(long userId){return repo.events(userId);}

  static String text(String value,int min,int max,String name){
    if(value==null||value.isBlank())return null;
    String trimmed=value.strip();
    if(trimmed.length()<min||trimmed.length()>max || trimmed.chars().anyMatch(Character::isISOControl))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,name+" 입력값을 확인해 주세요.");
    return trimmed;
  }
  @Transactional
  public OpportunityAlertController.Rule create(long userId,OpportunityAlertController.CreateRule input){
    if(input==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"관심 조건을 입력해 주세요.");
    String keyword=text(input.keyword(),2,80,"키워드");
    String kind=text(input.kind(),1,30,"정보 유형");
    String region=text(input.region(),1,40,"지역");
    Integer age=input.age();
    if(kind!=null&&!List.of("POLICY","BENEFIT","JOB","PROGRAM").contains(kind))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"지원하지 않는 정보 유형입니다.");
    if(region!=null&&!List.of("전국","서울","경기","인천","부산","대구","광주","대전","울산","세종","강원","충북","충남","전북","전남","경북","경남","제주").contains(region))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"지원하지 않는 지역입니다.");
    if(age!=null&&(age<0||age>100))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"나이는 0~100 사이로 입력하세요.");
    if(keyword==null&&kind==null&&region==null&&age==null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"하나 이상의 관심 조건을 입력해 주세요.");
    if(repo.count(userId)>=30)throw new ResponseStatusException(HttpStatus.CONFLICT,"관심 규칙은 최대 30개입니다.");
    try{return repo.create(userId,keyword,kind,region,age);}
    catch(DuplicateKeyException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 등록한 관심 조건입니다.");}
  }
  public void toggle(long userId,long id,OpportunityAlertController.ToggleRule input){
    if(input==null||input.enabled()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"enabled가 필요합니다.");
    if(id<=0||!repo.toggle(userId,id,input.enabled()))throw missing();
  }
  public void delete(long userId,long id){if(id<=0||!repo.delete(userId,id))throw missing();}
  private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"관심 규칙을 찾을 수 없습니다.");}
}
