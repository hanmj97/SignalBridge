import {useEffect,useState} from 'react';
import type {FormEvent} from 'react';
import {loginMember,registerMember,resendVerification,verifyEmail,requestPasswordReset,confirmPasswordReset} from './api';
import type {Member} from './api';

export default function AuthPanel({onLogin}: {onLogin:(member:Member)=>void}) {
  const [register,setRegister]=useState(false);
  const [resetMode,setResetMode]=useState(false);
  const [resetToken,setResetToken]=useState(()=>new URLSearchParams(window.location.search).get('resetPassword'));
  const [resetNext,setResetNext]=useState('');
  const [resetConfirm,setResetConfirm]=useState('');
  const [awaitingVerification,setAwaitingVerification]=useState(false);
  const [email,setEmail]=useState('');
  const [nickname,setNickname]=useState('');
  const [password,setPassword]=useState('');
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const [success,setSuccess]=useState('');
  const [verificationToken,setVerificationToken]=useState(()=>new URLSearchParams(window.location.search).get('verifyEmail'));
  // Remove reset tokens from the address bar after parsing, to avoid accidental sharing.
  useEffect(()=>{
    if(!resetToken)return;
    const url=new URL(window.location.href);
    url.searchParams.delete('resetPassword');
    window.history.replaceState({},'',url.pathname+url.search+url.hash);
  },[resetToken]);
  async function requestRecovery(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(busy)return;
    setBusy(true);setError('');setSuccess('');
    try {
      await requestPasswordReset(email.trim());
      setSuccess('해당 이메일로 인증된 계정이 있다면 비밀번호 재설정 메일이 발송됩니다. 메일함을 확인해 주세요.');
    }catch(err:unknown){setError(err instanceof Error?err.message:'재설정 메일 요청 실패');}
    finally{setBusy(false);}
  }
  async function completePasswordReset(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(busy||!resetToken)return;
    setError('');setSuccess('');
    if(resetNext!==resetConfirm){setError('새 비밀번호 확인이 일치하지 않습니다.');return;}
    if(resetNext.length<12){setError('새 비밀번호는 12자 이상이어야 합니다.');return;}
    setBusy(true);
    try {
      await confirmPasswordReset(resetToken,resetNext);
      setResetToken(null);setResetMode(false);setRegister(false);
      setResetNext('');setResetConfirm('');setPassword('');
      setSuccess('비밀번호가 변경됐습니다. 새 비밀번호로 로그인해 주세요.');
    }catch(err:unknown){setError(err instanceof Error?err.message:'비밀번호 재설정 실패');}
    finally{setBusy(false);}
  }
  async function submit(e:FormEvent<HTMLFormElement>) {
    e.preventDefault();if(busy)return;setError('');setSuccess('');setBusy(true);
    try {
      if(register) {
        await registerMember(email.trim(),nickname.trim(),password);
        setRegister(false);setAwaitingVerification(true);setPassword('');setSuccess('회원가입 요청을 받았어요. 이메일로 보낸 인증 링크를 확인한 후 로그인해 주세요.');
      } else {const member=await loginMember(email.trim(),password);setPassword('');onLogin(member);}
    } catch(err:unknown){setError(err instanceof Error?err.message:'계정 요청에 실패했습니다.');}
    finally{setBusy(false);}
  }
  async function requestResend() {
    if(!email.trim()){setError('가입할 때 사용한 이메일을 입력해 주세요.');return;}
    setBusy(true);setError('');setSuccess('');
    try {
      await resendVerification(email.trim());
      setSuccess('대상 계정이 미인증 상태라면 새 인증 메일이 발송됩니다. (재발송 간격: 2분)');
    } catch(err:unknown){setError(err instanceof Error?err.message:'재발송 요청 실패');}
    finally{setBusy(false);}
  }
  async function completeVerification() {
    if(!verificationToken || busy)return;
    setBusy(true);setError('');setSuccess('');
    try {
      const result=await verifyEmail(verificationToken);
      const url=new URL(window.location.href);
      url.searchParams.delete('verifyEmail');
      window.history.replaceState({},'',url.pathname+url.search+url.hash);
      setVerificationToken(null);setRegister(false);setAwaitingVerification(false);
      setSuccess(result.message);
    } catch(err:unknown){setError(err instanceof Error?err.message:'인증 링크 확인 실패');}
    finally{setBusy(false);}
  }
  return <section className="panel auth-panel" aria-label="회원 로그인 및 이메일 인증">
    <div className="auth-badge">SIGNALBRIDGE ACCOUNT</div>
    <h2>{resetToken?'비밀번호 재설정':resetMode?'비밀번호 찾기':verificationToken?'이메일 주소 인증':awaitingVerification?'인증 메일을 확인해 주세요':register?'새 계정 만들기':'관심 알림을 사용하려면 로그인하세요'}</h2>
    <p>공개 릴리스는 로그인 없이 조회할 수 있어요. 관심 키워드는 내 계정에만 저장됩니다.</p>
    {resetToken ? <form className="auth-form" onSubmit={e=>void completePasswordReset(e)}>
      <p>인증 링크를 확인했어요. 새 비밀번호를 설정해 주세요.</p>
      <label>새 비밀번호<input type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={resetNext} onChange={e=>setResetNext(e.target.value)} placeholder="12자 이상"/></label>
      <label>새 비밀번호 확인<input type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={resetConfirm} onChange={e=>setResetConfirm(e.target.value)} placeholder="새 비밀번호 다시 입력"/></label>
      {error&&<p className="notice-error" role="alert">{error}</p>}
      <button type="submit" className="auth-primary" disabled={busy}>{busy?'변경 중...':'비밀번호 재설정 완료'}</button>
      <small>링크는 30분 동안 유효하며 한 번만 사용할 수 있습니다.</small>
    </form> : resetMode ? <form className="auth-form" onSubmit={e=>void requestRecovery(e)}>
      <p>가입 시 인증한 이메일을 입력하면 재설정 링크를 보내드려요.</p>
      <label>가입 이메일<input type="email" autoComplete="email" required maxLength={254} value={email} onChange={e=>setEmail(e.target.value)} placeholder="name@example.com"/></label>
      {error&&<p className="notice-error" role="alert">{error}</p>}
      {success&&<p className="watch-success" role="status">{success}</p>}
      <button type="submit" className="auth-primary" disabled={busy}>{busy?'요청 중...':'비밀번호 재설정 메일 받기'}</button>
      <button type="button" className="auth-signin" onClick={()=>{setResetMode(false);setError('');setSuccess('');}}>로그인 화면으로 돌아가기</button>
    </form> : verificationToken ? <div className="auth-form">
      <p>메일에서 링크를 열었어요. 아래 버튼을 눌러 이메일 인증을 완료해 주세요.</p>
      {error&&<p className="notice-error" role="alert">{error}</p>}
      <button type="button" className="auth-primary" disabled={busy} onClick={()=>void completeVerification()}>{busy?'인증 중...':'이메일 인증 완료하기'}</button>
      <small>링크는 발송 시점부터 30분간 유효하며 1회만 사용할 수 있습니다.</small>
    </div> : awaitingVerification ? <div className="auth-form">
      <p>가입 시 입력한 이메일로 보낸 인증 링크를 확인해 주세요. 인증을 완료한 뒤 로그인할 수 있어요.</p>
      {error&&<p className="notice-error" role="alert">{error}</p>}
      {success&&<p className="watch-success" role="status">{success}</p>}
      <button type="button" className="auth-primary" onClick={()=>void requestResend()} disabled={busy||!email.trim()}>{busy?'요청 중...':'인증 메일 재발송'}</button>
      <div className="auth-tabs">
        <button type="button" className="selected" onClick={()=>{setAwaitingVerification(false);setRegister(false);setError('');setSuccess('');}}>로그인 화면으로 이동</button>
      </div>
    </div> : <>
      <div className="auth-tabs">
        <button type="button" className={!register?'selected':''} onClick={()=>{setRegister(false);setError('');setSuccess('');}}>로그인</button>
        <button type="button" className={register?'selected':''} onClick={()=>{setRegister(true);setError('');setSuccess('');}}>회원가입</button>
      </div>
      <form onSubmit={submit} className="auth-form">
        <label>이메일<input type="email" autoComplete="email" required value={email} onChange={e=>setEmail(e.target.value)} maxLength={254} placeholder="name@example.com"/></label>
        {register&&<label>닉네임<input type="text" autoComplete="nickname" required minLength={2} maxLength={40} value={nickname} onChange={e=>setNickname(e.target.value)} placeholder="2~40자"/></label>}
        <label>비밀번호<input type="password" autoComplete={register?'new-password':'current-password'} minLength={register?12:1} maxLength={72} required value={password} onChange={e=>setPassword(e.target.value)} placeholder={register?'12자 이상, 72자 이하':'비밀번호 입력'}/></label>
        {error&&<p className="notice-error" role="alert">{error}</p>}
        {success&&<p className="watch-success" role="status">{success}</p>}
        <button type="submit" className="auth-primary" disabled={busy}>{busy?'처리 중...':register?'인증 메일 받고 가입하기':'로그인'}</button>
      </form>
      {!register&&<div style={{marginTop:12,textAlign:'right'}}><button className="auth-signin" type="button" onClick={()=>{setResetMode(true);setError('');setSuccess('');setPassword('');}}>비밀번호를 잊으셨나요?</button></div>}
      {register&&<div className="auth-form" style={{marginTop:14}}>
        <p>인증 메일이 도착하지 않았나요? 가입한 이메일을 입력하면 재발송할 수 있어요.</p>
        <button type="button" className="auth-primary" onClick={()=>void requestResend()} disabled={busy||!email.trim()}>{busy?'요청 중...':'인증 메일 재발송'}</button>
      </div>}
    </>}
    <small>이메일 인증을 완료한 계정만 로그인할 수 있습니다.</small>
  </section>;
}
