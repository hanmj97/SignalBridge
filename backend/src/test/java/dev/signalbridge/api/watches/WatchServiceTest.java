package dev.signalbridge.api.watches;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WatchServiceTest {
  @Test void preservesLiteralKeyword(){assertEquals("Docker%_",WatchService.cleanKeyword(" Docker%_ "));}
  @Test void checksKeywordBoundaries(){
    assertThrows(ResponseStatusException.class,()->WatchService.cleanKeyword(" "));
    assertThrows(ResponseStatusException.class,()->WatchService.cleanKeyword("x".repeat(81)));
  }
  @Test void invalidSourceDoesNotWrite(){
    WatchRepository repo=mock(WatchRepository.class);WatchService svc=new WatchService(repo);
    assertThrows(ResponseStatusException.class,()->svc.create(10,new WatchDtos.CreateWatch("Grafana",777L)));
    verify(repo,never()).create(anyLong(),anyString(),any());
  }
  @Test void rejectedNullEnabled(){
    WatchService svc=new WatchService(mock(WatchRepository.class));
    assertThrows(ResponseStatusException.class,()->svc.setEnabled(10,1,new WatchDtos.SetEnabled(null)));
  }
  @Test void createAlwaysUsesAuthenticatedOwner(){
    WatchRepository repo=mock(WatchRepository.class);WatchService svc=new WatchService(repo);
    svc.create(123L,new WatchDtos.CreateWatch("Grafana",null));
    verify(repo).create(123L,"Grafana",null);
  }
  @Test void otherUserCannotToggleOrDelete(){
    WatchRepository repo=mock(WatchRepository.class);WatchService svc=new WatchService(repo);
    assertThrows(ResponseStatusException.class,()->svc.setEnabled(200,12,new WatchDtos.SetEnabled(true)));
    assertThrows(ResponseStatusException.class,()->svc.delete(200,12));
    verify(repo).setEnabled(200,12,true);verify(repo).delete(200,12);
  }
}
