import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import {addMyRepository,fetchMyRepositories,removeMyRepository} from './api';
import type {MyRepository} from './api';

export default function MyRepositoriesPanel({onBrowse,onChanged}: {
  onBrowse:(sourceId:number)=>void;onChanged:()=>void;
}) {
  const [url,setUrl]=useState('');
  const [items,setItems]=useState<MyRepository[]>([]);
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const [feedback,setFeedback]=useState('');
  async function reload(){const data=await fetchMyRepositories();setItems(data);}
  useEffect(()=>{let alive=true;
    fetchMyRepositories().then(data=>{if(alive)setItems(data);}).catch(err=>{if(alive)setError(err instanceof Error?err.message:'저장소 조회 실패');})
      .finally(()=>{if(alive)setLoading(false);});return()=>{alive=false;};
  },[]);
  async function submit(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(busy)return;setBusy(true);setError('');setFeedback('');
    try{await addMyRepository(url.trim());await reload();setUrl('');setFeedback('등록 완료. Airflow의 다음 GitHub 수집 후 릴리스가 표시됩니다.');onChanged();}
    catch(err:unknown){setError(err instanceof Error?err.message:'등록 실패');}
    finally{setBusy(false);}
  }
  async function remove(item:MyRepository){
    if(busy||!window.confirm(`${item.resource} 저장소 연결을 해제할까요? 수집된 공용 릴리스 기록은 삭제되지 않습니다.`))return;
    setBusy(true);setError('');setFeedback('');
    try{await removeMyRepository(item.sourceId);await reload();onChanged();setFeedback('내 저장소 목록에서 연결을 해제했습니다.');}
    catch(err:unknown){setError(err instanceof Error?err.message:'해제 실패');}
    finally{setBusy(false);}
  }
  return <section className="panel my-repos-panel">
    <div className="panel-title"><div><h2>내 GitHub 저장소</h2><p>원하는 공개 저장소 주소를 등록하면 릴리스와 변경 내용을 자동 추적합니다. 최대 20개.</p></div></div>
    <form className="repo-register-form" onSubmit={e=>void submit(e)}>
      <label htmlFor="github-repo-url">GitHub Repository URL</label>
      <div className="repo-register-controls"><input id="github-repo-url" value={url} onChange={e=>setUrl(e.target.value)} type="text"
        maxLength={240} placeholder="https://github.com/owner/repository" required autoComplete="off"/>
        <button type="submit" disabled={busy||!url.trim()}>{busy?'확인 중...':'저장소 등록'}</button></div>
    </form>
    {error&&<p className="notice-error" role="alert">{error}</p>}
    {feedback&&<p className="watch-success" role="status">{feedback}</p>}
    {loading?<p className="empty">저장소 목록 확인 중...</p>:items.length===0?<p className="empty">등록된 저장소가 없습니다. GitHub URL을 입력해 주세요.</p>:
      <div className="repo-list">{items.map(item=><div className="repo-list-row" key={item.sourceId}>
        <div><strong>{item.resource}</strong><small>릴리스 {item.recordCount}건 · 마지막 확인 {item.lastCheckedAt?new Date(item.lastCheckedAt).toLocaleString('ko-KR'):'수집 전'}</small></div>
        <div className="repo-row-actions"><button type="button" onClick={()=>onBrowse(item.sourceId)}>릴리스 보기</button>
          <button type="button" className="repo-disconnect" disabled={busy} onClick={()=>void remove(item)}>연결 해제</button></div>
      </div>)}</div>}
    <p className="ops-footnote">공개 GitHub Releases만 조회합니다. 커밋 변경 내역이나 GitHub 릴리스 게시·수정 기능은 제공하지 않습니다. 비공개 저장소는 지원하지 않습니다.</p>
  </section>;
}
