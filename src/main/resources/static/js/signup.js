/**
 * 회원가입 유효성 및 중복검사 스크립트
 * - 아이디 중복 검사
 * - 이메일 중복 검사  
 * - 닉네임 중복 검사
 * - 비밀번호 일치 확인
 * - 약관 동의 확인
 */

$(document).ready(function() {
    // ✅ CSRF 토큰 설정
    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");
    
    if (token && header) {
        $.ajaxSetup({
            beforeSend: function(xhr) {
                xhr.setRequestHeader(header, token);
            }
        });
    }

    // -------------------------------------------------------------------
    // [1] 아이디 중복 확인 (ID: userId, checkBtn, checkResult)
    // -------------------------------------------------------------------
    $("#checkBtn").click(function() {
        let userId = $("#userId").val();
        if (userId === "") {
            $("#checkResult").attr("class", "error-message").text("아이디를 입력하세요.");
            return;
        }

        $.ajax({
            url: "/checkId",
            type: "GET",
            data: { userId: userId },
            success: function(response) {
                if (response === true) {
                    $("#checkResult")
                        .attr("class", "error-message")
                        .text("이미 사용 중인 아이디입니다. 다른 아이디를 입력해주세요.");
                } else {
                    $("#checkResult")
                        .attr("class", "success-message")
                        .text("사용 가능한 아이디입니다.");
                }
            },
            error: function() {
                $("#checkResult")
                    .attr("class", "error-message")
                    .text("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            }
        });
    });

    // -------------------------------------------------------------------
    // [2] 닉네임 중복 확인 (ID: userNick, nickCheckBtn, nickCheckResult)
    // -------------------------------------------------------------------
    $("#nickCheckBtn").click(function() {
        let nickname = $("#userNick").val();
        if (nickname === "") {
            $("#nickCheckResult").attr("class", "error-message").text("닉네임을 입력하세요.");
            return;
        }

        $.ajax({
            url: "/check-nickname",
            type: "GET",
            data: { nickname: nickname },
            success: function(response) {
                // ✅ UserController 응답 형식에 맞춤
                if (response.available === false) {
                    $("#nickCheckResult")
                        .attr("class", "error-message")
                        .text("이미 사용중인 닉네임입니다. 다른 닉네임을 사용해주세요.");
                } else {
                    $("#nickCheckResult")
                        .attr("class", "success-message")
                        .text("사용 가능한 닉네임입니다.");
                }
            },
            error: function() {
                $("#nickCheckResult")
                    .attr("class", "error-message")
                    .text("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            }
        });
    });

    // -------------------------------------------------------------------
    // [3] 이메일 중복 확인 (ID: userEmail, emailCheckBtn, emailCheckResult)
    // -------------------------------------------------------------------
    $("#emailCheckBtn").click(function() {
        let email = $("#userEmail").val();
        if (email === "") {
            $("#emailCheckResult").attr("class", "error-message").text("이메일을 입력하세요.");
            return;
        }

        $.ajax({
            url: "/check-email", // ✅ 올바른 API 경로
            type: "GET",
            data: { email: email },
            success: function(response) {
                // ✅ UserController 응답 형식에 맞춤
                if (response.available === false) {
                    $("#emailCheckResult")
                        .attr("class", "error-message")
                        .text("이미 등록된 이메일입니다. 다른 이메일을 사용해주세요.");
                } else {
                    $("#emailCheckResult")
                        .attr("class", "success-message")
                        .text("사용 가능한 이메일입니다.");
                }
            },
            error: function(xhr, status, error) {
                console.log("이메일 체크 오류:", xhr, status, error);
                $("#emailCheckResult")
                    .attr("class", "error-message")
                    .text("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            }
        });
    });

    // -------------------------------------------------------------------
    // [4] 실시간 비밀번호 확인 (선택사항)
    // -------------------------------------------------------------------
    $("#confirmPw").on("input", function() {
        const password = $("#userPw").val();
        const confirmPassword = $(this).val();
        
        if (confirmPassword !== "") {
            if (password === confirmPassword) {
                $(this).removeClass("error").addClass("success");
            } else {
                $(this).removeClass("success").addClass("error");
            }
        }
    });
    
    // ✅ URL 파라미터 오류 메시지 표시
    const urlParams = new URLSearchParams(window.location.search);
    
    if (urlParams.has('error')) {
        const errorType = urlParams.get('error');
        let message = '';
        
        switch(errorType) {
            case 'emailDuplicate':
                message = '이미 등록된 이메일입니다.';
                break;
            case 'duplicate':
                message = '이미 등록된 사용자입니다.';
                break;
            case 'userIdDuplicate':
                message = '이미 사용중인 아이디입니다.';
                break;
            default:
                message = '회원가입 중 오류가 발생했습니다.';
        }
        
        alert('❌ ' + message);
    }
});

// -------------------------------------------------------------------
// [5] 폼 제출 시 유효성 검사 (HTML에서 onsubmit으로 호출)
// -------------------------------------------------------------------
function validateForm() {
    const pw = document.getElementById("userPw").value;
    const confirmPw = document.getElementById("confirmPw").value;
    const terms = document.getElementById("terms").checked;

    // 비밀번호 일치 여부 확인
    if (pw !== confirmPw) {
        alert("비밀번호가 일치하지 않습니다.");
        document.getElementById("confirmPw").focus();
        return false;
    }

    // 비밀번호 최소 길이 확인 (선택사항)
    if (pw.length < 4) {
        alert("비밀번호는 최소 4자 이상이어야 합니다.");
        document.getElementById("userPw").focus();
        return false;
    }

    // 약관 동의 여부 확인  
    if (!terms) {
        alert("서비스 이용약관에 동의해야 가입할 수 있습니다.");
        document.getElementById("terms").focus();
        return false;
    }

    // 모든 검증 통과
    return true;
}
