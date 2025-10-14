/**
 * 회원가입 유효성 및 중복검사 스크립트
 * - 아이디 중복 검사
 * - 이메일 중복 검사
 * - 비밀번호 일치 확인
 * - 약관 동의 확인
 */

$(document).ready(function() {

	// -------------------------------------------------------------------
	// [1] 아이디 중복 확인
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
	// [2] 이메일 중복 확인
	// -------------------------------------------------------------------
	$("#emailCheckBtn").click(function() {
		let email = $("#userEmail").val();

		if (email === "") {
			$("#emailCheckResult").attr("class", "error-message").text("이메일을 입력하세요.");
			return;
		}

		$.ajax({
			url: "/checkEmail",
			type: "GET",
			data: { email: email },
			success: function(response) {
				if (response === true) {
					$("#emailCheckResult")
						.attr("class", "error-message")
						.text("이미 등록된 이메일입니다. 다른 이메일을 사용해주세요.");
				} else {
					$("#emailCheckResult")
						.attr("class", "success-message")
						.text("사용 가능한 이메일입니다.");
				}
			},
			error: function() {
				$("#emailCheckResult")
					.attr("class", "error-message")
					.text("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
			}
		});
	});
});

// -------------------------------------------------------------------
// [3] 폼 유효성 검사 (비밀번호 + 약관 동의 확인)
// -------------------------------------------------------------------
function validateForm() {
	const pw = document.getElementById("userPw").value;
	const confirmPw = document.getElementById("confirmPw").value;

	// --- 비밀번호 일치 여부 확인
	if (pw !== confirmPw) {
		alert("비밀번호가 일치하지 않습니다.");
		return false;
	}

	// --- 약관 동의 여부 확인
	if (!document.getElementById("terms").checked) {
		alert("약관에 동의해야 가입할 수 있습니다.");
		return false;
	}

	return true; // 모든 검증 통과 시 폼 전송
}
