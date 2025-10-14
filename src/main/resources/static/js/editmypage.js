// 이미지 미리보기
function previewImage(event) {
    const file = event.target.files[0];
    if (!file) return; // 파일 없으면 종료

    const reader = new FileReader();
    reader.onload = function(e) {
        const preview = document.getElementById('preview');
        if (preview) preview.src = e.target.result;
    };
    reader.readAsDataURL(file);
}

// 비밀번호 확인 (폼 제출 시 사용)
function validatePassword() {
    const pw = document.getElementById('userPw')?.value;
    const confirm = document.getElementById('userPwConfirm')?.value;

    if (pw && pw !== confirm) {
        alert('비밀번호가 일치하지 않습니다.');
        return false;
    }
    return true;
}

// 이메일 중복 확인
function checkEmailDuplicate() {
    const email = document.getElementById('userEmail')?.value;
    if (!email) return alert('이메일을 입력해주세요.');

    fetch(`/check-email?email=${encodeURIComponent(email)}`)
        .then(res => res.json())
        .then(data => {
            if (data.available) alert('사용 가능한 이메일입니다.');
            else alert('이미 사용 중인 이메일입니다.');
        })
        .catch(err => console.error('이메일 체크 오류:', err));
}

// 현재 비밀번호 확인 (AJAX)
async function verifyCurrentPassword() {
    const currentPw = document.getElementById('currentPw')?.value;
    if (!currentPw) return false;

    try {
        const res = await fetch('/verify-password', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: `currentPw=${encodeURIComponent(currentPw)}`
        });
        const data = await res.json();
        return data.valid;
    } catch (err) {
        console.error('비밀번호 검증 오류:', err);
        return false;
    }
}

// 계정 삭제
function deleteAccount() {
    if (!confirm('정말로 계정을 삭제하시겠습니까?')) return;

    fetch('/delete-account', { method: 'POST' })
        .then(res => {
            if (res.ok) {
                alert('계정이 삭제되었습니다.');
                window.location.href = '/';
            } else alert('계정 삭제 중 오류가 발생했습니다.');
        })
        .catch(err => console.error('계정 삭제 오류:', err));
}

// DOMContentLoaded 시 폼 submit 이벤트 연결
document.addEventListener('DOMContentLoaded', () => {
    const form = document.querySelector('form[th\\:action="@{/editMypage}"]');
    if (form) form.onsubmit = validatePassword;
});
