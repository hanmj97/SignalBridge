import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { fetchChanges, fetchRecord, fetchRecordHistory, fetchRecords, fetchSources, fetchMyRecords, fetchMyChanges, fetchMySources, fetchMyEngineeringDashboard } from './api';
import type { ApiPage, ChangeItem, RecordDetail, RecordItem, Source, EngineeringDashboardSummary } from './api';
import OperationsPanel from './OperationsPanel';
import MyRepositoriesPanel from './MyRepositoriesPanel';
import OpportunitiesPanel from './OpportunitiesPanel';
import WatchlistPanel from './WatchlistPanel';
import AuthPanel from './AuthPanel';
import AccountPanel from './AccountPanel';
import EmailChangeConfirm from './EmailChangeConfirm';
import {currentMember,logoutMember} from './api';
import type {Member} from './api';

type View = 'opportunities' | 'repositories' | 'dashboard' | 'records' | 'changes' | 'sources' | 'health' | 'watches' | 'account';
type IconName = 'home'|'list'|'history'|'database'|'activity'|'search'|'bell'|'arrow'|'external'|'clock'|'check'|'refresh'|'layers'|'close'|'bookmark'|'filter'|'chevron'|'menu'|'monitor'|'info'|'sparkles';

function Icon({ name, size = 19, strokeWidth = 1.9 }: {name: IconName; size?: number; strokeWidth?: number}) {
  const paths: Record<IconName, ReactNode> = {
    home: <><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/></>,
    list: <><rect x="4" y="3" width="16" height="18" rx="3"/><path d="M8 8h8M8 12h8M8 16h5"/></>,
    history: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l4 2"/></>,
    database: <><ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 4 16 4 16 0V5M4 12c0 4 16 4 16 0"/></>,
    activity: <><path d="M3 12h4l3-7 4 14 3-7h4"/></>,
    search: <><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 5 5"/></>,
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></>,
    arrow: <><path d="M4 12h16m-6-6 6 6-6 6"/></>,
    external: <><path d="M14 4h6v6M20 4l-9 9"/><path d="M19 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h5"/></>,
    clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l4 2"/></>,
    check: <><circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/></>,
    refresh: <><path d="M20 7v5h-5M4 17v-5h5"/><path d="M5 9a8 8 0 0 1 14-3l1 6M19 15a8 8 0 0 1-14 3l-1-6"/></>,
    layers: <><rect x="4" y="4" width="16" height="16" rx="3"/><path d="M8 9h8M8 13h8"/></>,
    close: <><path d="M5 5l14 14M19 5 5 19"/></>,
    bookmark: <><path d="M6 3h12v18l-6-4-6 4z"/></>,
    filter: <><path d="M3 6h18M6 12h12M10 18h4"/></>,
    chevron: <><path d="m9 5 7 7-7 7"/></>,
    menu: <><path d="M4 7h16M4 12h16M4 17h16"/></>,
    monitor: <><rect x="3" y="4" width="18" height="14" rx="2"/><path d="M8 21h8M12 18v3"/></>,
    info: <><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></>,
    sparkles: <><path d="m12 3 2.2 6.8L21 12l-6.8 2.2L12 21l-2.2-6.8L3 12l6.8-2.2z"/></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}

function SourceLogo({name, large=false}:{name:string;large?:boolean}) {
  const label=name.toLowerCase();
  const kind=label.includes('docker')?'docker':label.includes('grafana')?'grafana':label.includes('visual studio')||label.includes('code')?'vscode':label.includes('github')?'github':'generic';
  const logo = kind==='vscode' ? <svg viewBox="0 0 32 32" width="25" height="25" aria-hidden="true"><path fill="#007ACC" d="M23 2 9 14l-5-4-3 2v8l3 2 5-4 14 12 7-3V5z"/><path fill="#43A6E9" d="M23 2v28l7-3V5z"/><path fill="#fff" opacity=".9" d="m5 15 8 7 3-3-8-7z"/></svg>
    :kind==='docker' ? <svg viewBox="0 0 32 32" width="26" height="26" aria-hidden="true"><g fill="#2189D3"><rect x="3" y="12" width="5" height="5" rx="1"/><rect x="9" y="12" width="5" height="5" rx="1"/><rect x="15" y="12" width="5" height="5" rx="1"/><rect x="9" y="6" width="5" height="5" rx="1"/><rect x="15" y="6" width="5" height="5" rx="1"/><rect x="21" y="12" width="5" height="5" rx="1"/><path d="M1 18c1 7 6 11 14 11 8 0 13-4 16-13-3 2-6 2-9 0-1 2-4 2-8 2z"/></g></svg>
    :kind==='grafana' ? <svg viewBox="0 0 32 32" width="27" height="27" aria-hidden="true"><path fill="#F66B18" d="M16 1c3 5 6 7 11 8-2 4-1 7 4 9-5 3-7 6-7 11-5-3-8-2-12 2-1-5-4-7-11-8 4-4 5-7 2-12 5 0 8-3 13-10z"/><circle cx="16" cy="17" r="8" fill="#FFAB24"/><circle cx="16" cy="17" r="5" fill="white"/><path d="M14 18c1-4 7-4 7 0 0 4-6 6-9 2" fill="none" stroke="#F66B18" strokeWidth="2.5" strokeLinecap="round"/></svg>
    :kind==='github' ? <svg viewBox="0 0 32 32" width="27" height="27" aria-hidden="true"><circle cx="16" cy="16" r="14" fill="#202b3e"/><path d="m8 11 1-5 5 3 2-1 2 1 5-3 1 5c3 5 1 12-8 12S5 16 8 11z" fill="white"/><circle cx="12.7" cy="15" r="1" fill="#202b3e"/><circle cx="19.3" cy="15" r="1" fill="#202b3e"/></svg>
    :<span>{name.slice(0,1)}</span>;
  return <span aria-hidden="true" className={`product-logo product-logo--${kind}${large?' product-logo--large':''}`}>{logo}</span>;
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR',{dateStyle:'medium',timeStyle:'short',timeZone:'Asia/Seoul'});
const dayFormatter = new Intl.DateTimeFormat('ko-KR',{month:'2-digit',day:'2-digit',timeZone:'Asia/Seoul'});
function formatDate(raw?:string|null) {
  if(!raw) return '기록 없음';
  const d=new Date(raw); return Number.isNaN(d.getTime())?'기록 없음':dateFormatter.format(d);
}
function relativeDate(raw?:string|null) {
  if(!raw) return '기록 없음';
  const distance=Date.now()-new Date(raw).getTime();
  if(distance<0) return formatDate(raw);
  if(distance<60_000) return '방금 전';
  if(distance<3_600_000) return `${Math.floor(distance/60_000)}분 전`;
  if(distance<86_400_000) return `${Math.floor(distance/3_600_000)}시간 전`;
  return formatDate(raw);
}
function dayKey(raw:string){
  const d=new Date(raw);
  if(Number.isNaN(d.getTime()))return '';
  return new Intl.DateTimeFormat('en-CA',{year:'numeric',month:'2-digit',day:'2-digit',timeZone:'Asia/Seoul'}).format(d);
}
function safeLink(url:string){try{const u=new URL(url);return ['http:','https:'].includes(u.protocol)?u.href:undefined;}catch{return undefined;}}
// 메뉴는 사용자 서비스 / 기술 화면 / 계정 관리로 그룹화합니다.
// 기존 View를 그대로 재사용해 백엔드나 화면 라우팅을 변경하지 않습니다.
type SidebarGroupId = 'service' | 'engineering' | 'account';
type SidebarItemId = View | 'opportunity-alerts';
type SidebarItem = {id:SidebarItemId;label:string;icon:IconName};
const sidebarGroups:{id:SidebarGroupId;label:string;icon:IconName;items:SidebarItem[]}[]=[
  {id:'service',label:'청년정책 · 채용',icon:'home',items:[
    {id:'opportunities',label:'정책·채용 통합 검색',icon:'search'},
    {id:'opportunity-alerts',label:'관심 정책·채용 알림',icon:'bell'},
  ]},
  {id:'engineering',label:'Engineering',icon:'monitor',items:[
    {id:'dashboard',label:'릴리스 대시보드',icon:'monitor'},
    {id:'repositories',label:'내 GitHub 저장소',icon:'database'},
    {id:'records',label:'개발 릴리스 목록',icon:'list'},
    {id:'changes',label:'변경 이력',icon:'history'},
    {id:'sources',label:'데이터 소스',icon:'database'},
    {id:'health',label:'시스템 상태',icon:'activity'},
    {id:'watches',label:'릴리스 관심 알림',icon:'bell'},
  ]},
  {id:'account',label:'내 계정',icon:'info',items:[
    {id:'account',label:'마이페이지',icon:'info'},
  ]},
];
function groupForView(v:View):SidebarGroupId{
  if(v==='account')return 'account';
  return v==='opportunities'?'service':'engineering';
}

export default function App(){
  const [view,setView]=useState<View>(()=>{const qs=new URLSearchParams(window.location.search);return qs.has('verifyEmail')||qs.has('resetPassword')?'watches':'opportunities';});
  // A recovery link must open the password form even if another session is still signed in.
  const [emailChangeToken,setEmailChangeToken]=useState(()=>new URLSearchParams(window.location.search).get('changeEmail'));
  const [recoveryEntry,setRecoveryEntry]=useState(()=>new URLSearchParams(window.location.search).has('resetPassword'));
  const [member,setMember]=useState<Member|null>(null);
  const [authReady,setAuthReady]=useState(false);
  const [authError,setAuthError]=useState('');
  useEffect(()=>{currentMember().then(setMember).catch((e:unknown)=>setAuthError(e instanceof Error?e.message:'계정 상태 확인 실패')).finally(()=>setAuthReady(true));},[]);
  async function signOut(){try{await logoutMember();setMember(null);setView('opportunities');setAuthError('');}
    catch(e:unknown){setAuthError(e instanceof Error?e.message:'로그아웃 실패');}}
  const [mobileMenu,setMobileMenu]=useState(false);
  const [expandedGroups,setExpandedGroups]=useState<SidebarGroupId[]>(()=>[
    new URLSearchParams(window.location.search).has('verifyEmail')||
    new URLSearchParams(window.location.search).has('resetPassword')?'engineering':'service'
  ]);
  const [opportunitySection,setOpportunitySection]=useState<'search'|'alerts'>('search');
  const [opportunityScrollRequest,setOpportunityScrollRequest]=useState(0);
  // 계정/이메일 확인 후 내부 view가 바뀌어도 해당 그룹을 자동으로 펼칩니다.
  useEffect(()=>{
    const group=groupForView(view);
    setExpandedGroups(current=>current.includes(group)?current:[...current,group]);
  },[view]);
  // React 화면 전환이 완료된 다음 관심 알림 영역으로 이동합니다.
  useEffect(()=>{
    if(view!=='opportunities'||opportunityScrollRequest===0)return;
    const frame=window.requestAnimationFrame(()=>{
      document.getElementById('opportunity-alerts')?.scrollIntoView({behavior:'smooth',block:'start'});
    });
    return()=>window.cancelAnimationFrame(frame);
  },[view,opportunityScrollRequest]);

  // A successful login must leave the recovery-only entry view and open the dashboard.
  function handleLogin(signedIn:Member){
    setMember(signedIn);
    setRecoveryEntry(false);
    setAuthError('');
    setView('opportunities');
    setMobileMenu(false);
    window.scrollTo({top:0,behavior:'auto'});
  }
  const [opQuery,setOpQuery]=useState('');
  const [query,setQuery]=useState('');
  const [search,setSearch]=useState('');
  const [sourceId,setSourceId]=useState<number|null>(null);
  const [page,setPage]=useState(0);
  const [refreshVersion,setRefreshVersion]=useState(0);
  const [sources,setSources]=useState<Source[]>([]);
  const [mySources,setMySources]=useState<Source[]>([]);
  const [engineeringDashboard,setEngineeringDashboard]=useState<EngineeringDashboardSummary|null>(null);
  const [engineeringDashboardError,setEngineeringDashboardError]=useState('');
  const [dashboardReleases,setDashboardReleases]=useState<RecordItem[]>([]);
  // When an authenticated member follows exactly one GitHub repo, use its
  // sourceId for queries even with "내 전체 저장소" selected. A source-specific
  // query is ordered by numeric release version (13.2.4 > 13.1.8 > 12.4.13).
  // With multiple repositories, the combined view remains ordered by publish date.
  const effectiveSourceId = sourceId ?? (member && mySources.length === 1 ? mySources[0].id : null);
  const [records,setRecords]=useState<ApiPage<RecordItem>|null>(null);
  const [historyPage,setHistoryPage]=useState(0);
  const [pagedChanges,setPagedChanges]=useState<ApiPage<ChangeItem>|null>(null);
  const [pagedChangesLoading,setPagedChangesLoading]=useState(false);
  const [pagedChangesError,setPagedChangesError]=useState('');
  const [sourcesError,setSourcesError]=useState('');
  const [recordsError,setRecordsError]=useState('');
  const [recordsLoading,setRecordsLoading]=useState(true);
  const [detailId,setDetailId]=useState<number|null>(null);
  const [detail,setDetail]=useState<RecordDetail|null>(null);
  const [detailError,setDetailError]=useState('');
  const [detailLoading,setDetailLoading]=useState(false);
  const [recordHistory,setRecordHistory]=useState<ChangeItem[]>([]);
  const [recordHistoryLoading,setRecordHistoryLoading]=useState(false);
  const [recordHistoryError,setRecordHistoryError]=useState('');
  const [favorites,setFavorites]=useState<number[]>([]);

  useEffect(()=>{const timer=window.setTimeout(()=>setSearch(query.trim()),300);return()=>window.clearTimeout(timer);},[query]);
  useEffect(()=>{setPage(0);},[search,effectiveSourceId]);
  useEffect(()=>{setSourceId(null);setPage(0);setHistoryPage(0);},[member?.id]);
  useEffect(()=>{
    if(!member){setMySources([]);return;}
    const c=new AbortController();fetchMySources(c.signal).then(setMySources).catch(()=>{if(!c.signal.aborted)setMySources([]);});
    return()=>c.abort();
  },[member?.id,refreshVersion]);

  useEffect(()=>{
    if(!member){setEngineeringDashboard(null);setEngineeringDashboardError('');return;}
    const c=new AbortController();
    setEngineeringDashboardError('');
    fetchMyEngineeringDashboard(c.signal)
      .then(setEngineeringDashboard)
      .catch((e:unknown)=>{if(!c.signal.aborted)setEngineeringDashboardError(e instanceof Error?e.message:'대시보드 조회 실패');});
    return()=>c.abort();
  },[member?.id,refreshVersion]);

  useEffect(()=>{
    if(!member||mySources.length===0){setDashboardReleases([]);return;}
    const c=new AbortController();
    Promise.all(mySources.map(src=>fetchMyRecords(0,'',src.id,c.signal)
      .then(page=>page.items[0]??null).catch(()=>null)))
      .then(rows=>{
        if(!c.signal.aborted)setDashboardReleases(rows.filter((v):v is RecordItem=>v!==null));
      });
    return()=>c.abort();
  },[member?.id,mySources,refreshVersion]);

  useEffect(()=>{
    const c=new AbortController();setSourcesError('');
    fetchSources(c.signal).then(setSources).catch((e:unknown)=>{if(!c.signal.aborted)setSourcesError(e instanceof Error?e.message:'소스 조회 실패');});
    return()=>c.abort();
  },[refreshVersion]);

  useEffect(()=>{
    const c=new AbortController();setRecordsError('');setRecordsLoading(true);
    (member ? fetchMyRecords(page,search,effectiveSourceId,c.signal) : fetchRecords(page,search,sourceId,c.signal)).then(setRecords).catch((e:unknown)=>{if(!c.signal.aborted)setRecordsError(e instanceof Error?e.message:'정보 조회 실패');}).finally(()=>{if(!c.signal.aborted)setRecordsLoading(false);});
    return()=>c.abort();
  },[page,search,sourceId,effectiveSourceId,refreshVersion,member?.id]);

  useEffect(()=>{
    if(view!=='changes')return;
    const c=new AbortController();setPagedChangesError('');setPagedChangesLoading(true);
    (member ? fetchMyChanges(historyPage,10,effectiveSourceId,c.signal) : fetchChanges(historyPage,10,sourceId,c.signal)).then(setPagedChanges).catch((e:unknown)=>{if(!c.signal.aborted)setPagedChangesError(e instanceof Error?e.message:'변경 이력 조회 실패');}).finally(()=>{if(!c.signal.aborted)setPagedChangesLoading(false);});
    return()=>c.abort();
  },[view,historyPage,sourceId,effectiveSourceId,refreshVersion,member?.id]);

  useEffect(()=>{
    if(detailId===null){setDetail(null);return;}
    const c=new AbortController();setDetail(null);setDetailError('');setDetailLoading(true);
    fetchRecord(detailId,c.signal).then(setDetail).catch((e:unknown)=>{if(!c.signal.aborted)setDetailError(e instanceof Error?e.message:'상세 정보 조회 실패');}).finally(()=>{if(!c.signal.aborted)setDetailLoading(false);});
    return()=>c.abort();
  },[detailId]);

  // History API is requested only when the detail drawer is opened.
  // Abort prevents stale results if the drawer closes or another record is selected.
  useEffect(()=>{
    if(detailId===null){setRecordHistory([]);setRecordHistoryError('');return;}
    const c=new AbortController();
    setRecordHistory([]);setRecordHistoryError('');setRecordHistoryLoading(true);
    fetchRecordHistory(detailId,c.signal)
      .then(setRecordHistory)
      .catch((e:unknown)=>{if(!c.signal.aborted)setRecordHistoryError(e instanceof Error?e.message:'변경 이력을 불러오지 못했습니다.');})
      .finally(()=>{if(!c.signal.aborted)setRecordHistoryLoading(false);});
    return()=>c.abort();
  },[detailId]);

  useEffect(()=>{
    if(detailId===null)return;
    const esc=(e:KeyboardEvent)=>{if(e.key==='Escape')setDetailId(null);};
    window.addEventListener('keydown',esc);return()=>window.removeEventListener('keydown',esc);
  },[detailId]);

  const dashboardSources=member?mySources:[];
  const totalRecords=member?(engineeringDashboard?.recordCount??0):0;
  const latestChecked=member&&engineeringDashboard?.latestCheckedAt
    ? Date.parse(engineeringDashboard.latestCheckedAt) : 0;
  const series=useMemo(()=>{
    const result:{key:string;label:string;NEW:number;UPDATED:number}[]=[];
    const today=new Date();
    const kstDay=new Intl.DateTimeFormat('en-CA',{year:'numeric',month:'2-digit',day:'2-digit',timeZone:'Asia/Seoul'}).format(today);
    const utcMidnight=new Date(`${kstDay}T00:00:00+09:00`).getTime();
    for(let n=6;n>=0;n--){
      const d=new Date(utcMidnight-n*86400000);
      result.push({key:dayKey(d.toISOString()),label:dayFormatter.format(d),NEW:0,UPDATED:0});
    }
    for(const d of engineeringDashboard?.days??[]){
      const match=result.find(x=>x.key===d.day);
      if(match){match.NEW=d.newCount;match.UPDATED=d.updatedCount;}
    }
    return result;
  },[engineeringDashboard]);
  const sevenDayEvents=engineeringDashboard?.changesLast7Days??0;
  const chartMax=Math.max(1,...series.map(s=>s.NEW+s.UPDATED));

  function navigate(to:View){
    setExpandedGroups(current=>current.includes(groupForView(to))?current:[...current,groupForView(to)]);
    if(to==='opportunities')setOpportunitySection('search');
    setView(to);setMobileMenu(false);
    if(to!=='records')setQuery('');
    setHistoryPage(0);
    window.scrollTo({top:0,behavior:'smooth'});
  }
  function navigateSidebar(to:SidebarItemId){
    if(to==='opportunity-alerts'){
      navigate('opportunities');
      setOpportunitySection('alerts');
      setOpportunityScrollRequest(v=>v+1);
    }else{
      navigate(to);
    }
  }
  function toggleGroup(id:SidebarGroupId){
    setExpandedGroups(current=>current.includes(id)?current.filter(group=>group!==id):[...current,id]);
  }
  function toggleFavorite(id:number){setFavorites(old=>old.includes(id)?old.filter(x=>x!==id):[...old,id]);}

  function recordRow(r:RecordItem){return <article key={r.id} className="release-row">
    <SourceLogo name={r.sourceLabel}/>
    <div className="release-copy"><button className="release-title" onClick={()=>setDetailId(r.id)}>{r.title}</button><span>{r.sourceLabel} · {r.tagName||'태그 없음'}</span></div>
    <span className="tag tag-blue">릴리스</span>
    <span className="release-time" title="GitHub 릴리스 게시일">{relativeDate(r.publishedAt??r.latestChangedAt??r.firstSeenAt)}</span>
    <button className={`star-button ${favorites.includes(r.id)?'saved':''}`} onClick={()=>toggleFavorite(r.id)} title="현재 페이지에서 관심 표시" aria-label="관심 표시"><Icon name="bookmark" size={17}/></button>
    <button className="small-icon-button" onClick={()=>setDetailId(r.id)} aria-label="릴리스 상세 보기"><Icon name="chevron" size={17}/></button>
  </article>;}
  function changeRow(c:ChangeItem){return <article key={c.id} className="release-row">
    <SourceLogo name={c.sourceLabel}/>
    <div className="release-copy"><button className="release-title" onClick={()=>setDetailId(c.recordId)}>{c.recordTitle}</button><span>{c.sourceLabel} · 변경 이력 v{c.version}</span></div>
    <span className={`tag ${c.changeType==='NEW'?'tag-blue':'tag-purple'}`}>{c.changeType==='NEW'?'신규':'수정'}</span>
    <span className="release-time" title="SignalBridge 변경 감지 시각">{relativeDate(c.detectedAt)}</span>
    <button className="small-icon-button" onClick={()=>setDetailId(c.recordId)} aria-label="변경 상세 보기"><Icon name="chevron" size={17}/></button>
  </article>;}

  return <div className="app-shell">
    <aside className={`sidebar ${mobileMenu?'sidebar-open':''}`}>
      <button className="brand" onClick={()=>navigate('opportunities')} aria-label="SignalBridge 메인으로 이동"><span className="brand-emblem"><Icon name="layers" size={22} strokeWidth={2.3}/></span><b>SignalBridge</b></button>
      <nav className="navigation sidebar-navigation" aria-label="메인 메뉴">
        {sidebarGroups.map(group=>{
          const expanded=expandedGroups.includes(group.id);
          const groupActive=groupForView(view)===group.id;
          return <section className="sidebar-group" key={group.id}>
            <button type="button" className={`sidebar-group-toggle ${groupActive?'group-active':''}`}
              onClick={()=>toggleGroup(group.id)} aria-expanded={expanded} aria-controls={`sidebar-group-${group.id}`}>
              <Icon name={group.icon} size={18}/>
              <span className="sidebar-group-label">{group.label}</span>
              <span className={`sidebar-group-chevron ${expanded?'expanded':''}`}><Icon name="chevron" size={15}/></span>
            </button>
            <div id={`sidebar-group-${group.id}`} className="sidebar-group-children" hidden={!expanded}>
              {group.items.map(item=>{
                const active=item.id==='opportunity-alerts'
                  ? view==='opportunities'&&opportunitySection==='alerts'
                  : view===item.id&&(item.id!=='opportunities'||opportunitySection==='search');
                return <button key={item.id} type="button"
                  className={`nav-button sidebar-child-button ${active?'active':''}`}
                  aria-current={active?'page':undefined}
                  onClick={()=>navigateSidebar(item.id)}>
                  <Icon name={item.icon} size={16}/><span>{item.label}</span>
                </button>;
              })}
            </div>
          </section>;
        })}
      </nav>
      <div className="sidebar-bottom"><div className="plan-note"><strong>SignalBridge MVP</strong><span>청년정책·채용 데이터 연동 실습 중</span></div><p>공개 데이터 변경 추적 프로젝트</p></div>
    </aside>
    {mobileMenu&&<button className="menu-shade" aria-label="메뉴 닫기" onClick={()=>setMobileMenu(false)}/>}
    <div className="workspace">
      <header className="topbar"><button className="mobile-menu" aria-label="메뉴 열기" onClick={()=>setMobileMenu(true)}><Icon name="menu"/></button>
        <label className="global-search"><Icon name="search" size={19}/><input placeholder={view==='opportunities'?'정책명, 기관, 직무를 검색해 보세요...':'프로젝트, 키워드, 저장소를 검색해보세요...'} type="search" value={view==='opportunities'?opQuery:query} maxLength={100} onChange={e=>{if(view==='opportunities'){setOpQuery(e.target.value);}else{setQuery(e.target.value);setPage(0);if(e.target.value)setView('records');}}}/></label>
        <div className="header-actions"><button className="header-icon" aria-label="새로고침" title="최신 API 데이터 다시 조회" onClick={()=>setRefreshVersion(n=>n+1)}><Icon name="refresh"/></button>{member?<><button type="button" className="auth-user-label account-top-link" title="마이페이지 열기" onClick={()=>navigate('account')}>{member.nickname}</button><button type="button" className="auth-signout" onClick={()=>void signOut()}>로그아웃</button></>:<button type="button" className="auth-signin" onClick={()=>navigate('watches')}>로그인</button>}</div>
      </header>
      <main className="main-container">
        {authError&&<p role="alert" className="notice-error">{authError}</p>}
        {/* 확인 링크는 로그인 여부와 무관하게 접근합니다. 성공 시 현재 브라우저 세션은 만료됩니다. */}
        {emailChangeToken ? <EmailChangeConfirm token={emailChangeToken} onDone={()=>{
          setEmailChangeToken(null);setMember(null);setAuthError('');setView('watches');
          window.history.replaceState({},'',window.location.pathname);
        }}/> : <>
        {/* view 변경 시 영역만 다시 마운트하여 기존 450ms 전환 효과를 재생합니다. */}
        <div key={view} className="view-motion">
        {view==='opportunities'&&<OpportunitiesPanel q={opQuery} onQueryChange={setOpQuery} refreshVersion={refreshVersion} loggedIn={authReady&&member!==null} onLogin={()=>navigate('watches')}/>}
        {view==='dashboard'&&<>
          <section className="hero"><div className="hero-copy"><span className="eyebrow">SIGNALBRIDGE</span><h1>중요한 변화, 놓치지 마세요.</h1><p>소프트웨어와 오픈소스 프로젝트의 새로운 릴리스와 변경 사항을 한곳에서 확인하세요.</p></div>
            <div className="hero-visual" aria-hidden="true"><div className="orbit orbit-one"/><div className="orbit orbit-two"/>{dashboardSources.slice(0,4).map((s,i)=><span key={s.id} className={`visual-tile ${['tile-code','tile-git','tile-docker','tile-grafana'][i]}`}><SourceLogo name={s.label} large/></span>)}<span className="orbit-note">{dashboardSources.length?`${dashboardSources.length}개 저장소 추적 중`:'저장소를 등록해 주세요'}</span></div></section>
          <section className="stats-grid" aria-label="데이터 현황">
            <div className="stat-card"><span className="stat-icon blue"><Icon name="layers" size={24}/></span><div><span className="stat-label">총 추적 항목</span><strong>{totalRecords.toLocaleString('ko-KR')}</strong><small>저장된 전체 릴리스</small></div></div>
            <div className="stat-card"><span className="stat-icon coral"><Icon name="bell" size={23}/></span><div><span className="stat-label">최근 7일 변화</span><strong>{!engineeringDashboard?'—':sevenDayEvents.toLocaleString('ko-KR')}</strong><small>내 저장소의 지난 7일 DB 변경 기록</small></div></div>
            <div className="stat-card"><span className="stat-icon violet"><Icon name="database" size={24}/></span><div><span className="stat-label">모니터링 중인 소스</span><strong>{dashboardSources.length}</strong><small>내가 등록한 GitHub 저장소</small></div></div>
            <div className="stat-card"><span className="stat-icon mint"><Icon name="clock" size={24}/></span><div><span className="stat-label">마지막 소스 확인</span><strong className="date-value">{latestChecked?relativeDate(new Date(latestChecked).toISOString()):'기록 없음'}</strong><small>실시간 상태와는 다릅니다</small></div></div>
          </section>
          <div className="middle-grid">
            <section className="panel trend-panel"><div className="panel-title"><div><h2>최근 7일 변경 추이</h2><p>실제 데이터 변경 이력 · KST 기준</p></div><span className="select-look">최근 7일 <Icon name="chevron" size={13}/></span></div>
              <div className="legend"><span><i className="legend-icon new"/>신규</span><span><i className="legend-icon updated"/>업데이트</span></div>
              {engineeringDashboardError?<p className="notice-error">{engineeringDashboardError}</p>:<div className="chart"><div className="grid-lines"><span>{chartMax}</span><span>{Math.ceil(chartMax/2)}</span><span>0</span></div><div className="bars-grid">{series.map(s=><div className="bar-group" key={s.key}><div className="bar-viewport"><div className="stack" style={{height:`${((s.NEW+s.UPDATED)/chartMax)*100}%`}} title={`${s.label}: 신규 ${s.NEW}건, 수정 ${s.UPDATED}건`}><span className="bar-part new" style={{flex:s.NEW||0,display:s.NEW?'block':'none'}}/><span className="bar-part updated" style={{flex:s.UPDATED||0,display:s.UPDATED?'block':'none'}}/></div></div><span className="bar-label">{s.label}</span></div>)}</div></div>}
              <div className="chart-footnote">* 회원별 변경 이력을 PostgreSQL에서 집계한 결과입니다.</div>
            </section>
            <section className="panel recent-panel"><div className="panel-title"><div><h2>최근 변경된 항목 5개</h2><p>마지막으로 감지한 릴리스 변경 사항</p></div><button className="more-link" onClick={()=>navigate('changes')}>전체보기 <Icon name="arrow" size={15}/></button></div>
              <div className="rows compact">{!engineeringDashboard&&!engineeringDashboardError?<p className="empty">내 변경 이력을 불러오는 중...</p>:engineeringDashboardError?<p className="notice-error">{engineeringDashboardError}</p>:engineeringDashboard?.recentChanges.map(changeRow)}{!!engineeringDashboard&&!engineeringDashboardError&&engineeringDashboard.recentChanges.length===0&&<p className="empty">내 저장소의 변경 이력이 아직 없습니다.</p>}</div>
            </section>
          </div>
          <div className="bottom-grid">
            <section className="panel sources-panel"><div className="panel-title"><div><h2>내 데이터 소스 현황</h2><p>직접 등록한 GitHub 저장소의 마지막 확인 기록</p></div><button className="more-link" onClick={()=>navigate('sources')}>전체 소스 보기 <Icon name="arrow" size={15}/></button></div><div className="source-rows">{engineeringDashboardError?<p className="notice-error">{engineeringDashboardError}</p>:dashboardSources.length?dashboardSources.map(s=><div className="source-row" key={s.id}><SourceLogo name={s.label}/><div className="source-label"><strong>{s.label}</strong><span>{s.provider} · {s.resource}</span></div><span className="source-check">{s.lastCheckedAt?<><i/>확인됨</>:'미확인'}</span><small>{s.recordCount}건 저장</small></div>):<p className="empty">소스를 불러오는 중...</p>}</div></section>
            <section className="panel health-panel"><div className="panel-title"><div><h2>시스템 상태</h2><p>DB 수집 기록 기반 운영 현황</p></div><button className="more-link" onClick={()=>navigate('health')}>상세 보기 <Icon name="arrow" size={15}/></button></div><div className="health-cards"><div className="health-card"><span className="health-icon mint"><Icon name="check" size={22}/></span><div><strong>API 조회</strong><span className={recordsError||sourcesError?'health-warning':'health-good'}>{recordsError||sourcesError?'확인 필요':'응답 확인'}</span><small>웹에서 실제 API 조회 결과 기준</small></div></div><div className="health-card"><span className="health-icon coral"><Icon name="bell" size={22}/></span><div><strong>실패 알림</strong><span className="health-pending">Airflow 선택 설정</span><small>전송 성공 여부는 표시하지 않음</small></div></div><div className="health-card"><span className="health-icon blue"><Icon name="database" size={22}/></span><div><strong>내 저장소 릴리스</strong><span className="health-good">{totalRecords.toLocaleString()}건 확인</span><small>API 제공 데이터 집계</small></div></div><div className="health-card"><span className="health-icon violet"><Icon name="clock" size={22}/></span><div><strong>최근 소스 확인</strong><span className="health-pending">{latestChecked?relativeDate(new Date(latestChecked).toISOString()):'기록 없음'}</span><small>각 소스의 기록 중 최근값</small></div></div></div>{member?<OperationsPanel compact refreshVersion={refreshVersion} memberScoped/>:<p className="empty">로그인 후 본인의 수집 기록을 확인할 수 있어요.</p>}</section>
          </div>
          <section className="panel dashboard-preview"><div className="panel-title"><div><h2>내 저장소별 대표 릴리스</h2><p>등록한 저장소마다 가장 높은 버전 한 건</p></div><button className="more-link" onClick={()=>navigate('records')}>모두 보기 <Icon name="arrow" size={15}/></button></div><div className="rows">{dashboardReleases.length?dashboardReleases.map(recordRow):<p className="empty">등록한 저장소의 수집된 릴리스가 없습니다.</p>}</div></section>
        </>}
        {view!=='dashboard'&&view!=='opportunities'&&<section className="inner-page"><div className="inner-page-title"><span className="eyebrow">SIGNALBRIDGE / {view.toUpperCase()}</span><h1>{({records:'릴리스 목록',changes:'변경 이력',repositories:'내 GitHub 저장소',sources:'데이터 소스',health:'시스템 상태',watches:'관심 알림',account:'마이페이지'} as Record<Exclude<View,'dashboard'|'opportunities'>,string>)[view]}</h1><p>{view==='repositories'?'관심 GitHub 저장소를 직접 등록하고 릴리스를 추적하세요.':view==='records'?'GitHub 릴리스를 숫자 버전이 높은 순서로 확인하세요.':view==='changes'?'데이터 변경 이력을 릴리스 버전이 높은 순서로 확인하세요.':view==='sources'?'연동된 출처와 최신 확인 시각을 조회하세요.':view==='watches'?'관심 키워드를 등록하고 새 릴리스와 변경 알림을 관리하세요.':view==='account'?'내 계정 정보를 변경하거나 회원 탈퇴를 관리하세요.':'시스템 관측 정보는 확인 가능한 데이터만 표시합니다.'}</p></div>
        {view==='repositories'&&(authReady ? member ? <MyRepositoriesPanel key={member.id} onChanged={()=>setRefreshVersion(n=>n+1)} onBrowse={sid=>{setSourceId(sid);setPage(0);navigate('records');}}/> : <AuthPanel onLogin={handleLogin}/> : <div className="panel auth-panel">계정 상태 확인 중...</div>)}
        {(view==='records'||view==='changes')&&<section className="panel inner-list-panel"><div className="toolbar"><div><h2>{view==='records'?'전체 릴리스 · 버전 높은순':'변경 이력 · 버전 높은순'}</h2><p>{view==='records'?`${records?.totalElements??0}건 검색 결과`:`${pagedChanges?.totalElements??0}건 기록`}</p></div><label className="source-select"><Icon name="filter" size={15}/><select value={sourceId??''} onChange={e=>{setSourceId(e.target.value?Number(e.target.value):null);setPage(0);setHistoryPage(0);}}><option value="">{member?'내 전체 저장소':'전체 데이터 소스'}</option>{(member?mySources:sources).map(s=><option key={s.id} value={s.id}>{s.label}</option>)}</select></label></div>{member&&mySources.length===0&&<p className="watch-caveat">등록된 저장소가 없습니다. Engineering → 내 GitHub 저장소에서 공개 저장소를 추가해 주세요.</p>}<div className="rows">{view==='records'?(recordsLoading&&!records?<p className="empty">불러오는 중...</p>:recordsError?<p className="notice-error">{recordsError}</p>:records?.items.map(recordRow)):(pagedChangesLoading&&!pagedChanges?<p className="empty">불러오는 중...</p>:pagedChangesError?<p className="notice-error">{pagedChangesError}</p>:pagedChanges?.items.map(changeRow))}</div>{view==='records'&&records&&<div className="pagination"><button disabled={page===0} onClick={()=>{setPage(p=>Math.max(0,p-1));window.scrollTo(0,0);}}>이전</button><span>{page+1} / {Math.max(1,records.totalPages)} 페이지</span><button disabled={page+1>=records.totalPages} onClick={()=>{setPage(p=>p+1);window.scrollTo(0,0);}}>다음</button></div>}{view==='changes'&&pagedChanges&&<div className="pagination"><button disabled={historyPage===0} onClick={()=>{setHistoryPage(p=>Math.max(0,p-1));window.scrollTo(0,0);}}>이전</button><span>{historyPage+1} / {Math.max(1,pagedChanges.totalPages)} 페이지</span><button disabled={historyPage+1>=pagedChanges.totalPages} onClick={()=>{setHistoryPage(p=>p+1);window.scrollTo(0,0);}}>다음</button></div>}</section>}
        {view==='sources'&&<section className="panel"><div className="source-rows detailed">{engineeringDashboardError?<p className="notice-error">{engineeringDashboardError}</p>:dashboardSources.map(s=><div className="source-row" key={s.id}><SourceLogo name={s.label}/><div className="source-label"><strong>{s.label}</strong><span>{s.provider} · {s.resource}</span></div><span className="source-check">{s.lastCheckedAt?'마지막 확인':'기록 없음'}</span><small>{formatDate(s.lastCheckedAt)}</small></div>)}</div></section>}
        {view==='watches'&&(authReady ? member&&!recoveryEntry ? <WatchlistPanel key={member.id} sources={mySources} refreshVersion={refreshVersion}/> : <AuthPanel onLogin={handleLogin}/> : <div className="panel auth-panel">계정 상태 확인 중...</div>)}
        {view==='account'&&(authReady ? member ? <AccountPanel key={member.id} onMemberUpdate={setMember} onPasswordChanged={()=>{setMember(null);setView('watches');setAuthError('비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해 주세요.');}} onAccountDeleted={()=>{setMember(null);setView('opportunities');setAuthError('계정 탈퇴가 완료됐습니다.');}}/> : <AuthPanel onLogin={handleLogin}/> : <div className="panel auth-panel">계정 상태 확인 중...</div>)}
        {view==='health'&&<>{member?<OperationsPanel refreshVersion={refreshVersion} memberScoped/>:<p className="empty">로그인 후 본인의 수집 상태를 확인할 수 있어요.</p>}<section className="panel health-description"><div className="health-explain"><Icon name="info" size={22}/><p>수집 실행 건수는 ingestion_runs에서 확인하며, 가용성과 Airflow 스케줄러 상태는 별도로 모니터링해야 합니다.</p></div><div className="facts"><p><strong>데이터 조회 API</strong><span>{recordsError?'오류 감지':'최근 조회 응답 확인'}</span></p><p><strong>연동 소스 수</strong><span>{dashboardSources.length}개</span></p><p><strong>저장된 릴리스</strong><span>{totalRecords}건</span></p><p><strong>최종 확인 기록</strong><span>{latestChecked?formatDate(new Date(latestChecked).toISOString()):'없음'}</span></p><p><strong>실패 알림</strong><span>Airflow에서 선택 설정 · 전송 여부 미확인</span></p></div></section></>}
        </section>}
        </div>
        </>}
        <footer className="footer">SignalBridge · 공개 데이터 기반 변경 추적 MVP <span>마지막 수집 시점의 정보를 보여주며 공식 발표를 대신하지 않습니다.</span></footer>
      </main>
    </div>
    {detailId!==null&&<div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)setDetailId(null);}}><section className="detail-drawer" role="dialog" aria-modal="true" aria-label="릴리스 상세"><div className="drawer-head"><span>릴리스 상세 정보</span><button aria-label="닫기" onClick={()=>setDetailId(null)}><Icon name="close"/></button></div>{detailLoading?<p className="empty">불러오는 중...</p>:detailError?<p className="notice-error">{detailError}</p>:detail&&<div className="drawer-body"><div className="drawer-project"><SourceLogo name={detail.sourceLabel} large/><div><span>{detail.sourceLabel}</span><h2>{detail.title}</h2></div></div><span className="tag tag-purple">변경 이력 v{detail.currentVersion}</span><div className="detail-facts"><div><small>릴리스 태그</small><strong>{detail.tagName||'정보 없음'}</strong></div><div><small>배포일</small><strong>{formatDate(detail.publishedAt)}</strong></div><div><small>최초 발견</small><strong>{formatDate(detail.firstSeenAt)}</strong></div><div><small>마지막 확인</small><strong>{formatDate(detail.lastSeenAt)}</strong></div></div><section className="record-history-section" aria-label="이 릴리스의 변경 이력"><h3>이 릴리스의 변경 이력</h3><p className="record-history-note">SignalBridge에서 수집한 최근 버전 최대 20개</p>{recordHistoryLoading?<p className="empty">변경 이력을 불러오는 중...</p>:recordHistoryError?<p className="notice-error" role="alert">{recordHistoryError}</p>:recordHistory.length===0?<p className="empty">아직 기록된 변경 이력이 없습니다.</p>:<ol className="record-history-timeline">{recordHistory.map(history=><li key={history.id} className="record-history-entry"><div className="record-history-header"><strong>버전 {history.version}</strong><span className={`tag ${history.changeType==='NEW'?'tag-blue':'tag-purple'}`}>{history.changeType==='NEW'?'최초 수집':'내용 변경'}</span></div><time>{formatDate(history.detectedAt)}</time><p>{history.changedFields.length?`변경된 필드: ${history.changedFields.join(', ')}`:history.changeType==='NEW'?'최초 수집 시점의 데이터입니다.':'변경된 필드 상세 정보 없음'}</p></li>)}</ol>}</section><h3>Release Notes</h3><div className="release-description">{detail.description||'릴리스 설명이 없습니다.'}</div>{safeLink(detail.url)&&<a href={safeLink(detail.url)} target="_blank" rel="noopener noreferrer" className="origin-link">GitHub 원문 보기 <Icon name="external" size={16}/></a>}</div>}</section></div>}
  </div>;
}
