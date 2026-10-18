import {useCallback,useEffect,useState} from 'react';
import type {FormEvent} from 'react';
import {createOpportunityRule,deleteOpportunityRule,fetchOpportunityEvents,fetchOpportunityRules,toggleOpportunityRule} from './api';
import type {OpportunityAlertEvent,OpportunityAlertRule} from './api';

const labels:Record<string,string>={POLICY:'청년정책',BENEFIT:'지원금·혜택',JOB:'채용공고',PROGRAM:'지원사업'};
const regions=['전국','서울','경기','인천','부산','대구','광주','대전','울산','세종','강원','충북','충남','전북','전남','경북','경남','제주'];
const dt = (s:string|null) => s ? new Date(s).toLocaleString('ko-KR') : '전송 전';
export default function OpportunityAlertsPanel({loggedIn,onLogin,refreshVersion}:{loggedIn:boolean;onLogin:()=>void;refreshVersion:number}) {
  const [rules,setRules]=useState<OpportunityAlertRule[]>([]);
  const [events,setEvents]=useState<OpportunityAlertEvent[]>([]);
  const [keyword,setKeyword]=useState('');
  const [kind,setKind]=useState('');
  const [region,setRegion]=useState('');
  const [age,setAge]=useState('');
  const [busy,setBusy]=useState(false);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [feedback,setFeedback]=useState('');
  const refresh=useCallback(async(signal?:AbortSignal)=>{
    try {
      const [a,b]=await Promise.all([fetchOpportunityRules(signal),fetchOpportunityEvents(signal)]);
      if(!signal?.aborted){setRules(a);setEvents(b);setError('');}
    } catch(e:unknown){if(!signal?.aborted)setError(e instanceof Error?e.message:'관심 알림 조회 실패');}
    finally {if(!signal?.aborted)setLoading(false);}
  },[]);
  useEffect(()=>{
    if(!loggedIn){setRules([]);setEvents([]);setLoading(false);return;}
    const ctrl=new AbortController();setLoading(true);void refresh(ctrl.signal);
    return()=>ctrl.abort();
  },[loggedIn,refresh,refreshVersion]);
  async function add(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(busy)return;
    setError('');setFeedback('');setBusy(true);
    try {
      await createOpportunityRule({keyword:keyword.trim()||null,kind:kind||null,region:region||null,age:age===''?null:Number(age)});
      setKeyword('');setKind('');setRegion('');setAge('');
      await refresh();setFeedback('관심 조건을 저장했어. 등록 이후 발생하는 새 공고 및 변경부터 검사해.');
    } catch(e:unknown){setError(e instanceof Error?e.message:'등록 실패');}
    finally {setBusy(false);}
  }
  async function setEnabled(rule:OpportunityAlertRule){
    if(busy)return;setBusy(true);setError('');setFeedback('');
    try {await toggleOpportunityRule(rule.id,!rule.enabled);await refresh();}
    catch(e:unknown){setError(e instanceof Error?e.message:'변경 실패');}finally{setBusy(false);}
  }
  async function remove(rule:OpportunityAlertRule){
    if(busy||!window.confirm('관심 조건을 삭제할까? 해당 조건의 이전 알림 기록도 삭제돼.'))return;
    setBusy(true);setError('');setFeedback('');
    try{await deleteOpportunityRule(rule.id);await refresh();}
    catch(e:unknown){setError(e instanceof Error?e.message:'삭제 실패');}finally{setBusy(false);}
  }
  return <section className="panel opp-sub-panel" aria-label="정책·채용 맞춤 알림">
    <div className="opp-title"><div><h2>내 정책·채용 관심 알림</h2><p>새 공고·내용 변경을 내 조건에 맞춰 확인하고 Discord로 알림받아.</p></div><span className="opp-count">V2 맞춤 구독</span></div>
    {!loggedIn ? <div className="opp-sub-empty"><p>관심 조건을 저장하려면 먼저 로그인해야 해. 공개 공고 조회는 로그인 없이 가능해.</p><button type="button" className="watch-submit" onClick={onLogin}>로그인 / 회원가입</button></div> : <>
      <p className="watch-caveat">가상 데모는 알림 전송 대상에서 제외돼. 공식 API에서 수집한 신규 공고의 알림은 별도 검증·활성화 이후에만 전송돼. Discord 웹훅은 기존 <strong>관심 알림</strong> 메뉴에서 한 번만 연결하면 돼.</p>
      <form className="opp-sub-form" onSubmit={add}>
        <label>관심 키워드<input value={keyword} onChange={e=>setKeyword(e.target.value)} minLength={keyword?2:undefined} maxLength={80} placeholder="예: DBA, 청년월세 (선택)"/></label>
        <label>정보 유형<select value={kind} onChange={e=>setKind(e.target.value)}><option value="">전체 유형</option>{Object.entries(labels).map(([v,t])=><option value={v} key={v}>{t}</option>)}</select></label>
        <label>지역<select value={region} onChange={e=>setRegion(e.target.value)}><option value="">전체 지역</option>{regions.map(v=><option key={v} value={v}>{v}</option>)}</select></label>
        <label>나이(선택)<input type="number" inputMode="numeric" min={0} max={100} value={age} onChange={e=>setAge(e.target.value)} placeholder="예: 30"/></label>
        <button type="submit" className="watch-submit" disabled={busy||rules.length>=30||!keyword.trim()&&!kind&&!region&&!age}>{busy?'저장 중...':'+ 관심 조건 등록'}</button>
      </form>
      <p className="opp-sub-tip">지역 ‘경기’는 ‘경기’ 또는 ‘전국’ 표시가 있는 공고에 매칭돼. 나이를 설정하면 연령 범위가 명시된 공고만 매칭해. 관심 조건끼리는 AND로 결합돼.</p>
      {error&&<p className="notice-error" role="alert">{error}</p>}
      {feedback&&<p className="watch-success" role="status">{feedback}</p>}
      <h3>등록한 관심 조건 <small>{rules.length}/30개</small></h3>
      {loading?<p className="empty">조회 중...</p>:rules.length===0?<p className="empty">아직 저장한 관심 조건이 없어.</p>:
        <div className="opp-sub-list">{rules.map(r=><div className="opp-sub-entry" key={r.id}>
          <div><strong>{[r.keyword,labels[r.kind??''],r.region,r.age===null?null:`${r.age}세`].filter(Boolean).join(' · ')}</strong><small>{dt(r.createdAt)} 등록 · {r.enabled?'사용 중':'일시 중지'}</small></div>
          <div className="opp-sub-actions"><button type="button" disabled={busy} onClick={()=>void setEnabled(r)}>{r.enabled?'중지':'재개'}</button><button type="button" disabled={busy} onClick={()=>void remove(r)}>삭제</button></div>
        </div>)}</div>}
      <div className="opp-sub-events-head"><h3>최근 정책·채용 알림</h3><button type="button" disabled={busy} onClick={()=>void refresh()}>새로고침</button></div>
      {events.length===0?<p className="empty">아직 알림이 없어. 새 실제 데이터가 수집되어야 기록돼.</p>:
        <div className="opp-sub-list">{events.map(e=><div className="opp-sub-entry" key={e.id}>
          <div><strong>{e.title}</strong><small>{e.sourceLabel} · {e.changeType==='NEW'?'신규':'내용 변경'} · {dt(e.detectedAt)}</small></div>
          <span className="opp-sub-status">{e.status==='SENT'?'Discord 전송':e.status==='FAILED'?'전송 실패':'미전송'}</span>
        </div>)}</div>}
      <p className="opp-sub-tip">알림의 SENT는 Discord가 2xx 응답을 보낸 상태야. 기기에서 실제 알림이 보였는지는 확인할 수 없어.</p>
    </>}
  </section>;
}
