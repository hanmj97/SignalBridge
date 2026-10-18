import { useEffect, useState } from 'react';
import { fetchIngestions } from './api';
import type { IngestionSnapshot, IngestionSource } from './api';

const formatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Seoul',
});

function showTime(value: string | null): string {
  if (!value) return '기록 없음';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? '기록 없음' : formatter.format(parsed);
}

function statusText(status: string | null): string {
  switch (status) {
    case 'SUCCESS': return '수집 성공';
    case 'NOT_MODIFIED': return '변경 없음';
    case 'FAILED': return '수집 실패';
    case 'RUNNING': return '수집 중';
    default: return '실행 기록 없음';
  }
}

function statusClass(status: string | null): string {
  if (status === 'FAILED') return 'ops-badge--failed';
  if (status === 'SUCCESS' || status === 'NOT_MODIFIED') return 'ops-badge--good';
  return 'ops-badge--muted';
}

function freshness(source: IngestionSource, generatedAt: string): { text: string; style: string } {
  if (source.latestStatus === 'FAILED') return { text: '최근 실행 실패', style: 'ops-badge--failed' };
  if (source.latestStatus === 'RUNNING') {
    const started = source.latestStartedAt ? Date.parse(source.latestStartedAt) : NaN;
    const now = Date.parse(generatedAt);
    if (Number.isFinite(started) && Number.isFinite(now) && now - started > 15 * 60 * 1000) {
      return { text: '수집 실행 지연', style: 'ops-badge--warning' };
    }
    return { text: '수집 진행 중', style: 'ops-badge--muted' };
  }
  if (!source.lastCheckedAt) return { text: '확인 기록 없음', style: 'ops-badge--muted' };
  const checked = Date.parse(source.lastCheckedAt);
  const now = Date.parse(generatedAt);
  if (!Number.isFinite(checked) || !Number.isFinite(now)) return { text: '시각 확인 불가', style: 'ops-badge--muted' };
  // 90 minutes is a UI observation threshold (30-minute scheduled DAG), not proof that Airflow is down.
  if (now - checked > 90 * 60 * 1000) return { text: '최근 확인 지연', style: 'ops-badge--warning' };
  return { text: '최근 확인됨', style: 'ops-badge--good' };
}

export default function OperationsPanel({ compact = false, refreshVersion }: {
  compact?: boolean;
  refreshVersion: number;
}) {
  const [data, setData] = useState<IngestionSnapshot | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let pending: AbortController | null = null;
    function load() {
      pending?.abort();
      const current = new AbortController();
      pending = current;
      fetchIngestions(current.signal)
        .then(value => { if (!current.signal.aborted) { setData(value); setError(''); } })
        .catch((cause: unknown) => {
          if (!current.signal.aborted) setError(cause instanceof Error ? cause.message : '수집 상태를 가져오지 못했습니다.');
        })
        .finally(() => { if (!current.signal.aborted) setLoading(false); });
    }
    load();
    // Poll only while this page is visible. This does NOT trigger the collector.
    const interval = window.setInterval(() => { if (!document.hidden) load(); }, 60_000);
    return () => { pending?.abort(); window.clearInterval(interval); };
  }, [refreshVersion]);

  if (compact) {
    return <div className="ops-compact" aria-live="polite">
      {loading && !data && !error ? <span>수집 운영 기록 불러오는 중...</span>
        : error ? <span className="ops-fetch-error">수집 기록 API 확인 필요 · {error}</span>
        : data ? <>
          <strong>최근 24시간 수집</strong>
          <span>실행 {data.runsLast24Hours}회</span>
          <span>완료 {data.completedLast24Hours}회</span>
          <span className={data.failuresLast24Hours ? 'ops-count-failed' : ''}>실패 {data.failuresLast24Hours}회</span>
          <small>DB 기록 기준 · 수집 작업 단위</small>
        </> : <span>수집 기록 없음</span>}
    </div>;
  }

  return <section className="panel ops-panel" aria-label="수집 운영 기록">
    <div className="panel-title ops-heading">
      <div><h2>수집 파이프라인 운영 기록</h2>
        <p>PostgreSQL ingestion_runs 기준 · 60초마다 화면에서 재조회</p></div>
      {data && <time className="ops-observed">조회 기준 {showTime(data.generatedAt)}</time>}
    </div>
    <p className="ops-caveat">이 화면은 DB에 저장된 수집 결과를 보여줍니다. Airflow 스케줄러 가동 여부, DAG 최종 상태 또는 Discord 알림 전송 성공 여부를 직접 확인하지 않습니다.</p>
    {loading && !data && !error && <p className="empty">수집 기록을 조회하는 중...</p>}
    {error && <p role="alert" className="notice-error">수집 기록 조회 실패: {error} · 상단 새로고침 버튼을 눌러 재시도해 주세요.</p>}
    {data && <>
      <div className="ops-metrics">
        <div><span>연동 중인 소스</span><strong>{data.activeSourceCount}</strong><small>활성 GitHub 소스</small></div>
        <div><span>최근 24시간 실행</span><strong>{data.runsLast24Hours}</strong><small>소스별 수집 실행 건수</small></div>
        <div><span>최근 24시간 완료</span><strong>{data.completedLast24Hours}</strong><small>SUCCESS + NOT_MODIFIED</small></div>
        <div><span>최근 24시간 실패</span><strong className={data.failuresLast24Hours ? 'ops-count-failed' : ''}>{data.failuresLast24Hours}</strong><small>재시도로 인한 실패도 포함</small></div>
      </div>
      <div className="ops-group-title"><h3>소스별 최근 수집</h3><span>90분 초과 미확인은 ‘지연’으로 표시</span></div>
      <div className="ops-sources">
        {data.sources.length === 0 ? <p className="empty">활성화된 GitHub 소스가 없습니다.</p> : data.sources.map(source => {
          const checked = freshness(source, data.generatedAt);
          return <div className="ops-source" key={source.sourceId}>
            <div className="ops-source-name"><strong>{source.label}</strong><span>{source.resource}</span></div>
            <div className="ops-source-state"><span className={`ops-badge ${checked.style}`}>{checked.text}</span><small>최근 작업: {statusText(source.latestStatus)}</small></div>
            <div className="ops-source-time"><span>마지막 성공 확인</span><strong>{showTime(source.lastCheckedAt)}</strong></div>
          </div>;
        })}
      </div>
      <div className="ops-group-title"><h3>최근 수집 실행 12건</h3><span>최신 순서 · 원본 오류 메시지 비공개</span></div>
      <div className="ops-table-scroll">
        <table className="ops-table">
          <thead><tr><th scope="col">데이터 소스</th><th scope="col">상태</th><th scope="col">시작 시각 (KST)</th><th scope="col">가져온 수</th><th scope="col">신규</th><th scope="col">변경</th></tr></thead>
          <tbody>
            {data.recentRuns.length === 0 ? <tr><td colSpan={6} className="ops-empty-cell">수집 실행 기록이 없습니다.</td></tr>
              : data.recentRuns.map(run => <tr key={run.id}>
                <td>{run.sourceLabel}</td>
                <td><span className={`ops-badge ${statusClass(run.status)}`}>{statusText(run.status)}</span></td>
                <td>{showTime(run.startedAt)}</td>
                <td>{run.fetchedCount}</td><td>{run.newCount}</td><td>{run.updatedCount}</td>
              </tr>)}
          </tbody>
        </table>
      </div>
      <p className="ops-footnote">NOT_MODIFIED는 원본 데이터에 변동이 없었다는 정상 결과입니다. 마지막 성공 확인은 sources.last_checked_at 기준이며, 최근 실행의 실패와 별도로 표시합니다.</p>
    </>}
  </section>;
}
