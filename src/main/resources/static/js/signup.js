$(document).ready(function() {
    // ✅ 중복검사 완료 플래그 추가
    let isIdChecked = false;
    let isNickChecked = false;
    let isEmailChecked = false;
    
    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");
    
    if (token && header) {
        $.ajaxSetup({
            beforeSend: function(xhr) {
                xhr.setRequestHeader(header, token);
            }
        });
    }

    // [1] 아이디 중복 확인
    $("#checkBtn").click(function() {
        let userId = $("#userId").val();
        if (userId === "") {
            $("#checkResult").attr("class", "error-message").text("아이디를 입력하세요.");
            isIdChecked = false;
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
                    isIdChecked = false;
                } else {
                    $("#checkResult")
                        .attr("class", "success-message")
                        .text("사용 가능한 아이디입니다.");
                    isIdChecked = true; // ✅ 중복검사 완료
                }
            },
            error: function() {
                $("#checkResult")
                    .attr("class", "error-message")
                    .text("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
                isIdChecked = false;
            }
        });
    });

    // [2] 닉네임 중복 확인
    $("#nickCheckBtn").click(function() {
        let nickname = $("#userNick").val();
        if (nickname === "") {
            $("#nickCheckResult").attr("class", "error-message").text("닉네임을 입력하세요.");
            isNickChecked = false;
            return;
        }

        $.ajax({
            url: "/check-nickname",
            type: "GET",
            data: { nickname: nickname },
            success: function(response) {
                if (response.available === false) {
                    $("#nickCheckResult")
                        .attr("class", "error-message")
                        .text("이미 사용중인 닉네임입니다. 다른 닉네임을 사용해주세요.");
                    isNickChecked = false;
                } else {
                    $("#nickCheckResult")
                        .attr("class", "success-message")
                        .text("사용 가능한 닉네임입니다.");
                    isNickChecked = true; // ✅ 중복검사 완료
                }
            },
            error: function() {
                $("#nickCheckResult")
                    .attr("class", "error-message")
                    .text("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
                isNickChecked = false;
            }
        });
    });

    // [3] 이메일 중복 확인
    $("#emailCheckBtn").click(function() {
        let email = $("#userEmail").val();
        if (email === "") {
            $("#emailCheckResult").attr("class", "error-message").text("이메일을 입력하세요.");
            isEmailChecked = false;
            return;
        }

        $.ajax({
            url: "/check-email",
            type: "GET",
            data: { email: email },
            success: function(response) {
                if (response.available === false) {
                    $("#emailCheckResult")
                        .attr("class", "error-message")
                        .text("이미 등록된 이메일입니다. 다른 이메일을 사용해주세요.");
                    isEmailChecked = false;
                } else {
                    $("#emailCheckResult")
                        .attr("class", "success-message")
                        .text("사용 가능한 이메일입니다.");
                    isEmailChecked = true; // ✅ 중복검사 완료
                }
            },
            error: function(xhr, status, error) {
                console.log("이메일 체크 오류:", xhr, status, error);
                $("#emailCheckResult")
                    .attr("class", "error-message")
                    .text("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
                isEmailChecked = false;
            }
        });
    });

    // ✅ 입력값 변경 시 중복검사 플래그 초기화
    $("#userId").on("input", function() {
        isIdChecked = false;
        $("#checkResult").text("");
    });

    $("#userNick").on("input", function() {
        isNickChecked = false;
        $("#nickCheckResult").text("");
    });

    $("#userEmail").on("input", function() {
        isEmailChecked = false;
        $("#emailCheckResult").text("");
    });

    // [4] 실시간 비밀번호 확인
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

    // URL 파라미터 오류 메시지 표시
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

    // ✅ validateForm을 전역 함수로 노출
    window.validateForm = function() {
        const pw = document.getElementById("userPw").value;
        const confirmPw = document.getElementById("confirmPw").value;
        const terms = document.getElementById("terms").checked;

        // 1. 아이디 중복검사 확인
        if (!isIdChecked) {
            alert("아이디 중복검사를 진행해주세요.");
            document.getElementById("checkBtn").focus();
            return false;
        }

        // 2. 닉네임 중복검사 확인
        if (!isNickChecked) {
            alert("닉네임 중복검사를 진행해주세요.");
            document.getElementById("nickCheckBtn").focus();
            return false;
        }

        // 3. 이메일 중복검사 확인
        if (!isEmailChecked) {
            alert("이메일 중복검사를 진행해주세요.");
            document.getElementById("emailCheckBtn").focus();
            return false;
        }

        // 4. 비밀번호 일치 여부 확인
        if (pw !== confirmPw) {
            alert("비밀번호가 일치하지 않습니다.");
            document.getElementById("confirmPw").focus();
            return false;
        }

        // 5. 비밀번호 최소 길이 확인
        if (pw.length < 4) {
            alert("비밀번호는 최소 4자 이상이어야 합니다.");
            document.getElementById("userPw").focus();
            return false;
        }

        // 6. 약관 동의 여부 확인
        if (!terms) {
            alert("서비스 이용약관에 동의해야 가입할 수 있습니다.");
            document.getElementById("terms").focus();
            return false;
        }

        // 7. 모든 검증 통과 시 로딩 애니메이션 활성화 및 버튼 비활성화
        const loadingOverlay = document.getElementById("loadingOverlay");
        const submitButton = document.getElementById("submitButton");
        
        if (loadingOverlay) {
            loadingOverlay.style.display = 'flex';
        }
        
        if (submitButton) {
            submitButton.disabled = true;
            submitButton.classList.add('submit-button-loading');
        }

        return true;
    };
});
