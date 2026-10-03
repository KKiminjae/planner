# 로그인과 소유자 검증

## 계정 설정

1인용 앱 기준으로 서버 환경에 단일 계정을 설정합니다. 회원가입 API는 없습니다.
계정의 소유자 ID는 1이며, 계정 이름을 바꿔도 기존 데이터의 소유자 ID는 유지합니다.
V5 마이그레이션은 기존 카테고리·사진에 소유자 ID 1을 지정합니다.
기록과 완료 이력은 카테고리를 통해 소유자를 구분합니다.

```text
APP_LOGIN_USERNAME=owner
APP_LOGIN_PASSWORD_HASH=BCrypt_해시
SESSION_COOKIE_SECURE=false
```

비밀번호 해시가 비어 있으면 로그인 가능한 계정이 없으며, 기본 비밀번호나
Spring의 자동 생성 비밀번호로 접근할 수 없습니다. 잘못된 해시 설정은 기동 시 거부합니다.
계정 이름은 1~50자의 영문·숫자·밑줄·점·하이픈을 지원합니다.

현재 개발 Mac에 설치된 `htpasswd`로 비밀번호를 화면에 표시하지 않고 해시를 만들 수 있습니다.

```bash
htpasswd -nBC 12 owner
```

프롬프트에 비밀번호를 두 번 입력하고 출력의 `owner:` 뒤 BCrypt 해시만 사용합니다.
서버 환경 변수 또는 IDE 실행 설정에 등록하며 비밀번호·해시를 저장소에 커밋하지 않습니다.
셸에서 해시를 설정할 때는 `$` 확장을 막도록 작은따옴표로 감쌉니다.
프로젝트의 `.env` 파일은 Spring이 자동으로 읽지 않으므로 실행 환경에 값을 전달해야 합니다.
HTTPS 배포에서는 `SESSION_COOKIE_SECURE=true`로 설정합니다.

## API와 호출 순서

| API | 용도 | 인증 |
| --- | --- | --- |
| GET /api/auth/csrf | 변경 요청용 CSRF 토큰 | 로그인 전에도 가능 |
| POST /api/auth/login | 단일 계정 로그인 | CSRF 토큰 필요 |
| GET /api/auth/me | 로그인한 계정 확인 | 로그인 필요 |
| POST /api/auth/logout | 세션 무효화·쿠키 삭제 | CSRF 토큰 필요 |

1. `GET /api/auth/csrf`를 호출하고 세션 쿠키를 보관합니다.
2. 응답의 `headerName`에 `token`을 넣고 같은 쿠키로 로그인 요청을 보냅니다.
3. 로그인 성공 후 CSRF 토큰이 갱신되므로 `GET /api/auth/csrf`를 다시 호출합니다.
4. 이후 POST·PATCH·DELETE에 세션 쿠키와 새 CSRF 토큰을 전달합니다. multipart 사진 업로드도 동일합니다.
5. 로그아웃 후 로그인하려면 CSRF 토큰과 세션을 다시 준비합니다.

로그인 요청은 `application/x-www-form-urlencoded`의 `username`, `password` 필드입니다.
JSON 로그인 본문은 지원하지 않습니다. curl·Postman에서는 세션 쿠키를 유지하고,
브라우저에서 별도 origin을 사용할 경우 쿠키·CORS 설정이 추가로 필요합니다.
현재 앱은 동일 origin 구성을 기준으로 하며 임의의 CORS 허용은 추가하지 않았습니다.

CSRF 응답:

```json
{"headerName":"X-CSRF-TOKEN","token":"변경_요청에_사용할_토큰"}
```

로그인 성공 및 계정 확인 응답:

```json
{"ownerId":1,"username":"owner"}
```

로그인은 200, 로그아웃은 204입니다. 비밀번호·해시는 응답에 포함하지 않습니다.
로그인 성공 시 세션 ID를 변경하며 로그아웃 시 세션을 무효화하고 JSESSIONID 쿠키를 삭제합니다.
세션 쿠키는 HttpOnly·SameSite=Lax이며 기본 유휴 만료시간은 30분입니다.
세션은 메모리에 저장하므로 서버 재시작 후에는 다시 로그인해야 합니다.
CSRF·로그인·계정 확인·인증 오류 응답은 캐시하지 않습니다.

## 접근 권한과 오류

카테고리 생성·목록·순서 변경·수정·삭제, 기록 생성·조회·수정·삭제,
월간·연간 조회와 통합 달력 집계는 모두 로그인한 소유자 범위로 제한합니다.
`isPrivate=false`인 카테고리도 다른 사용자에게 공개하지 않습니다.
요청 본문·쿼리의 ownerId로 소유자를 선택할 수 없으며 인증된 주체에서 결정합니다.
사진 업로드는 인증된 소유자로 등록하고, 미연결 사진의 연결도 본인의 사진만 허용합니다.
사진 URL은 사진과 연결된 기록의 소유자가 모두 일치할 때만 발급합니다.

| 상황 | 응답 |
| --- | --- |
| 미인증 조회 또는 유효 CSRF가 있는 미인증 변경 | 401 AUTHENTICATION_REQUIRED |
| 틀린 계정 이름·비밀번호 | 401 INVALID_CREDENTIALS |
| 운영 Nginx의 IP별 로그인 POST 제한 초과 | 429 LOGIN_RATE_LIMITED, Retry-After: 12 |
| CSRF 토큰 누락·잘못된 토큰 | 403 REQUEST_FORBIDDEN |
| 타인 카테고리·기록·사진의 ID/키 접근 | 404 CATEGORY_NOT_FOUND / RECORD_NOT_FOUND / PHOTO_NOT_FOUND |
| 타인 카테고리 ID를 포함한 순서 변경 | 400 INVALID_CATEGORY_ORDER |

CSRF 필터가 인증 검사보다 먼저 실행되므로 미인증 변경 요청도 토큰이 없으면 403입니다.
오류 응답은 기존 공통 JSON 형식이며 로그인 페이지로 리다이렉트하지 않습니다.
아직 유효한 S3 서명 URL은 로그아웃이나 연결 해제 후에도 파일·권한이 유지되는 동안
만료 전까지 사용할 수 있습니다. 로그인은 새 URL 발급을 보호합니다.
사진 정리 작업은 내부 스케줄러로 모든 소유자의 정리 대상을 처리하며 인증된 HTTP API로 노출하지 않습니다.

## 운영 로그인 시도 제한

2026-10-02 운영 Nginx에서 정확한 `/api/auth/login` 경로의 POST 요청에
실제 접속 IP별 제한을 적용했습니다. 평균 분당 5회, 초기 연속 요청은 최대 5회
(기본 1회 + burst 4회) 허용하며 이후 약 12초마다 한 요청의 여유가 생깁니다.
고정된 1분 창의 횟수 제한이 아니라 시간이 지나면서 여유가 회복되는 방식입니다.
로그인 성공·실패·CSRF 오류 요청 모두 제한 횟수에 포함됩니다.
GET 및 다른 API 경로는 이 제한 대상이 아니며 같은 공유 IP를 사용하는 기기는 한도를 공유합니다.

초과하면 JSON `LOGIN_RATE_LIMITED`, HTTP 429, `Retry-After: 12`,
`Cache-Control: no-store`를 반환합니다. 다른 요청이 계속되면 12초 후에도 제한될 수 있습니다.
CSRF 없는 요청은 한도 안에서 기존 403, 한도 초과 시 Nginx가 먼저 429를 반환합니다.
로컬 개발 서버에 직접 접속하는 경우에는 Nginx 제한이 없습니다.

Nginx는 클라이언트의 X-Forwarded-For/X-Real-IP 값을 실제 TCP 접속 IP로 덮어씁니다.
X-Forwarded-Proto는 실제 연결 프로토콜로 덮어쓰며 Forwarded 및
X-Forwarded-Host/Port/Prefix는 제거합니다. 현재 구조는 Nginx가 직접 공개 요청을 받는 구성입니다.
앞으로 CDN이나 별도 로드밸런서를 앞에 두면 신뢰할 프록시 범위를 별도로 설정해야 합니다.
앱 8080과 DB 3306은 127.0.0.1에만 바인딩된 것을 확인했습니다.

```bash
# 다른 로그인 시도 없이 1분 이상 지난 뒤 실행합니다. 실제 계정 정보는 사용하지 않습니다.
python3 scripts/check-login-limit.py --base-url https://photo-calendar.15-165-115-190.sslip.io
# 제한 검사 후 1분 대기하고, 실제 비밀번호를 로컬 터미널에만 입력합니다.
python3 scripts/check-login.py --base-url https://photo-calendar.15-165-115-190.sslip.io
```

공개 서버에서 잘못된 CSRF 403, 잘못된 로그인 401, 연속 초과 429 및 응답 헤더,
위조 IP 헤더로 우회 불가, 제한 중 다른 인증 API 접근, 13초 후 요청 재허용을 확인했습니다.
위조 전달 헤더를 보내도 CSRF 발급과 Secure 세션 쿠키가 유지되는 것을 확인했습니다.
변경 후 사용자가 실제 owner 로그인 200·세션 확인 200·로그아웃 204 통과를 확인했습니다.

공식 설명: [Nginx 요청 제한](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html),
[프록시 헤더 설정](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_set_header).

카테고리 삭제는 완료 이력을 남기고 해당 기록을 삭제하는 기존 동작을 유지합니다.
기록 삭제 서비스를 통해 사진 연결도 해제하고 해제 시각부터 24시간 유예기간을 계산합니다.

## 검증과 남은 단계

실제 MySQL과 Spring Security 필터로 로그인·세션 ID 변경·CSRF 갱신·로그아웃,
미인증 차단과 다른 소유자의 기록 접근 차단을 검증했습니다.
소유자별 목록·순서·달력 집계, 카테고리 접근, 사진 연결·URL 발급과 업로드 소유자를 검증했습니다.
기존 컨트롤러 단위 테스트는 필터를 제외하여 입력·응답 계약만 검증하고,
인증 검증은 필터가 활성화된 AuthIntegrationTest가 담당합니다.
2026-10-01 Lightsail 운영 계정을 설정하고 공개 HTTPS의 Secure·HttpOnly 쿠키와
미인증 접근 차단을 확인했습니다. 사용자가 공개 서버에서 로그인하여 실제 S3 사진
업로드·기록 연결·서명 URL 발급·다운로드 내용 일치 및 테스트 기록 삭제까지 확인했습니다.
사진 자동 정리의 실제 24시간 경과 동작은 아직 확인하지 않았습니다.

참고: [Spring Security 로그인](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html),
[CSRF 보호와 토큰 갱신](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
