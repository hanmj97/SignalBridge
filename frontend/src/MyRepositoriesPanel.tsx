import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import {
  addMyRepository, fetchMyRepositories, removeMyRepository,
  requestGitHubRefresh, fetchGitHubRefreshStatus,
} from './api';
import type { MyRepository, GitHubRefreshStatus } from './api';

const pending = (s:GitHubRefreshStatus | undefined) =>
  s?.status === 'PENDING' || s?.status === 'RUNNING';

function statusLabel(s:GitHubRefreshStatus | undefined) {
  if (!s) return '';
  return ({
    PENDING:'최신화 대기 중 (Airflow가 1분 간격 확인)',
    RUNNING:'GitHub 릴리스 수집 중...',
    SUCCESS:'최신화 완료',
    FAILED:'최신화 실패 · Airflow 수집 기록 확인',
  })[s.status];
}

export default function MyRepositoriesPanel({onBrowse,onChanged}: {
  onBrowse:(sourceId:number)=>void;onChanged:()=>void;
}) {
  const [url,setUrl]=useState('');
  const [items,setItems]=useState<MyRepository[]>([]);
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [refreshBusy,setRefreshBusy]=useState<number[]>([]);
  const [refreshStates,setRefreshStates]=useState<Record<number,GitHubRefreshStatus>>({});
  const [error,setError]=useState('');
  const [feedback,setFeedback]=useState('');

  async function reload(){
    const data=await fetchMyRepositories();
    setItems(data);
    // Restore queued/completed request state after a page reload.
    const result=await Promise.all(data.map(async item => {
      try{return [item.sourceId,await fetchGitHubRefreshStatus(item.sourceId)] as const;}
      catch{return [item.sourceId,null] as const;}
    }));
    setRefreshStates(prev=>{
      const next={...prev};
      result.forEach(([id,state])=>{if(state)next[id]=state;});
      return next;
    });
  }

  useEffect(()=>{
    let alive=true;
    fetchMyRepositories().then(async data=>{
      if(!alive)return;
      setItems(data);
      const states=await Promise.all(data.map(async item=>{
        try{return [item.sourceId,await fetchGitHubRefreshStatus(item.sourceId)] as const;}
        catch{return [item.sourceId,null] as const;}
      }));
      if(alive)setRefreshStates(Object.fromEntries(states.filter((entry):entry is readonly [number,GitHubRefreshStatus]=>entry[1]!==null)));
    }).catch(err=>{if(alive)setError(err instanceof Error?err.message:'저장소 조회 실패');})
      .finally(()=>{if(alive)setLoading(false);});
    return()=>{alive=false;};
  },[]);

  // Airflow processes queued requests asynchronously. Poll only while active.
  useEffect(()=>{
    const active=Object.entries(refreshStates)
      .filter(([,value])=>pending(value))
      .map(([key])=>Number(key));
    if(!active.length)return;
    let alive=true;
    const timer=window.setInterval(()=>{
      void Promise.all(active.map(async sourceId=>{
        try{return [sourceId,await fetchGitHubRefreshStatus(sourceId)] as const;}
        catch{return [sourceId,null] as const;}
      })).then(async updates=>{
        if(!alive)return;
        const valid=updates.filter((entry):entry is readonly [number,GitHubRefreshStatus]=>entry[1]!==null);
        setRefreshStates(previous=>{
          const next={...previous};
          valid.forEach(([id,state])=>{next[id]=state;});
          return next;
        });
        if(valid.some(([id,value])=>refreshStates[id]?.status!==value.status && value.status==='SUCCESS')){
          await reload();
          onChanged();
        }
      });
    },5000);
    return()=>{alive=false;window.clearInterval(timer);};
  },[refreshStates,onChanged]);

  async function submit(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(busy)return;setBusy(true);setError('');setFeedback('');
    try{
      await addMyRepository(url.trim());
      await reload();setUrl('');
      setFeedback('등록 완료. 기존 자동 수집 또는 지금 최신화 버튼으로 릴리스를 불러올 수 있어요.');
      onChanged();
    }catch(err:unknown){setError(err instanceof Error?err.message:'등록 실패');}
    finally{setBusy(false);}
  }

  async function remove(item:MyRepository){
    if(busy||!window.confirm(`${item.resource} 저장소 연결을 해제할까요? 수집된 공용 릴리스 기록은 삭제되지 않습니다.`))return;
    setBusy(true);setError('');setFeedback('');
    try{await removeMyRepository(item.sourceId);await reload();onChanged();setFeedback('내 저장소 목록에서 연결을 해제했습니다.');}
    catch(err:unknown){setError(err instanceof Error?err.message:'해제 실패');}
    finally{setBusy(false);}
  }

  async function refresh(item:MyRepository){
    if(refreshBusy.includes(item.sourceId) || pending(refreshStates[item.sourceId]))return;
    setRefreshBusy(prev=>[...prev,item.sourceId]);setError('');setFeedback('');
    try{
      const state=await requestGitHubRefresh(item.sourceId);
      setRefreshStates(prev=>({...prev,[item.sourceId]:state}));
      setFeedback('최신화 요청을 접수했어요. 자동으로 수집 상태를 확인합니다.');
    }catch(err:unknown){setError(err instanceof Error?err.message:'최신화 요청 실패');}
    finally{setRefreshBusy(prev=>prev.filter(id=>id!==item.sourceId));}
  }

  return <section className="panel my-repos-panel">
    <div className="panel-title"><div><h2>내 GitHub 저장소</h2><p>공개 저장소를 연결하면 30분마다 자동 수집하고, 버튼으로 추가 수집을 요청할 수 있어요. 최대 20개.</p></div></div>
    <form className="repo-register-form" onSubmit={e=>void submit(e)}>
      <label htmlFor="github-repo-url">GitHub Repository URL</label>
      <div className="repo-register-controls"><input id="github-repo-url" value={url} onChange={e=>setUrl(e.target.value)} type="text"
        maxLength={240} placeholder="https://github.com/owner/repository" required autoComplete="off"/>
        <button type="submit" disabled={busy||!url.trim()}>{busy?'확인 중...':'저장소 등록'}</button></div>
    </form>
    {error&&<p className="notice-error" role="alert">{error}</p>}
    {feedback&&<p className="watch-success" role="status">{feedback}</p>}
    {loading?<p className="empty">저장소 목록 확인 중...</p>:items.length===0?<p className="empty">등록된 저장소가 없습니다. GitHub URL을 입력해 주세요.</p>:
      <div className="repo-list">{items.map(item=>{
        const state=refreshStates[item.sourceId];
        return <div className="repo-list-row" key={item.sourceId}>
          <div><strong>{item.resource}</strong>
            <small>릴리스 {item.recordCount}건 · 마지막 확인 {item.lastCheckedAt?new Date(item.lastCheckedAt).toLocaleString('ko-KR'):'수집 전'}</small>
            {state&&<small role="status" aria-live="polite">{statusLabel(state)}</small>}
          </div>
          <div className="repo-row-actions">
            <button type="button" onClick={()=>onBrowse(item.sourceId)}>릴리스 보기</button>
            <button type="button" disabled={busy||refreshBusy.includes(item.sourceId)||pending(state)} onClick={()=>void refresh(item)}>
              {pending(state)?'수집 대기 중':'지금 최신화'}
            </button>
            <button type="button" className="repo-disconnect" disabled={busy} onClick={()=>void remove(item)}>연결 해제</button>
          </div>
        </div>;
      })}</div>}
    <p className="ops-footnote">수동 요청은 서버 큐에 저장되며 Airflow가 약 1분 간격으로 처리합니다. 같은 저장소의 연속 요청은 2분 이내 중복 등록되지 않습니다. 공개 GitHub Releases만 조회하며 GitHub 릴리스 생성·수정 기능은 포함되지 않습니다.</p>
  </section>;
}
