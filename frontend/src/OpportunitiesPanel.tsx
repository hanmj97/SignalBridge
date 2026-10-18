import {useEffect, useState} from 'react';
import {createPortal} from 'react-dom';
import {fetchOpportunities, fetchOpportunity} from './api';
import OpportunityAlertsPanel from './OpportunityAlertsPanel';
import type {ApiPage, OpportunityDetail, OpportunityItem} from './api';

const kinds:Record<string,string>={POLICY:'청년정책',BENEFIT:'지원금·혜택',JOB:'채용공고',PROGRAM:'지원사업'};
const statuses:Record<string,string>={OPEN:'접수 중(출처 기준)',UPCOMING:'접수 예정(출처 기준)',CLOSED:'마감(출처 기준)',UNKNOWN:'접수 상태 미확인'};
const fieldNames:Record<string,string>={title:'제목',application_deadline:'신청 마감일',application_start:'신청 시작일',
  organization:'운영기관',region_codes:'지역',eligibility_summary:'신청 자격',benefit_summary:'지원 혜택',
  status:'접수 상태',age_min:'최소 연령',age_max:'최대 연령',category:'분야'};
function labelDate(s:string|null|undefined){return s ? s.replaceAll('-','.') : '정보 미제공';}
function trustedLink(raw:string):string|undefined{
  try {const u=new URL(raw);return u.protocol==='https:'?u.href:undefined;} catch {return undefined;}
}
function ageText(item:OpportunityItem){
  if(item.ageMin===null&&item.ageMax===null)return '연령 정보 미제공';
  return `${item.ageMin??'제한 미확인'} ~ ${item.ageMax??'제한 미확인'}세`;
}

export default function OpportunitiesPanel({q,onQueryChange,refreshVersion,loggedIn,onLogin}:{q:string;onQueryChange:(v:string)=>void;refreshVersion:number;loggedIn:boolean;onLogin:()=>void}){
  const [scope,setScope]=useState<'YOUTH'|'JOB'>('YOUTH');
  const [region,setRegion]=useState('');
  const [page,setPage]=useState(0);
  const [debounced,setDebounced]=useState(q.trim());
  const [result,setResult]=useState<ApiPage<OpportunityItem>|null>(null);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [selectedId,setSelectedId]=useState<number|null>(null);
  const [detail,setDetail]=useState<OpportunityDetail|null>(null);
  const [detailError,setDetailError]=useState('');
  const [detailLoading,setDetailLoading]=useState(false);
  useEffect(()=>{const t=window.setTimeout(()=>setDebounced(q.trim()),280);return()=>window.clearTimeout(t);},[q]);
  useEffect(()=>{setPage(0);},[debounced,scope,region]);
  useEffect(()=>{
    const c=new AbortController();setLoading(true);setError('');
    fetchOpportunities(page,debounced,scope,region,c.signal)
      .then(setResult)
      .catch((e:unknown)=>{if(!c.signal.aborted)setError(e instanceof Error?e.message:'공고 조회 실패');})
      .finally(()=>{if(!c.signal.aborted)setLoading(false);});
    return()=>c.abort();
  },[page,debounced,scope,region,refreshVersion]);
  useEffect(()=>{
    if(selectedId===null){setDetail(null);return;}
    const c=new AbortController();setDetail(null);setDetailError('');setDetailLoading(true);
    fetchOpportunity(selectedId,c.signal)
      .then(setDetail)
      .catch((e:unknown)=>{if(!c.signal.aborted)setDetailError(e instanceof Error?e.message:'상세 조회 실패');})
      .finally(()=>{if(!c.signal.aborted)setDetailLoading(false);});
    return()=>c.abort();
  },[selectedId]);
  useEffect(()=>{
    if(selectedId===null)return;
    const esc=(e:KeyboardEvent)=>{if(e.key==='Escape')setSelectedId(null);};
    document.addEventListener('keydown',esc);return()=>document.removeEventListener('keydown',esc);
  },[selectedId]);
  return <div className="opp-page">
    <section className="opp-hero">
      <span className="eyebrow">SIGNALBRIDGE / OPPORTUNITIES</span>
      <h1>청년에게 필요한 정보와 기회를 한곳에서.</h1>
      <p>2030 청년 지원정책과 채용공고만 모아 보고, 모집 조건과 마감 정보를 확인하세요.</p>
      <div className="opp-hero-chips"><span>2030 청년 지원정책</span><span>채용공고</span></div>
    </section>
    <div className="opp-demo-banner" role="note"><strong>V2 데이터 연동 실습 중</strong><span>가상 데모는 테스트 정보입니다. 실제 연동 자료도 출처 API에서 받은 항목만 표시하며, 링크가 데이터 제공 페이지인 경우 개별 공고 원문이 아닙니다. 지원 전 공식 기관의 최신 공고를 확인하세요.</span></div>
    <section className="panel opp-panel">
      <div className="opp-title"><div><h2>청년정책 · 채용 통합 검색</h2><p>총 {result?.totalElements??0}건 · 데이터가 제공되지 않은 조건은 추정하지 않습니다.</p></div><span className="opp-count">{loading?'조회 중':`${result?.items.length??0}건 표시`}</span></div>
      <div className="opp-scope-tabs" role="group" aria-label="정보 유형 선택">
        <button type="button" className={scope==='YOUTH'?'active':''} aria-pressed={scope==='YOUTH'} onClick={()=>{setSelectedId(null);setScope('YOUTH');}}>2030 청년 지원정책</button>
        <button type="button" className={scope==='JOB'?'active':''} aria-pressed={scope==='JOB'} onClick={()=>{setSelectedId(null);setScope('JOB');}}>채용공고</button>
      </div>
      <div className="opp-controls">
        <label>검색어<input value={q} maxLength={100} placeholder="정책명, 직무, 기관을 입력하세요" onChange={e=>onQueryChange(e.target.value)}/></label>
        <label>지역<select value={region} onChange={e=>setRegion(e.target.value)}><option value="">전체 지역</option>{['서울','경기','인천','전국'].map(v=><option key={v} value={v}>{v}</option>)}</select></label>
      </div>
      {error?<p role="alert" className="notice-error">{error} — V2-2 API 컨테이너 및 실습 DB 연결을 확인해 주세요.</p>:
        loading?<p className="empty">공고 정보를 불러오고 있습니다...</p>:
        result?.items.length===0?<p className="empty">조건에 맞는 데이터가 없습니다. 필터를 바꾸거나 API 연결 상태를 확인해 주세요.</p>:
        <div className="opp-list">{result?.items.map(item=><article key={item.id} className="opp-card">
          <div className="opp-card-head"><span className="opp-kind">{kinds[item.kind]??item.kind}</span>{item.isDemo?<span className="opp-demo-mark">가상 데모</span>:<span className="opp-kind">{item.sourceLabel}</span>}<span className="opp-status">{statuses[item.status]??'미확인'}</span></div>
          <button className="opp-card-title" onClick={()=>setSelectedId(item.id)}>{item.title}</button>
          <p className="opp-card-source">{item.organization??'기관 정보 미제공'} · {item.category??'분야 미분류'}</p>
          <div className="opp-card-meta"><span>지역: {item.regionCodes.join(', ')||'정보 미제공'}</span><span>{item.linkKind==='DATASET'?'종료일(출처)':'신청 마감'}: {labelDate(item.applicationDeadline)}</span><span>연령: {ageText(item)}</span></div>
          <button className="opp-card-more" onClick={()=>setSelectedId(item.id)}>상세 및 변경 이력 보기 →</button>
        </article>)}</div>}
      {result&&result.totalPages>1&&<div className="pagination"><button disabled={page===0} onClick={()=>setPage(v=>v-1)}>이전</button><span>{page+1} / {result.totalPages}</span><button disabled={page+1>=result.totalPages} onClick={()=>setPage(v=>v+1)}>다음</button></div>}
    </section>
    <div id="opportunity-alerts" className="opp-alerts-anchor"><OpportunityAlertsPanel loggedIn={loggedIn} onLogin={onLogin} refreshVersion={refreshVersion}/></div>
    {selectedId!==null && typeof document!=='undefined' && createPortal(<div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)setSelectedId(null);}}>
      <section className="detail-drawer" role="dialog" aria-modal="true" aria-label="정책 및 공고 상세 정보">
        <div className="drawer-head"><span>정책·채용공고 상세</span><button aria-label="닫기" onClick={()=>setSelectedId(null)}>✕</button></div>
        {detailLoading?<p className="empty opp-drawer-padding">불러오는 중...</p>:detailError?<p className="notice-error opp-drawer-padding">{detailError}</p>:detail&&<div className="drawer-body">
          <span className="opp-kind">{kinds[detail.item.kind]}</span>{detail.item.isDemo&&<div className="opp-demo-banner"><strong>가상 데모 공고</strong><span>실제 신청할 수 없는 테스트 정보입니다.</span></div>}
          <h2 className="opp-detail-title">{detail.item.title}</h2>
          <p className="opp-card-source">{detail.item.organization??'기관 정보 미제공'} · {detail.item.sourceLabel}</p>{detail.item.linkKind==='DATASET' && <p className="opp-demo-banner">개별 채용공고 원문 링크가 제공되지 않았습니다. 아래 링크는 공공데이터 제공 페이지이며, 최신 공고·지원 방법은 운영기관에서 확인해야 합니다.</p>}
          <div className="detail-facts"><div><small>지원 지역</small><strong>{detail.item.regionCodes.join(', ')||'미제공'}</strong></div><div><small>지원 연령</small><strong>{ageText(detail.item)}</strong></div><div><small>{detail.item.linkKind==='DATASET'?'시작일(출처)':'신청 시작'}</small><strong>{labelDate(detail.item.applicationStart)}</strong></div><div><small>{detail.item.linkKind==='DATASET'?'종료일(출처)':'신청 마감'}</small><strong>{labelDate(detail.item.applicationDeadline)}</strong></div><div><small>접수 상태</small><strong>{statuses[detail.item.status]}</strong></div><div><small>채용 형태</small><strong>{detail.item.employmentType??'해당 없음·미제공'}</strong></div></div>
          <h3>지원 조건</h3><p className="release-description">{detail.item.eligibilitySummary??'정보 미제공'}</p>
          <h3>지원 내용</h3><p className="release-description">{detail.item.benefitSummary??'정보 미제공'}</p>
          {(!detail.item.isDemo && trustedLink(detail.item.url))&&<a className="origin-link" href={trustedLink(detail.item.url)} target="_blank" rel="noopener noreferrer">{detail.item.linkKind==='DATASET'?'공공데이터 제공 페이지 ↗':'공식 원문으로 이동 ↗'}</a>}
          <section className="record-history-section"><h3>수집 변경 이력</h3><p className="record-history-note">최신 20개 버전 · 변경 감지 시점</p>
            {detail.history.length===0?<p className="empty">변경 이력이 없습니다.</p>:
              <ol className="record-history-timeline">{detail.history.map(h=><li key={h.version} className="record-history-entry"><div className="record-history-header"><strong>v{h.version}</strong><span className="tag tag-blue">{h.changeType==='NEW'?'최초 수집':'내용 변경'}</span></div><time>{new Date(h.detectedAt).toLocaleString('ko-KR')}</time><p>{h.changedFields.length?h.changedFields.map(f=>fieldNames[f]??f).join(', '):'변경 필드 정보 없음'}</p></li>)}</ol>}
          </section>
        </div>}
      </section></div>, document.body)}
  </div>;
}
