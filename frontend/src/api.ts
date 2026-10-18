// Types mirror the immutable JSON DTOs from Phase 2 Spring Boot backend.
export type ApiPage<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type Source = {
  id: number;
  sourceKey: string;
  label: string;
  provider: string;
  resource: string;
  lastCheckedAt: string | null;
  recordCount: number;
};

export type RecordItem = {
  id: number;
  sourceId: number;
  sourceLabel: string;
  title: string;
  url: string;
  tagName: string | null;
  publishedAt: string | null;
  currentVersion: number;
  firstSeenAt: string;
  lastSeenAt: string;
  latestChangedAt: string | null;
};

export type RecordDetail = RecordItem & {
  description: string | null;
  prerelease: boolean;
};

export type ChangeItem = {
  id: number;
  recordId: number;
  recordTitle: string;
  sourceLabel: string;
  version: number;
  changeType: 'NEW' | 'UPDATED';
  changedFields: string[];
  detectedAt: string;
};

async function readJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(url, { signal, headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`API 요청 실패 (HTTP ${response.status}). 백엔드 컨테이너를 확인해 주세요.`);
  }
  return (await response.json()) as T;
}

export function fetchSources(signal?: AbortSignal) {
  return readJson<Source[]>('/api/v1/sources', signal);
}

export function fetchRecords(page: number, q: string, sourceId: number | null, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: '10' });
  if (q.trim()) params.set('q', q.trim());
  if (sourceId !== null) params.set('sourceId', String(sourceId));
  return readJson<ApiPage<RecordItem>>(`/api/v1/records?${params}`, signal);
}

export function fetchChanges(page: number, size: number, sourceId: number | null, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (sourceId !== null) params.set('sourceId', String(sourceId));
  return readJson<ApiPage<ChangeItem>>(`/api/v1/changes?${params}`, signal);
}

export function fetchRecord(id: number, signal?: AbortSignal) {
  return readJson<RecordDetail>(`/api/v1/records/${id}`, signal);
}

// Recent recorded versions for a single release (new in Phase 4).
export function fetchRecordHistory(id: number, signal?: AbortSignal) {
  return readJson<ChangeItem[]>(`/api/v1/records/${id}/history`, signal);
}

// Phase 7: database-backed ingestion outcome reporting (not Airflow scheduler health).
export type IngestionSource = {
  sourceId: number;
  label: string;
  resource: string;
  lastCheckedAt: string | null;
  latestRunId: number | null;
  latestStatus: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'NOT_MODIFIED' | null;
  latestStartedAt: string | null;
  latestFinishedAt: string | null;
  latestFetchedCount: number;
  latestNewCount: number;
  latestUpdatedCount: number;
};

export type IngestionRun = {
  id: number;
  sourceId: number;
  sourceLabel: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'NOT_MODIFIED';
  startedAt: string;
  finishedAt: string | null;
  fetchedCount: number;
  newCount: number;
  updatedCount: number;
};

export type IngestionSnapshot = {
  generatedAt: string;
  activeSourceCount: number;
  runsLast24Hours: number;
  completedLast24Hours: number;
  failuresLast24Hours: number;
  sources: IngestionSource[];
  recentRuns: IngestionRun[];
};

export function fetchIngestions(signal?: AbortSignal) {
  return readJson<IngestionSnapshot>('/api/v1/operations/ingestions', signal);
}

// Phase 9: watch rules are scoped to the authenticated member.
export type WatchRule = {
  id: number; keyword: string; sourceId: number | null; sourceLabel: string | null;
  enabled: boolean; createdAt: string;
};
export type WatchEvent = {
  id: number; ruleId: number; keyword: string; recordId: number; versionId: number;
  title: string; sourceLabel: string; url: string; changeType: 'NEW' | 'UPDATED';
  status: 'PENDING' | 'SENT' | 'FAILED'; detectedAt: string; deliveredAt: string | null;
};

export function fetchWatchRules(signal?: AbortSignal) {
  return readJson<WatchRule[]>('/api/v1/watches', signal);
}
export function fetchWatchEvents(signal?: AbortSignal) {
  return readJson<WatchEvent[]>('/api/v1/watches/events', signal);
}

async function writeWatch(path: string, method: 'POST'|'PATCH'|'DELETE', body?: unknown) {
  const response = await fetch(path, {
    method, headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-CSRF-TOKEN': await getCsrfToken() },
    ...(body === undefined ? {} : {body: JSON.stringify(body)}),
  });
  if (!response.ok) {
    const hint = response.status === 409 ? '이미 등록된 규칙이거나 규칙 수 제한에 도달했습니다.'
      : response.status === 400 ? '키워드나 데이터 소스 선택을 확인해 주세요.'
      : response.status === 404 ? '이미 삭제된 규칙입니다.'
      : `HTTP ${response.status} · 서버 로그를 확인해 주세요.`;
    throw new Error(hint);
  }
  return response;
}
export async function createWatch(keyword: string, sourceId: number | null) {
  const response = await writeWatch('/api/v1/watches', 'POST', { keyword, sourceId });
  return (await response.json()) as WatchRule;
}
export async function setWatchEnabled(id: number, enabled: boolean) {
  await writeWatch(`/api/v1/watches/${id}`, 'PATCH', { enabled });
}
export async function removeWatch(id: number) {
  await writeWatch(`/api/v1/watches/${id}`, 'DELETE');
}


// Phase 9: same-origin session + server-side CSRF token (never localStorage).
export type Member = { id: number; email: string; nickname: string };
let csrfPromise: Promise<string> | undefined;
export function clearCsrf() { csrfPromise = undefined; }
export async function getCsrfToken(): Promise<string> {
  if (!csrfPromise) csrfPromise = fetch('/api/v1/auth/csrf', {credentials:'same-origin'})
    .then(async response => {if (!response.ok) throw new Error('보안 토큰 요청 실패');return (await response.json() as {token:string}).token;})
    .catch(err => {csrfPromise=undefined;throw err;});
  return csrfPromise;
}
export async function currentMember(): Promise<Member|null> {
  const response=await fetch('/api/v1/account/profile');
  if(response.status===401) return null;
  if(!response.ok) throw new Error(`계정 상태 조회 실패 (HTTP ${response.status})`);
  return (await response.json()) as Member;
}
async function authWrite<T>(path:string, payload?:unknown):Promise<T> {
  const response=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-TOKEN':await getCsrfToken()},
    ...(payload===undefined?{}:{body:JSON.stringify(payload)})});
  if(response.status===401)throw new Error('이메일 또는 비밀번호가 일치하지 않습니다.');
  if(response.status===423)throw new Error('이메일 인증이 필요합니다. 인증 메일을 확인하거나 재발송해 주세요.');
  if(response.status===409)throw new Error('이미 사용 중인 이메일입니다.');
  if(response.status===400)throw new Error('입력한 이메일·닉네임·비밀번호를 확인해 주세요.');
  if(response.status===403){clearCsrf();throw new Error('인증 토큰이 만료됐습니다. 다시 시도해 주세요.');}
  if(response.status===503)throw new Error('메일 발송 서비스가 준비되지 않았거나 요청에 실패했습니다. 관리자 설정을 확인하고 잠시 후 재발송해 주세요.');
  if(!response.ok)throw new Error(`요청 실패 (HTTP ${response.status})`);
  if(response.status===204)return undefined as T;
  return (await response.json()) as T;
}
export type VerificationMessage = {message:string};
export function registerMember(email:string,nickname:string,password:string){return authWrite<VerificationMessage>('/api/v1/auth/register',{email,nickname,password});}
export function resendVerification(email:string){return authWrite<VerificationMessage>('/api/v1/auth/resend-verification',{email});}
export function verifyEmail(token:string){return authWrite<VerificationMessage>('/api/v1/auth/verify-email',{token});}
export async function loginMember(email:string,password:string){
  const member=await authWrite<Member>('/api/v1/auth/login',{email,password});
  clearCsrf(); // a fresh session ID was issued on login.
  return member;
}
export async function logoutMember(){await authWrite<void>('/api/v1/auth/logout');clearCsrf();}


// Phase 11: member Discord destinations. The raw URL is never returned by GET.
export type DiscordStatus = { configured: boolean; connectedAt: string | null };
export function fetchDiscordStatus(signal?: AbortSignal) {
  return readJson<DiscordStatus>('/api/v1/watches/discord', signal);
}
export async function connectDiscord(url: string) {
  const response = await writeWatch('/api/v1/watches/discord', 'POST', {webhookUrl:url});
  return (await response.json()) as DiscordStatus;
}
export async function disconnectDiscord() {
  await writeWatch('/api/v1/watches/discord', 'DELETE');
}


// Phase 12: authenticated account settings. Browser session cookie is handled by the browser.
export type AccountProfile = Member & { emailVerified: boolean; createdAt: string };
export function fetchAccountProfile(signal?: AbortSignal) {
  return readJson<AccountProfile>('/api/v1/account/profile', signal);
}
async function accountWrite<T>(path:string, method:'PATCH'|'POST', payload:unknown):Promise<T> {
  const response=await fetch(path,{
    method, credentials:'same-origin',
    headers:{'Content-Type':'application/json',Accept:'application/json','X-CSRF-TOKEN':await getCsrfToken()},
    body:JSON.stringify(payload),
  });
  if(response.status===401)throw new Error('로그인 세션이 만료됐습니다. 다시 로그인해 주세요.');
  if(response.status===403){clearCsrf();throw new Error('보안 토큰이 만료되었습니다. 새로고침 후 다시 시도해 주세요.');}
  if(response.status===400)throw new Error(method==='PATCH'?'닉네임은 2~40자로 입력해 주세요.':'현재 비밀번호와 새 비밀번호를 확인해 주세요. (새 비밀번호 12자 이상)');
  if(response.status===409)throw new Error('다른 요청으로 계정 정보가 변경됐습니다. 새로고침 후 재시도해 주세요.');
  if(!response.ok)throw new Error(`계정 정보 요청 실패 (HTTP ${response.status})`);
  if(response.status===204)return undefined as T;
  return (await response.json()) as T;
}
export async function changeNickname(nickname:string){
  return accountWrite<AccountProfile>('/api/v1/account/nickname','PATCH',{nickname});
}
export async function changeAccountPassword(currentPassword:string,newPassword:string){
  await accountWrite<void>('/api/v1/account/password','POST',{currentPassword,newPassword});
  clearCsrf(); // Server invalidates current browser session.
}


// Phase 13: public password recovery (identical response for unknown emails).
export function requestPasswordReset(email:string) {
  return authWrite<VerificationMessage>('/api/v1/auth/password-reset/request',{email});
}
export async function confirmPasswordReset(token:string,newPassword:string) {
  const response=await fetch('/api/v1/auth/password-reset/confirm',{
    method:'POST',credentials:'same-origin',
    headers:{'Content-Type':'application/json','X-CSRF-TOKEN':await getCsrfToken()},
    body:JSON.stringify({token,newPassword}),
  });
  if(response.status===400)throw new Error('링크가 만료되었거나, 비밀번호가 유효하지 않습니다. (12자 이상, 기존 비밀번호와 다르게)');
  if(response.status===403){clearCsrf();throw new Error('보안 토큰이 만료됐습니다. 페이지를 새로고침해 주세요.');}
  if(response.status===409)throw new Error('다른 계정 변경과 충돌했습니다. 새 링크를 요청해 주세요.');
  if(!response.ok)throw new Error(`비밀번호 재설정 실패 (HTTP ${response.status})`);
}


// Phase 14: member lifecycle, all mutating requests require session CSRF.
async function lifecycleWrite(path:string,method:'POST'|'DELETE',payload:unknown):Promise<void> {
  const response=await fetch(path,{
    method,credentials:'same-origin',
    headers:{'Content-Type':'application/json','X-CSRF-TOKEN':await getCsrfToken()},
    body:JSON.stringify(payload),
  });
  if(response.status===401)throw new Error('로그인이 필요합니다. 다시 로그인해 주세요.');
  if(response.status===403){clearCsrf();throw new Error('보안 토큰이 만료됐습니다. 새로고침 후 다시 시도해 주세요.');}
  if(response.status===429)throw new Error('요청 횟수가 제한됐습니다. 2분 후 다시 시도해 주세요.');
  if(response.status===409)throw new Error('이미 사용 중인 이메일이거나 계정 상태가 변경됐습니다.');
  if(response.status===400)throw new Error('입력 내용 또는 현재 비밀번호가 잘못됐거나 인증 링크가 만료됐습니다.');
  if(response.status===503)throw new Error('이메일 발송 서비스 요청에 실패했습니다. 발송 설정을 확인해 주세요.');
  if(!response.ok)throw new Error(`요청 실패 (HTTP ${response.status})`);
}
export function requestAccountEmailChange(newEmail:string,currentPassword:string){
  return lifecycleWrite('/api/v1/account/email/request','POST',{newEmail,currentPassword});
}
export async function confirmAccountEmailChange(token:string){
  await lifecycleWrite('/api/v1/auth/email-change/confirm','POST',{token});
  clearCsrf();
}
export async function deleteMyAccount(currentPassword:string,confirmation:string){
  await lifecycleWrite('/api/v1/account/profile','DELETE',{currentPassword,confirmation});
  clearCsrf();
}


// SignalBridge V2-2 — read-only public opportunity catalog.
// `includeDemo=true` is explicit for the isolated V2 lab.
export type OpportunityKind = 'POLICY' | 'BENEFIT' | 'JOB' | 'PROGRAM';
export type OpportunityItem = {
  id: number; title: string; url: string; sourceLabel: string; linkKind: 'ARTICLE' | 'DATASET';
  kind: OpportunityKind; organization: string | null; category: string | null;
  regionCodes: string[]; applicationStart: string | null; applicationDeadline: string | null;
  status: 'OPEN' | 'UPCOMING' | 'CLOSED' | 'UNKNOWN';
  ageMin: number | null; ageMax: number | null; employmentType: string | null;
  eligibilitySummary: string | null; benefitSummary: string | null;
  sourcePublishedAt: string | null; firstSeenAt: string | null; lastSeenAt: string | null;
  currentVersion: number; isDemo: boolean;
};
export type OpportunityChange = {
  version: number; changeType: 'NEW' | 'UPDATED';
  changedFields: string[]; detectedAt: string;
};
export type OpportunityDetail = {
  item: OpportunityItem; history: OpportunityChange[];
};
export function fetchOpportunities(page:number, q:string, scope:'YOUTH'|'JOB', region:string, signal?:AbortSignal){
  const p=new URLSearchParams({page:String(page), size:'12', includeDemo:'true', scope});
  if(q.trim())p.set('q',q.trim());
  if(region)p.set('region',region);
  return readJson<ApiPage<OpportunityItem>>(`/api/v1/opportunities?${p}`,signal);
}
export function fetchOpportunity(id:number,signal?:AbortSignal){
  return readJson<OpportunityDetail>(`/api/v1/opportunities/${id}?includeDemo=true`,signal);
}


// V2-3 policy/job subscription rules (main member DB; V2 catalog lives separately).
export type OpportunityAlertRule={id:number;keyword:string|null;kind:string|null;region:string|null;age:number|null;enabled:boolean;createdAt:string};
export type OpportunityAlertEvent={id:number;ruleId:number;catalogRecordId:number;title:string;sourceLabel:string;changeType:'NEW'|'UPDATED';status:'PENDING'|'SENT'|'FAILED';detectedAt:string;deliveredAt:string|null};
export type OpportunityAlertInput={keyword:string|null;kind:string|null;region:string|null;age:number|null};
export function fetchOpportunityRules(signal?:AbortSignal){return readJson<OpportunityAlertRule[]>('/api/v1/opportunity-alerts',signal);}
export function fetchOpportunityEvents(signal?:AbortSignal){return readJson<OpportunityAlertEvent[]>('/api/v1/opportunity-alerts/events',signal);}
async function opportunityRuleWrite(path:string,method:'POST'|'PATCH'|'DELETE',body?:unknown){
  const response=await fetch(path,{method,credentials:'same-origin',
    headers:{'Content-Type':'application/json',Accept:'application/json','X-CSRF-TOKEN':await getCsrfToken()},
    ...(body===undefined?{}:{body:JSON.stringify(body)})});
  if(response.status===401)throw new Error('로그인이 필요해.');
  if(response.status===403){clearCsrf();throw new Error('보안 토큰 오류야. 새로고침 후 다시 시도해 줘.');}
  if(response.status===400)throw new Error('관심 조건을 확인해 줘. 키워드 2~80자, 나이 0~100세로 입력해야 해.');
  if(response.status===409)throw new Error('중복된 규칙이거나 최대 30개를 초과했어.');
  if(!response.ok)throw new Error(`관심 알림 요청 실패 (HTTP ${response.status})`);
  return response;
}
export async function createOpportunityRule(input:OpportunityAlertInput){const response=await opportunityRuleWrite('/api/v1/opportunity-alerts','POST',input);return await response.json() as OpportunityAlertRule;}
export async function toggleOpportunityRule(id:number,enabled:boolean){await opportunityRuleWrite(`/api/v1/opportunity-alerts/${id}`,'PATCH',{enabled});}
export async function deleteOpportunityRule(id:number){await opportunityRuleWrite(`/api/v1/opportunity-alerts/${id}`,'DELETE');}

// V2-9: authenticated personal public GitHub repository subscriptions.
export type MyRepository = {sourceId:number;label:string;resource:string;sourceKey:string;lastCheckedAt:string|null;recordCount:number};
export function fetchMyRepositories(signal?:AbortSignal){return readJson<MyRepository[]>('/api/v1/my-repositories',signal);}
export function fetchMySources(signal?:AbortSignal){return readJson<Source[]>('/api/v1/my-repositories/sources',signal);}
export function fetchMyRecords(page:number,q:string,sourceId:number|null,signal?:AbortSignal){
  const p=new URLSearchParams({page:String(page),size:'10'});
  if(q.trim())p.set('q',q.trim());if(sourceId!==null)p.set('sourceId',String(sourceId));
  return readJson<ApiPage<RecordItem>>(`/api/v1/my-repositories/records?${p}`,signal);
}
export function fetchMyChanges(page:number,size:number,sourceId:number|null,signal?:AbortSignal){
  const p=new URLSearchParams({page:String(page),size:String(size)});
  if(sourceId!==null)p.set('sourceId',String(sourceId));
  return readJson<ApiPage<ChangeItem>>(`/api/v1/my-repositories/changes?${p}`,signal);
}
async function myRepoWrite(path:string,method:'POST'|'DELETE',body?:unknown){
  const res=await fetch(path,{method,credentials:'same-origin',headers:{'Content-Type':'application/json','X-CSRF-TOKEN':await getCsrfToken()},
    ...(body===undefined?{}:{body:JSON.stringify(body)})});
  if(res.status===403){clearCsrf();throw new Error('보안 토큰이 만료됐습니다. 새로고침 후 다시 시도해 주세요.');}
  if(res.status===400)throw new Error('GitHub 저장소 주소를 확인해 주세요. 공개 저장소만 등록할 수 있습니다.');
  if(res.status===409)throw new Error('저장소를 최대 20개까지 등록할 수 있습니다.');
  if(res.status===503)throw new Error('GitHub API 요청 한도를 확인한 후 다시 시도해 주세요.');
  if(!res.ok)throw new Error(`저장소 관리 요청 실패 (HTTP ${res.status})`);
}
export function addMyRepository(url:string){return myRepoWrite('/api/v1/my-repositories','POST',{url});}
export function removeMyRepository(sourceId:number){return myRepoWrite(`/api/v1/my-repositories/${sourceId}`,'DELETE');}


// V2-10: authenticated refresh requests are queued for Airflow; no GitHub token in the browser.
export type GitHubRefreshStatus = {
  id:number; sourceId:number; status:'PENDING'|'RUNNING'|'SUCCESS'|'FAILED';
  requestedAt:string;startedAt:string|null;finishedAt:string|null;
};
export function fetchGitHubRefreshStatus(sourceId:number,signal?:AbortSignal){
  return readJson<GitHubRefreshStatus|null>(`/api/v1/my-repositories/${sourceId}/refresh`,signal);
}
export async function requestGitHubRefresh(sourceId:number):Promise<GitHubRefreshStatus>{
  const res=await fetch(`/api/v1/my-repositories/${sourceId}/refresh`,{
    method:'POST',credentials:'same-origin',
    headers:{'X-CSRF-TOKEN':await getCsrfToken(),Accept:'application/json'}
  });
  if(res.status===401)throw new Error('저장소 최신화는 로그인 후 이용할 수 있어요.');
  if(res.status===403){clearCsrf();throw new Error('보안 토큰이 만료됐습니다. 새로고침해 주세요.');}
  if(res.status===404)throw new Error('내가 등록한 GitHub 저장소만 최신화할 수 있어요.');
  if(!res.ok)throw new Error(`최신화 요청 실패 (HTTP ${res.status})`);
  return await res.json() as GitHubRefreshStatus;
}
