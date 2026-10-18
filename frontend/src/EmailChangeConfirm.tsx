import {useState} from 'react';
import {confirmAccountEmailChange} from './api';

export default function EmailChangeConfirm({token,onDone}: {token:string;onDone:()=>void}) {
  const [busy,setBusy]=useState(false);
  const [success,setSuccess]=useState(false);
  const [error,setError]=useState('');
  async function confirm(){
    if(busy)return;
    setBusy(true);setError('');
    try {
      await confirmAccountEmailChange(token);
      setSuccess(true);
      // A successful server-side confirmation invalidates this browser's session.
      window.history.replaceState({},'',window.location.pathname);
    } catch(e:unknown) {setError(e instanceof Error?e.message:'이메일 변경에 실패했습니다.');}
    finally {setBusy(false);}
  }
  return <section className="panel auth-panel" aria-label="새 이메일 주소 변경 확인">
    <span className="auth-badge">SIGNALBRIDGE ACCOUNT</span>
    <h2>이메일 주소 변경</h2>
    {success?<>
      <p className="watch-success" role="status">새 이메일로 변경됐습니다. 새 주소로 다시 로그인해 주세요.</p>
      <button type="button" className="auth-primary" onClick={onDone}>로그인 화면으로</button>
    </>:<>
      <p>새 주소의 소유권을 확인한 후 기존 계정의 이메일만 변경합니다. 관심 키워드와 Discord 설정은 유지됩니다.</p>
      {error&&<p className="notice-error" role="alert">{error}</p>}
      <button type="button" className="auth-primary" disabled={busy} onClick={()=>void confirm()}>{busy?'확인 중...':'새 이메일 인증 완료하기'}</button>
      <small>링크는 30분 동안 유효하며 한 번만 사용할 수 있습니다.</small>
    </>}
  </section>;
}
