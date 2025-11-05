# 프로젝트 소개

SSAEGIM.AI 는 AI 기반 학습 지원 플랫폼입니다. 사용자는 학습 자료를 업로드하면 AI가 자동으로 요약, 키워드 추출, 폴더 분류를 진행합니다.
벡터 기반 RAG 검색으로 개인화된 강의 추천을 받을 수 있으며, 챗봇과의 실시간 상호작용으로 학습을 지원합니다. 모든 데이터는 클라우드 없이 로컬 환경에서 관리되므로 학교나 기관의 폐쇄망에서도 독립적으로 운영할 수 있습니다.

# 핵심 기능
## 1. AI 학습 요약 기능
- 다양한 파일 형식 지원: PDF, HWP(한글), DOCX, Excel, CSV, MD, TXT
- 문서 크기별 최적화: 소형(일괄), 중형(슬라이딩 윈도우), 대형(의미 기반) 청킹
- 16가지 요약 형식 × 3단계 모드: 심플형, 키워드형, 포트폴리오형 등을 심플/표준/고급으로 제공
- 자동 키워드 추출: TF-IDF + LLM + DB 매칭으로 신뢰도 점수 산출
- 자동 폴더 분류: Jaccard 유사도 + LLM으로 기존 폴더 트리에 자동 배치

## 2. 벡터 DB 및 RAG 시스템
- BGE-M3 임베딩: 1024차원, 100개 이상 언어 지원, CPU 기반 비동기 처리
- PostgreSQL + pgvector: HNSW 인덱스로 밀리초 단위 유사도 검색
- 하이브리드 검색: 텍스트(MySQL) + 벡터(PostgreSQL) + 메타데이터 필터링 결합
- 유사도 임계값: 0.7 이상만 필터링하여 정확도 85% 이상 달성

## 3. AI 문제 은행 시스템
- 7가지 문제 유형: 객관식, 참/거짓, 빈칸, 단답형, 서술형, 연결형, 순서배열
- 5단계 난이도 체계: 원하는 난이도로 선택 가능 / 시험결과 기반 자동 난이도 조절
- 정답 랜덤화: 객관식 선택지를 매번 다르게 섞어 암기 방지
- 자동 채점: 객관식은 문자 일치, 주관식은 DB 매칭 기반
- 통계 추적: test_attempts와 user_answers로 응시 기록 및 정답률 분석

## 4. 노트 관리 시스템
- 계층적 폴더 트리: parentId 필드로 재귀 구조 구현, 무제한 깊이
- 순환 참조 방지: 이동 전 재귀 검사로 폴더를 자신의 하위로 이동 불가
- 메타데이터 추적: 생성일, 수정일, 최종 접근일로 "최근 본 노트" 구현
- 마크다운 지원: 클라이언트 사이드 렌더링으로 서버 부하 감소

## 5. 일정 관리 시스템
- 다양한 일정 유형: 색상으로 개인화 가능
- 반복 일정(RRULE): iCalendar 표준 기반, 종료일까지 인스턴스 사전 생성
- 자동 알림 시스템: @Scheduled로 1분마다 PENDING 알림 조회 및 발송
- 다채널 알림: 이메일 및 푸시 알림 지원, 실패 시 재시도  <-- 현재는 이메일까지만 지원. 추후 푸시 알림 등 지원 예정
- 캘린더 뷰: 월간/주간/일간 조회 지원, toLocalDate() 그룹화

## 6. 강의 목록 관리
- 하이브리드 강의 소스: 외부 링크(유튜브) + 내부 영상(GridFS) 동시 지원
- 키워드 기반 추천: 노트 키워드와 강의 키워드 비교로 관련 강의 자동 추천
- 벡터 검색 통합: 챗봇의 자연어 질문으로 의미 기반 강의 검색
- 온프레미스 지원: GridFS로 대용량 영상 저장, 인터넷 없이도 시청 가능
-  메타데이터 저장: MySQL에 강의명, 강사, 카테고리, 난이도, 키워드 관리

## 7. AI 챗봇 시스템
- RAG 기반 답변: pgvector에서 Top-5 검색(임계값 0.7↑) + 사용자 자료만 검색
- 대화 지속성: MySQL chat_sessions에 userId, messages 배열 저장
- 스마트 링크 삽입: 답변에 키워드 감지 시 [강의 목록], [노트], [시험] 등 클릭 가능 링크 자동 삽입
- 멀티 기능: 학습 지원, 일정 조회, 시험 안내, 노트 검색, 사이트 가이드 통합
- 오류 폴백: 검색 실패 시 일반 지식으로 답변, 자료 업로드 제안

# 기술 스택
## 백엔드
- 프레임워크: Spring Boot 3.x, Spring Data JPA/MongoDB, Spring Security
- AI 모델: LG ExaOne 3.5 7.8B Instruct AWQ (요약), ExaOne 4.0 1.2B AWQ (챗봇)
- 임베딩: BAAI/bge-m3 (1024차원, CPU 기반)
- 배포: vLLM (OpenAI 호환 API), Docker

## 데이터베이스
- MySQL: 메타데이터(강의, 노트 요약, 사용자, 통계)
- PostgreSQL + pgvector: 벡터 저장 및 HNSW 인덱싱
- MongoDB: 문서 저장소(노트 원본, 폴더 트리, 대화 세션)
- GridFS: 대용량 파일 저장(강의 영상, 학습 자료, 30일 자동 삭제)

## 프론트엔드
- JavaScript: Vanilla JS, chatbot.js 모달 팝업
- 마크다운 렌더러: 클라이언트 사이드 변환
- 실시간 통신: SSE(Server-Sent Events) 스트리밍

## 파일 처리
- Apache POI: XLSX, DOCX 파싱
- Apache PDFBox: PDF 추출
- HWP Parser: 한글 문서 지원  <--- 유료 한글과컴퓨터에서 판매하는 API 외에 [hwp.js](https://github.com/hahnlee/hwp.js) 사용.

## 스케줄링 & 비동기
- @Scheduled(cron/fixedRate): 일정 알림 발송
- @EnableScheduling: 스케줄 활성화
- @Async + ThreadPoolTaskExecutor: 벡터 임베딩 비동기 처리(Core 4, Max 8)

# 시스템 아키텍처
## 기술적 도전과제 해결
Challenge 1: 메모리 최적화 (RTX 5080 16GB)
- 상황 분석
초기 개발 단계에서 저희는 단일 7.8B 모델로 문서 요약 기능만 구현하려고 했습니다. 이 모델은 정확도가 높아 복잡한 학습 자료도 정확하게 요약할 수 있었습니다. 그러나 프로젝트가 진행되면서 새로운 기능 추가가 필요해졌습니다. 
사용자들이 "챗봇 기능도 있으면 좋겠다"는 피드백을 주기 시작한 것입니다. 실시간으로 학습 질문에 답해주는 챗봇이 있으면 사용 경험이 훨씬 나아질 것은 분명했습니다.

- 기술적 제약 직면
당연하게 같은 7.8B 모델에 챗봇 기능을 추가하려고 했습니다. 그런데 실제 구현 단계에 들어서자 심각한 문제가 생겼습니다. 
테스트 결과 사용자 A가 대용량 문서를 요약할 때 모델이 20~30초, 길게는 1분간 점유되었습니다. 그 동안 사용자 B가 챗봇 질문을 하면 기다려야 했고, 심각한 경우 요청이 타임아웃되기도 했습니다. 단일 모델 구조로는 두 가지 용도를 동시에 만족시킬 수 없었습니다.

- 메모리 계산과 초기 실패
해결책은 간단해 보였습니다. "작은 모델을 하나 더 올리면 되지 않을까?" 그래서 1.2B 챗봇 모델을 Docker에서 시작하려고 했습니다. 그 순간 문제가 터졌습니다.
***
초기 시도 (둘 다 기본 설정):
7.8B Instruct AWQ: 약 12GB 요구
1.2B AWQ: 약 3.5GB 요구  
시스템 오버헤드: 약 1GB
예상 총 메모리: 16.5GB → RTX 5080의 16GB 초과!
결과: CUDA Out of Memory 에러
핵심 문제: vLLM은 기본적으로 GPU 메모리를 최대한 많이 사용하려고 합니다. 그래서 첫 번째 모델이 거의 모든 메모리를 점유해버립니다.
해결책: GPU 메모리 할당량 제한
저희는 각 모델이 사용할 수 있는 GPU 메모리의 비율을 명시적으로 제한했습니다.

- 핵심 옵션: --gpu-memory-utilization
```
docker run -d `
  --name exaone-3.5-7.8b `
  --gpus all `
  -p 8006:8005 `
  -v D:/models/exaone-3.5-7.8b-instruct-awq:/models/exaone `
  --entrypoint "vllm" `
  vllm/vllm-openai:latest `
  serve /models/exaone `
    --host 0.0.0.0 --port 8005 `
    --trust-remote-code `
    --max-model-len 8192 `
    --gpu-memory-utilization 0.65

docker run -d `
  --name exaone-4.0-chatbot `
  --gpus all `
  -p 8007:8005 `
  -v D:/models/exaone-4.0-1.2b-awq:/models/exaone-4.0 `
  --entrypoint "vllm" `
  vllm/vllm-openai:latest `
  serve /models/exaone-4.0 `
    --host 0.0.0.0 --port 8005 `
    --trust-remote-code `
    --max-model-len 4096 `
    --gpu-memory-utilization 0.25

docker run -d `
  --name embedding `
  -p 8081:8081 `
  -v D:/models/cpu:/models `
  --memory="4g" `
  embedding-bge-m3
```

- 메모리 배분 결과

7.8B 모델: 10.4GB (65%)
1.2B 모델: 4.0GB (25%)
시스템 오버헤드: 1.1GB (10%)
총 사용량: 15.5GB
남는 버퍼: 0.5GB
 16GB 이내로 안정적 운영 가능

## 포트 분산은 왜 했나? (동시 요청 처리)
메모리 문제를 해결한 후, 두 번째 문제가 있었습니다:

문제 상황:
User A: "문서 요약해줘" (7.8B 필요, 30초 소요)
User B: "챗봇 질문" (1.2B 필요, 2초면 충분)

만약 같은 포트에서 처리하면?
→ User A가 먼저 점유 (30초간)
→ User B는 30초 대기 (사용불가능)
이 문제를 해결하려고 포트를 분리한 겁니다.

docker run -d -p 8006:8005 exaone-7.8b
docker run -d -p 8007:8005 exaone-1.2b

결과
User A: 포트 8000에 요청 → 7.8B 작동 (30초)
User B: 포트 8001에 요청 → 1.2B 작동 (2초)
    
   **서로 영향 없음**
## 정리
- 메모리 문제 해결: --gpu-memory-utilization 옵션으로 각 모델의 메모리 사용량 제한
7.8B → 65% (10.4GB)
1.2B → 25% (4GB)
합계 15.5GB로 16GB 내 수용

- 동시 요청 문제 해결: 포트 분산으로 각 모델을 독립 프로세스로 실행
포트 8000 → 7.8B (요약 전담)
포트 8001 → 1.2B (챗봇 전담)
서로 대기하지 않고 병렬 처리

- 상태: 안정적 운영 가능
동시 요청 처리 방식
이 구조의 가장 큰 장점은 완전한 독립성입니다.
시나리오: 동시에 두 요청 발생
```
User A: "이 문서 요약해줘" (5MB 파일)
  ├─ 포트 8006에서 7.8B 모델 실행
  ├─ 처리 시간: 30초
  └─ 이 동안 GPU 메모리의 65% 점유

User B: "스프링이 뭐야?" (챗봇 질문)
  ├─ 포트 8007에서 1.2B 모델 실행
  ├─ 처리 시간: 1초
  ├─ GPU 메모리의 25% 점유 (독립적)
  └─ User A의 요약이 끝날 때까지 기다릴 필요 없음
```
**결과: User A와 User B의 요청이 서로 영향을 주지 않음**


# Challenge 2: 온프레미스 환경 구축

## 상황 분석
개발 초반, 저희는 마켓 기능을 플랫폼에 추가하려고 했습니다. 학생들이 자신의 정리 자료나 강의 영상을 공유하고, 필요한 사람이 구매할 수 있도록 하는 개념이었습니다. 학습 커뮤니티를 형성하고 추가 수익도 창출할 수 있을 것 같았습니다.
하지만 개발이 진행되면서 근본적인 질문이 생겼습니다. "우리 프로젝트의 정체성이 무엇인가?" 다시 생각해보니 몇 가지 문제가 있었습니다.
학습 지원이 메인이어야 하는데 마켓이 커질수록 거래 분쟁, 환불, 저작권 등 처리할 게 너무 많다는 점입니다.
또한 학생들이 경제활동을 하는 장이 되어 순수 학습 목적이 훼손되며, 결제 API, 보안, 감시 체계 등 추가 구현이 필요해진다는 것이었습니다.
결국 저희는 방향을 완전히 틀기로 결정했습니다. 대신 온프레미스 환경 구축에 집중하기로 한 것입니다.

## 온프레미스의 의미
온프레미스란 "자체 서버에서 모든 것을 관리한다"는 뜻입니다. 클라우드 서비스처럼 외부 API에 의존하지 않고, 학교나 기관의 폐쇄망에서도 완벽하게 작동하는 시스템을 만드는 것입니다. 이렇게 하면:
- 인터넷이 안 되는 환경에서도 사용 가능
- 모든 학생 데이터가 학교 서버에만 존재 (개인정보 보호)
- 외부 API 비용 없음 (비용 절감)
- 학교 자체 정책으로 운영 가능 (자율성)

## 핵심 문제: 데이터의 다층적 요구

그런데 단순한 데이터베이스 구조로는 온프레미스 환경을 제대로 지원할 수 없었습니다:
강의를 유튜브 링크로만 저장하면? 인터넷이 없을 때 볼 수 없음
모든 데이터를 MongoDB에 저장하면? 벡터 검색 불가능 (벡터 데이터베이스 미지원)
모든 데이터를 MySQL에 저장하면? 비정형 데이터 관리 어려움, 유연성 부족
한 DB에 다 저장하면? 성능 저하, 인덱싱 어려움
각 데이터 유형에 최적의 DB를 선택해야 했습니다.

## 해결책: 트리플 DB 아키텍처

### 1. MySQL: 빠른 메타데이터 검색


MySQL은 구조화된 데이터를 빠르게 검색하는 데 최적입니다.

저장 정보:
강의 메타데이터 (제목, 강사, 카테고리, 난이도, 키워드)
사용자 정보 및 권한
시험 정보, 통계, 일정
문제 은행 데이터

성능:
키워드 검색: 10~20ms (밀리초)
예: "스프링" 키워드로 검색 → 0.01초 내 결과 반환


인덱싱: userId, category, difficulty로 최적화


```
sql
-- 예시 쿼리
SELECT * FROM lectures 
WHERE keywords LIKE '%스프링%' 
AND category = 'programming'
LIMIT 20;  -- 10ms 내에 완료
```
### 2. PostgreSQL + pgvector: 의미 기반 검색


단순 키워드 매칭만으로는 부족합니다. 사용자가 "자바 기초 배울 수 있는 강의" 같은 자연어로 검색할 때, "Java basics tutorial"도 같은 의미로 인식해야 합니다. 이를 위해 pgvector를 사용합니다.


동작 방식:

강의 정보 벡터화: 제목 + 설명 + 키워드 → BGE-M3 → 1024차원 벡터

저장: PostgreSQL pgvector에 저장

인덱싱: HNSW(Hierarchical Navigable Small World) 인덱스 적용

검색: 코사인 유사도로 가장 비슷한 강의 찾기


성능:
검색 속도: 30~50ms (수십ms)
정확도: 85% 이상
Top-5 검색 시간: 50ms 내에 완료


```
sql
-- pgvector 예시
SELECT lecture_id, 
       1 - (embedding <=> query_vector) AS similarity
FROM lecture_embeddings
WHERE 1 - (embedding <=> query_vector) > 0.7  -- 임계값
ORDER BY similarity DESC
LIMIT 5;  -- 50ms 내에 완료
```


### 3. MongoDB + GridFS: 유연한 문서 저장


영상, 이미지, PDF, 학습 원본 데이터 등 대용량 비정형 데이터는 MongoDB의 GridFS에 저장합니다.


저장 데이터:

강의 영상 (5MB 청크 단위로 분할)

노트 원본 파일

학습 자료 첨부파일

폴더 트리 구조 (parentId 필드로 무제한 깊이)

챗봇 대화 세션

30일 자동 삭제 정책

모든 파일에 생성 시간(createdAt)을 기록하고, 매일 자동으로 30일이 지난 파일을 삭제합니다.


```
// MongoDB 예시
db.fs.files.deleteMany({
  uploadDate: {$lt: new Date(Date.now() - 30*24*60*60*1000)}
});
```


이렇게 하면 저장 공간이 무한 증가하지 않음

사용자는 30일 동안 충분히 파일 활용 가능

서버 스토리지 효율화


### 동영상 저장 및 재생

일반 DB에 동영상을 저장하면 파일 크기가 너무 커집니다. GridFS는 파일을 5MB 청크 단위로 나눠 저장하고, 필요할 때 청크 단위로 스트리밍합니다.

```
사용자가 강의 영상 재생 요청
  ↓
GridFS에서 첫 번째 청크(0~5MB) 로드
  ↓
사용자 화면에 스트리밍 시작
  ↓
시청하는 동안 다음 청크 미리 로드
  ↓
인터넷 없이도 로컬에서 완벽하게 재생
```
장점:
메모리 효율적 (전체 파일을 메모리에 로드하지 않음)
느린 인터넷에서도 재생 가능
중단된 재생 이어서 시청 가능

## 하이브리드 검색 흐름
이 3가지 DB를 조합하면 강력한 검색 시스템이 됩니다:

```
사용자 질문: "스프링 웹 개발 강의 추천해줘"
  ↓
1단계: MySQL 키워드 검색 (10ms)
   ├─ keywords LIKE '%스프링%' 검색
   └─ 15개의 관련 강의 추출
  ↓
2단계: PostgreSQL 벡터 검색 (50ms)
   ├─ 질문을 벡터화
   ├─ 코사인 유사도 계산
   └─ 5개의 의미 유사 강의 추출
  ↓
3단계: 결과 병합 및 정렬
   ├─ 중복 제거
   ├─ 최종 점수 계산
   └─ Top 5 강의 반환
  ↓
4단계: MongoDB에서 강의 상세 정보 조회
   ├─ 강의 설명, 이미지
   ├─ 강의 영상 GridFS ID 추출
   └─ 최종 결과 사용자에게 제공
  ↓
사용자가 강의 선택 → GridFS에서 영상 스트리밍
```


## 온프레미스의 최종 형태

결과적으로 저희는 다음을 달성했습니다.

- 완전한 자립성: 클라우드 API 0개 사용
- 보안: 모든 학생 데이터가 학교 서버에만 존재
- 비용 절감: 클라우드 스토리지 비용 0원
- 폐쇄망 호환: 인터넷이 없는 학교에서도 완벽 작동
- 검색 정확도: 하이브리드 방식으로 85% 이상 달성
- 확장성: 용량 자동 관리로 무한 운영 가능

# 설치 및 실행
## 사전 요구사항
- Docker & Docker Compose
- RTX 5080 (또는 16GB+ VRAM GPU)
- Python 3.10+
- Java 17+
- 디스크 공간: 최소 200GB (모델 + DB)
- SSL 인증서 (HTTPS 사용)
***


## Docker 컨테이너 구성
1. MySQL (메타데이터 저장소)     <= postgreSQL로 병합 가능. 학원에서 부여해준 프로젝트라 학원 DB 사용.

```
docker run -d `
  --name ssaegim-mysql `
  -p 3312:3306 `
  -e MYSQL_ROOT_PASSWORD=your_root_password `
  -e MYSQL_DATABASE=sc_25K_LI4_p3_2 `
  -e MYSQL_USER=sc_25K_LI4_p3_2 `
  -e MYSQL_PASSWORD=smhrd2 `
  -v D:\data\mysql:/var/lib/mysql `
  mysql:8.0
```

2. MongoDB (문서 저장소 + GridFS)


```
docker run -d `
  --name ssaegim-mongodb `
  -p 27017:27017 `
  -e MONGO_INITDB_ROOT_USERNAME=smhrd `
  -e MONGO_INITDB_ROOT_PASSWORD=0S3M1H5RD `
  -e MONGO_INITDB_DATABASE=ssaegim `
  -v D:\data\mongodb:/data/db `
  mongo:latest
```
3. PostgreSQL + pgvector (벡터 저장소)

```
docker run -d `
  --name ssaegim-postgres `
  -p 5432:5432 `
  -e POSTGRES_USER=smhrd `
  -e POSTGRES_PASSWORD=0S3M1H5RD `
  -e POSTGRES_DB=ssaegim `
  -v D:\data\postgres:/var/lib/postgresql/data `
  pgvector/pgvector:pg16
```

4. vLLM - 7.8B 모델 (요약)


```
docker run -d `
  --name ssaegim-vllm-7.8b `
  --gpus all `
  -p 8006:8005 `
  -v D:\models\exaone-3.5-7.8b:/models/exaone `
  vllm/vllm-openai:latest `
  python -m vllm.entrypoints.openai_api_server `
    --model /models/exaone `
    --host 0.0.0.0 `
    --port 8005 `
    --trust-remote-code `
    --max-model-len 8192 `
    --gpu-memory-utilization 0.65 `
    --dtype float16
```

5. vLLM - 1.2B 모델 (챗봇)

```
docker run -d `
  --name ssaegim-vllm-1.2b `
  --gpus all `
  -p 8007:8005 `
  -v D:\models\exaone-4.0-1.2b:/models/exaone-4.0 `
  vllm/vllm-openai:latest `
  python -m vllm.entrypoints.openai_api_server `
    --model /models/exaone-4.0 `
    --host 0.0.0.0 `
    --port 8005 `
    --trust-remote-code `
    --max-model-len 4096 `
    --gpu-memory-utilization 0.25 `
    --dtype float16
```
6. BGE-M3 임베딩 (CPU 기반)


```
docker run -d `
  --name ssaegim-embedding `
  -p 8081:8081 `
  -v D:\models\bge-m3:/models `
  --memory="4g" `
  -e MODEL_NAME=bge-m3 `
  embedding-bge-m3
```

## Spring Boot 빌드 및 실행
1. SSL 인증서 준비  :   Win ACME 사용해서 해결봄. tplinkdns 주소는 전세계에 사용자들이 너무많아서 선착순 풀릴때 바로해야할것;

2. Maven 빌드 및 실행

 ```
# 1. 프로젝트 클론
git clone https://github.com/2025-SMHRD-KDT-LangIntelligence-4/Noteflow.git
cd Noteflow

# 2. 의존성 다운로드 및 빌드
mvn clean install -DskipTests

# 3. Spring Boot 애플리케이션 실행
mvn spring-boot:run

# 또는 JAR 파일로 빌드 후 실행
mvn clean package -DskipTests
java -jar target/noteflow-1.0.0.jar
```

3. 환경 변수 설정 (PowerShell)
```
$env:SPRING_PROFILES_ACTIVE="production"
$env:SPRING_DATASOURCE_URL="jdbc:mysql:///        sc_25K_LI4_p3_2
$env:SPRING_DATASOURCE_USERNAME=
$env:SPRING_DATASOURCE_PASSWORD=
```

| 서비스                 | 포트    | 설명        |
| ------------------- | ----- | --------- |
| Spring Boot (HTTPS) | 443   | 메인 애플리케이션 |
| MySQL               | 3312  | 메타데이터 저장소 |
| MongoDB             | 27017 | 문서 저장소    |
| PostgreSQL          | 5432  | 벡터 저장소    |
| vLLM 7.8B           | 8006  | 요약 모델 API |
| vLLM 1.2B           | 8007  | 챗봇 모델 API |
| BGE-M3              | 8081  | 임베딩 API   |


연결테스트
```
# 요약 모델 상태 확인
Invoke-WebRequest -Uri "http://localhost:8006/v1/models" -Method Get

# 챗봇 모델 상태 확인
Invoke-WebRequest -Uri "http://localhost:8007/v1/models" -Method Get

# 임베딩 서비스 상태 확인
Invoke-WebRequest -Uri "http://localhost:8081/health" -Method Get

# MySQL 연결 확인
docker exec ssaegim-mysql mysql -u sc_25K_LI4_p3_2 -psmhrd2 -e "SELECT 1"

# MongoDB 연결 확인
docker exec ssaegim-mongodb mongosh --host localhost:27017 -u smhrd -p 0S3M1H5RD --authenticationDatabase admin --eval "db.adminCommand('ping')"

# PostgreSQL 연결 확인
docker exec ssaegim-postgres psql -U smhrd -d ssaegim -c "SELECT 1"
```

# 보안 주의사항


1. 기본 패스워드 변경

  - MySQL 루트 패스워드

  - MongoDB 인증 정보

  - PostgreSQL 패스워드

  - Spring Security 기본 사용자명/비밀번호

2. SSL 인증서

  - 자체 서명 인증서 → 공인 인증서 교체

  - 인증서 만료 전 갱신 체계 구축

3. 이메일 설정

  - 실제 Gmail 계정 및 앱 비밀번호 사용

  - 외부에 노출되지 않도록 환경 변수로 관리

4. 데이터베이스

  - 정기적 백업 체계 구축

  - 접근 권한 최소화

5. 로깅

  - 프로덕션: INFO 레벨로 설정

  - 민감한 정보(패스워드 등) 로깅 제거
