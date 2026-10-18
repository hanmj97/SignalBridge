import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { createWatch, fetchWatchEvents, fetchWatchRules, removeWatch, setWatchEnabled, connectDiscord, disconnectDiscord, fetchDiscordStatus } from './api';
import type { DiscordStatus, Source, WatchEvent, WatchRule } from './api';

const dateFormat = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Seoul',
});
function dateString(raw: string | null) {
  if (!raw) return '전송 전';
  const d = new Date(raw);
  return Number.isNaN(d.getTime()) ? '확인 불가' : dateFormat.format(d);
}
function externalLink(value: string) {
  try { const url = new URL(value); return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined; }
  catch { return undefined; }
}
const statusLabel: Record<WatchEvent['status'], string> = {
  PENDING: '전송 대기', SENT: '전송 완료', FAILED: '전송 실패',
};

export default function WatchlistPanel({sources, refreshVersion}: {sources: Source[];refreshVersion: number}) {
  const [discord, setDiscord] = useState<DiscordStatus|null>(null);
  const [discordUrl, setDiscordUrl] = useState('');
  const [discordBusy, setDiscordBusy] = useState(false);
  const [discordFeedback, setDiscordFeedback] = useState('');
  const [discordError, setDiscordError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [sourceId, setSourceId] = useState('');
  const [rules, setRules] = useState<WatchRule[]>([]);
  const [events, setEvents] = useState<WatchEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const refresh = useCallback(async (signal?: AbortSignal) => {
    try {
      const [nextRules, nextEvents, nextDiscord] = await Promise.all([
        fetchWatchRules(signal), fetchWatchEvents(signal), fetchDiscordStatus(signal),
      ]);
      if (!signal?.aborted) setDiscord(nextDiscord);
      if (!signal?.aborted) { setRules(nextRules); setEvents(nextEvents); setError(''); }
    } catch (cause: unknown) {
      if (!signal?.aborted) setError(cause instanceof Error ? cause.message : '관심 알림 조회에 실패했습니다.');
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const c = new AbortController();
    void refresh(c.signal);
    return () => c.abort();
  }, [refresh, refreshVersion]);

  async function addRule(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy) return;
    setError(''); setSuccess(''); setBusy(true);
    try {
      await createWatch(keyword.trim(), sourceId ? Number(sourceId) : null);
      setKeyword(''); setSourceId(''); setSuccess('관심 규칙을 등록했습니다. 이후 발생하는 신규·변경 이벤트부터 검사합니다.');
      await refresh();
    } catch (cause: unknown) { setError(cause instanceof Error ? cause.message : '등록 실패'); }
    finally { setBusy(false); }
  }
  // Explicit opt-in for all future releases from one of my own repositories.
  // Stored as an ordinary watch rule; existing per-user Discord controls apply.
  async function addAllReleasesRule() {
    if (busy || !sourceId) return;
    setBusy(true); setError(''); setSuccess('');
    try {
      await createWatch('모든 릴리스', Number(sourceId));
      setKeyword(''); setSourceId('');
      setSuccess('선택한 저장소의 이후 신규 릴리스·내용 변경 알림을 등록했습니다.');
      await refresh();
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : '릴리스 전체 알림 등록 실패');
    } finally { setBusy(false); }
  }

  async function toggleRule(rule: WatchRule) {
    if (busy) return;
    setBusy(true);setError('');setSuccess('');
    try { await setWatchEnabled(rule.id, !rule.enabled); await refresh(); }
    catch (cause: unknown) { setError(cause instanceof Error ? cause.message : '상태 변경 실패'); }
    finally {setBusy(false);}
  }
  async function deleteRule(rule: WatchRule) {
    if (busy) return;
    if (!window.confirm(`'${rule.keyword}' 규칙을 삭제할까요? 해당 규칙의 알림 기록도 함께 삭제됩니다.`)) return;
    setBusy(true);setError('');setSuccess('');
    try { await removeWatch(rule.id); await refresh(); }
    catch (cause: unknown) { setError(cause instanceof Error ? cause.message : '삭제 실패'); }
    finally {setBusy(false);}
  }

  async function saveDiscord(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (discordBusy) return;
    setDiscordBusy(true); setDiscordError(''); setDiscordFeedback('');
    try {
      const next = await connectDiscord(discordUrl.trim());
      setDiscord(next); setDiscordUrl('');
      setDiscordFeedback('연결 테스트 메시지가 Discord에 발송됐습니다. 새 변경 이벤트부터 이 채널로 알림이 전송됩니다.');
    } catch (cause: unknown) {
      setDiscordError(cause instanceof Error ? cause.message : 'Discord 채널 연결에 실패했습니다.');
    } finally {setDiscordBusy(false);}
  }
  async function deleteDiscord() {
    if (discordBusy || !window.confirm('Discord 알림 채널 연결을 해제할까요? 기존 알림 기록은 유지됩니다.')) return;
    setDiscordBusy(true); setDiscordError(''); setDiscordFeedback('');
    try {await disconnectDiscord(); setDiscord({configured:false, connectedAt:null});setDiscordUrl('');setDiscordFeedback('Discord 연결을 해제했습니다.');}
    catch(cause: unknown) {setDiscordError(cause instanceof Error ? cause.message : '해제 실패');}
    finally {setDiscordBusy(false);}
  }

  const activeCount = rules.filter(r => r.enabled).length;
  const sent = events.filter(e => e.status === 'SENT').length;
  const queued = events.filter(e => e.status === 'PENDING').length;
  return <div className="watch-page">
    <section className="panel watch-panel discord-connect-panel">
      <div className="panel-title"><div><h2>내 Discord 알림 채널</h2><p>내 관심 키워드에 맞는 새 변경만 내 채널로 전송합니다.</p></div>
        <span className={`watch-status ${discord?.configured?'watch-status--enabled':'watch-status--muted'}`}>
          {discord===null?'확인 중':discord.configured?'연결됨':'미연결'}</span></div>
      <p className="watch-caveat">Discord 서버 설정 → 연동 → 웹후크 → 새 웹후크에서 주소를 복사하세요. 연결 시 테스트 메시지 1건이 전송되며 주소는 암호화해 저장합니다. 연결 이전에 생성된 이벤트는 소급 전송하지 않습니다.</p>
      {discord?.configured && <p className="discord-connected">연결 시각: {dateString(discord.connectedAt)} · 주소는 보안을 위해 다시 표시하지 않습니다.</p>}
      <form className="discord-connect-form" onSubmit={saveDiscord}>
        <label className="watch-field"><span>Discord 웹훅 URL</span>
          <input type="password" autoComplete="off" value={discordUrl} onChange={e=>setDiscordUrl(e.target.value)} placeholder="https://discord.com/api/webhooks/..." required minLength={60}/>
        </label>
        <div className="discord-connect-actions">
          <button type="submit" className="watch-submit" disabled={discordBusy || discordUrl.trim().length<60}>{discordBusy?'연결 확인 중...':discord?.configured?'채널 변경 · 테스트 전송':'채널 연결 · 테스트 전송'}</button>
          {discord?.configured && <button type="button" className="discord-disconnect" disabled={discordBusy} onClick={()=>void deleteDiscord()}>연결 해제</button>}
        </div>
      </form>
      {discordError && <p className="notice-error" role="alert">{discordError}</p>}
      {discordFeedback && <p className="watch-success" role="status">{discordFeedback}</p>}
    </section>
    <section className="panel watch-panel">
      <div className="panel-title"><div><h2>관심 키워드 등록</h2><p>연결한 저장소에 대해 키워드 또는 모든 신규·변경 릴리스 알림을 등록하세요.</p></div></div>
      <p className="watch-caveat">로그인한 계정에서만 자신의 관심 규칙을 볼 수 있습니다. 기존 릴리스에는 소급 알림을 보내지 않습니다. 상단에서 Discord 채널을 연결하면 이후 수집되는 신규·변경 이벤트가 30분 수집 주기마다 전달됩니다. 연결 전 이벤트는 전송하지 않습니다.</p>
      <form className="watch-create" onSubmit={addRule}>
        <label className="watch-field"><span>관심 키워드</span><input type="text" value={keyword} minLength={2} maxLength={80} onChange={e=>setKeyword(e.target.value)} placeholder="예: Grafana, rollback" required/></label>
        <label className="watch-field"><span>데이터 소스</span><select value={sourceId} onChange={e=>setSourceId(e.target.value)}><option value="">전체 데이터 소스</option>{sources.map(s=><option value={s.id} key={s.id}>{s.label}</option>)}</select></label>
        <button type="submit" className="watch-submit" disabled={busy || keyword.trim().length < 2 || rules.length >= 30}>+ 규칙 추가</button>
        <button type="button" className="watch-submit" disabled={busy || !sourceId || rules.length >= 30}
          onClick={()=>void addAllReleasesRule()}>선택 저장소 모든 릴리스 알림</button>
      </form>
      {error&&<p className="notice-error" role="alert">{error}</p>}
      {success&&<p role="status" className="watch-success">{success}</p>}
      <div className="watch-metrics"><div><span>등록 규칙</span><strong>{rules.length}</strong></div><div><span>활성 규칙</span><strong>{activeCount}</strong></div><div><span>최근 20건 중 전송 완료</span><strong>{sent}</strong></div><div><span>최근 20건 중 대기</span><strong>{queued}</strong></div></div>
      <div className="watch-section-heading"><h3>등록한 관심 규칙</h3><span>최대 30개 · 변경 사항은 DB에 보관</span></div>
      {loading ? <p className="empty">불러오는 중...</p> : rules.length===0 ? <p className="empty">관심 키워드를 등록하면 새 릴리스를 감지할 때 알림 후보가 기록됩니다.</p>:
        <div className="watch-rules">{rules.map(rule=><div className="watch-rule" key={rule.id}>
          <div className="watch-rule-main"><strong>{rule.keyword}</strong><small>{rule.sourceLabel||'전체 소스'} · {dateString(rule.createdAt)} 등록</small></div>
          <span className={`watch-status ${rule.enabled?'watch-status--enabled':'watch-status--muted'}`}>{rule.enabled?'사용 중':'일시 중지'}</span>
          <div className="watch-rule-actions"><button type="button" disabled={busy} onClick={()=>void toggleRule(rule)}>{rule.enabled?'중지':'재개'}</button><button type="button" className="watch-delete" disabled={busy} onClick={()=>void deleteRule(rule)}>삭제</button></div>
        </div>)}</div>}
    </section>
    <section className="panel watch-panel">
      <div className="panel-title"><div><h2>최근 관심 알림 이벤트</h2><p>DB에 기록된 최근 20건 · 신규 버전 기준 중복 방지</p></div><button type="button" className="watch-refresh" onClick={()=>{setLoading(true);void refresh();}} disabled={busy}>새로고침</button></div>
      {events.length===0?<p className="empty">아직 알림 이벤트가 없습니다. 새 릴리스 또는 내용 변경이 수집되면 표시됩니다.</p>:<div className="watch-events">{events.map(event=><article className="watch-event" key={event.id}>
        <div className="watch-event-head"><span className={`watch-status watch-status--${event.status.toLowerCase()}`}>{statusLabel[event.status]}</span><small>{event.changeType==='NEW'?'신규 릴리스':'내용 변경'} · {dateString(event.detectedAt)}</small></div>
        <strong>{event.title}</strong><p>{event.sourceLabel} · 규칙: {event.keyword}</p>
        <div className="watch-event-foot"><span>{event.deliveredAt?`전송: ${dateString(event.deliveredAt)}`:'Discord 전송 대기 또는 미완료'}</span>{externalLink(event.url)&&<a href={externalLink(event.url)} target="_blank" rel="noopener noreferrer">원문 보기 ↗</a>}</div>
      </article>)}</div>}
      <p className="ops-footnote">이 화면은 Airflow 알림 작업의 DB 기록을 보여줍니다. SENT는 Discord 웹훅으로 2xx 응답을 받은 상태이며, 실제 사용자 기기에서 알림이 표시됐는지는 확인할 수 없습니다.</p>
    </section>
  </div>;
}
