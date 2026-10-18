import {useEffect,useState} from 'react';
import type {FormEvent} from 'react';
import {changeAccountPassword,changeNickname,fetchAccountProfile,fetchDiscordStatus,requestAccountEmailChange,deleteMyAccount} from './api';
import type {AccountProfile,DiscordStatus,Member} from './api';

function formatDate(value:string){
  const date=new Date(value);
  return Number.isNaN(date.getTime())?'알 수 없음':date.toLocaleDateString('ko-KR',{year:'numeric',month:'long',day:'numeric'});
}
export default function AccountPanel({onMemberUpdate,onPasswordChanged,onAccountDeleted}: {
  onMemberUpdate:(member:Member)=>void;
  onPasswordChanged:()=>void;
  onAccountDeleted:()=>void;
}) {
  const [profile,setProfile]=useState<AccountProfile|null>(null);
  const [discord,setDiscord]=useState<DiscordStatus|null>(null);
  const [nickname,setNickname]=useState('');
  const [currentPassword,setCurrentPassword]=useState('');
  const [newPassword,setNewPassword]=useState('');
  const [confirmPassword,setConfirmPassword]=useState('');
  const [newEmail,setNewEmail]=useState('');
  const [emailPassword,setEmailPassword]=useState('');
  const [emailBusy,setEmailBusy]=useState(false);
  const [emailError,setEmailError]=useState('');
  const [emailSuccess,setEmailSuccess]=useState('');
  const [deletePassword,setDeletePassword]=useState('');
  const [deleteConfirmation,setDeleteConfirmation]=useState('');
  const [deleteBusy,setDeleteBusy]=useState(false);
  const [deleteError,setDeleteError]=useState('');
  const [loading,setLoading]=useState(true);
  const [nicknameBusy,setNicknameBusy]=useState(false);
  const [passwordBusy,setPasswordBusy]=useState(false);
  const [error,setError]=useState('');
  const [nicknameError,setNicknameError]=useState('');
  const [nicknameSuccess,setNicknameSuccess]=useState('');
  const [passwordError,setPasswordError]=useState('');
  useEffect(()=>{
    const abort=new AbortController();
    void Promise.all([fetchAccountProfile(abort.signal),fetchDiscordStatus(abort.signal)])
      .then(([p,d])=>{if(!abort.signal.aborted){setProfile(p);setNickname(p.nickname);setDiscord(d);}})
      .catch((e:unknown)=>{if(!abort.signal.aborted)setError(e instanceof Error?e.message:'계정 정보 조회 실패');})
      .finally(()=>{if(!abort.signal.aborted)setLoading(false);});
    return()=>abort.abort();
  },[]);
  async function saveNickname(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(nicknameBusy)return;
    setNicknameBusy(true);setNicknameError('');setNicknameSuccess('');
    try{
      const next=await changeNickname(nickname.trim());
      setProfile(next);setNickname(next.nickname);onMemberUpdate(next);
      setNicknameSuccess('닉네임이 변경되었습니다.');
    }catch(e:unknown){setNicknameError(e instanceof Error?e.message:'닉네임 변경 실패');}
    finally{setNicknameBusy(false);}
  }
  async function savePassword(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(passwordBusy)return;
    setPasswordError('');
    if(newPassword!==confirmPassword){setPasswordError('새 비밀번호 확인이 일치하지 않습니다.');return;}
    if(newPassword.length<12){setPasswordError('새 비밀번호는 최소 12자 이상이어야 합니다.');return;}
    setPasswordBusy(true);
    try{
      await changeAccountPassword(currentPassword,newPassword);
      setCurrentPassword('');setNewPassword('');setConfirmPassword('');
      onPasswordChanged();
    }catch(e:unknown){setPasswordError(e instanceof Error?e.message:'비밀번호 변경 실패');}
    finally{setPasswordBusy(false);}
  }
  async function sendEmailChange(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(emailBusy)return;
    setEmailError('');setEmailSuccess('');setEmailBusy(true);
    try {
      await requestAccountEmailChange(newEmail.trim(),emailPassword);
      setEmailPassword('');
      setEmailSuccess('새 주소로 확인 메일을 요청했습니다. 메일의 링크를 열고 인증해 주세요. 인증 전까지 기존 주소가 유지됩니다.');
    }catch(e:unknown){setEmailError(e instanceof Error?e.message:'이메일 변경 요청 실패');}
    finally{setEmailBusy(false);}
  }
  async function removeAccount(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(deleteBusy||deleteConfirmation!=='DELETE')return;
    // A second explicit confirmation prevents accidental destructive actions.
    if(!window.confirm('회원 계정과 관심 키워드, Discord 연결, 알림 이력을 삭제합니다. 이 작업은 되돌릴 수 없습니다. 진행할까요?'))return;
    setDeleteBusy(true);setDeleteError('');
    try {
      await deleteMyAccount(deletePassword,deleteConfirmation);
      setDeletePassword('');setDeleteConfirmation('');
      onAccountDeleted();
    }catch(e:unknown){setDeleteError(e instanceof Error?e.message:'회원 탈퇴 실패');}
    finally{setDeleteBusy(false);}
  }
  return <div className="account-page">
    {loading&&<section className="panel"><p className="empty">계정 정보를 불러오는 중...</p></section>}
    {error&&<section className="panel"><p className="notice-error" role="alert">{error}</p></section>}
    {profile&&<>
      <section className="panel account-panel">
        <div className="panel-title"><div><h2>내 계정</h2><p>현재 로그인한 계정의 정보입니다.</p></div></div>
        <div className="account-facts">
          <div><span>이메일</span><strong>{profile.email}</strong></div>
          <div><span>이메일 인증</span><strong>{profile.emailVerified?'인증 완료':'인증 필요'}</strong></div>
          <div><span>가입일</span><strong>{formatDate(profile.createdAt)}</strong></div>
          <div><span>Discord 알림</span><strong>{discord?.configured?'연결됨':'연결되지 않음'}</strong></div>
        </div>
        <p className="account-note">새 이메일은 인증 후에만 반영됩니다. Discord 연결 정보는 ‘관심 알림’에서 변경할 수 있습니다.</p>
      </section>
      <section className="panel account-panel">
        <div className="panel-title"><div><h2>닉네임 변경</h2><p>변경 사항은 즉시 계정에 반영됩니다.</p></div></div>
        <form className="account-form" onSubmit={saveNickname}>
          <label>새 닉네임<input type="text" minLength={2} maxLength={40} value={nickname} onChange={e=>setNickname(e.target.value)} required autoComplete="nickname"/></label>
          {nicknameError&&<p className="notice-error" role="alert">{nicknameError}</p>}
          {nicknameSuccess&&<p className="watch-success" role="status">{nicknameSuccess}</p>}
          <button className="watch-submit" type="submit" disabled={nicknameBusy||nickname.trim()===profile.nickname||nickname.trim().length<2}>{nicknameBusy?'저장 중...':'닉네임 저장'}</button>
        </form>
      </section>
      <section className="panel account-panel">
        <div className="panel-title"><div><h2>비밀번호 변경</h2><p>현재 비밀번호를 확인한 뒤 새 비밀번호를 설정합니다.</p></div></div>
        <form className="account-form" onSubmit={savePassword}>
          <label>현재 비밀번호<input type="password" autoComplete="current-password" value={currentPassword} onChange={e=>setCurrentPassword(e.target.value)} required/></label>
          <label>새 비밀번호<input type="password" autoComplete="new-password" minLength={12} value={newPassword} onChange={e=>setNewPassword(e.target.value)} required/></label>
          <label>새 비밀번호 확인<input type="password" autoComplete="new-password" minLength={12} value={confirmPassword} onChange={e=>setConfirmPassword(e.target.value)} required/></label>
          {passwordError&&<p className="notice-error" role="alert">{passwordError}</p>}
          <p className="account-note">12자 이상 · 현재 비밀번호와 다르게 설정해 주세요. 변경 후 현재 브라우저는 로그아웃됩니다.</p>
          <button className="watch-submit" type="submit" disabled={passwordBusy||!currentPassword||newPassword.length<12||!confirmPassword}>{passwordBusy?'변경 중...':'비밀번호 변경'}</button>
        </form>
      </section>
      <section className="panel account-panel">
        <div className="panel-title"><div><h2>이메일 변경</h2><p>새 이메일로 전송된 인증 링크를 완료해야 주소가 변경됩니다.</p></div></div>
        <form className="account-form" onSubmit={sendEmailChange}>
          <label>새 이메일 주소<input type="email" autoComplete="email" maxLength={254} required value={newEmail} onChange={e=>setNewEmail(e.target.value)} placeholder="새로운 이메일 주소" /></label>
          <label>현재 비밀번호<input type="password" autoComplete="current-password" required value={emailPassword} onChange={e=>setEmailPassword(e.target.value)}/></label>
          {emailError&&<p className="notice-error" role="alert">{emailError}</p>}
          {emailSuccess&&<p className="watch-success" role="status">{emailSuccess}</p>}
          <p className="account-note">현재 비밀번호 확인 후 새 이메일로 30분 유효한 링크를 보냅니다. Resend 테스트 계정에서는 다른 수신 주소로 발송이 제한될 수 있습니다.</p>
          <button className="watch-submit" type="submit" disabled={emailBusy||!newEmail||!emailPassword||newEmail.trim().toLowerCase()===profile.email.toLowerCase()}>{emailBusy?'발송 중...':'새 이메일 인증 메일 발송'}</button>
        </form>
      </section>
      <section className="panel account-panel account-danger-panel">
        <div className="panel-title"><div><h2>회원 탈퇴</h2><p>계정과 개인 관심 설정을 복구할 수 없도록 삭제합니다.</p></div></div>
        <div className="account-danger-warning">회원 탈퇴 시 관심 키워드, 알림 이력, Discord 연결 설정과 계정 정보가 삭제됩니다. 공개 GitHub 릴리스 데이터는 유지됩니다.</div>
        <form className="account-form" onSubmit={removeAccount}>
          <label>현재 비밀번호<input type="password" autoComplete="current-password" required value={deletePassword} onChange={e=>setDeletePassword(e.target.value)} /></label>
          <label>확인 문구 입력 (DELETE)<input type="text" autoComplete="off" required value={deleteConfirmation} onChange={e=>setDeleteConfirmation(e.target.value)} placeholder="DELETE"/></label>
          {deleteError&&<p className="notice-error" role="alert">{deleteError}</p>}
          <button className="account-danger-button" type="submit" disabled={deleteBusy||!deletePassword||deleteConfirmation!=='DELETE'}>{deleteBusy?'탈퇴 처리 중...':'회원 탈퇴'}</button>
        </form>
      </section>
    </>}
  </div>;
}
