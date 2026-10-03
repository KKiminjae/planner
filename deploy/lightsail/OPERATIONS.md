# 운영 점검과 프론트엔드 연결 기준

점검일: 2026-10-02. 프론트엔드 구현은 별도 작업입니다.

## 시각 저장과 달력 날짜

| 항목 | 기준 |
| --- | --- |
| 서버 OS·JVM·MySQL 시각 | UTC, OS NTP 동기화 확인 |
| created_at·updated_at·deleted_at·사진 정리 메타데이터 | UTC 시각을 DATETIME에 저장 |
| 기록 날짜 recordDate·기록 시각 recordTime | 사용자가 입력한 한국시간 날짜/시각 그대로 보존 |
| 오늘·미래 날짜 판단·카테고리 생성일·월간/연간 달력 | Asia/Seoul |
| 사진 정리 | UTC 기준 연결 해제 후 24시간, 5분 간격 검사 |
| S3 서명 URL expiresAt | UTC Instant 응답 |
| 백업 타이머 | 한국시간 03:30, 최대 5분 무작위 지연 |

서버 UTC 날짜를 그대로 오늘로 사용하면 한국시간 00:00~08:59의 오늘 기록이
미래로 거부될 수 있었습니다. ApplicationTime으로 한국시간 날짜를 판단하고
달력 집계에 사용할 하루 경계를 UTC로 변환하도록 수정했습니다.
예를 들어 한국시간 10월 2일 00:30은 UTC 10월 1일 15:30이지만 기록 날짜는 10월 2일입니다.
한국시간 10월 1일의 집계 범위는 UTC 9월 30일 15:00부터 10월 1일 15:00 직전까지입니다.

엔티티와 사진 스케줄러는 호스트 기본 시간대에 의존하지 않고 명시적으로 UTC를 사용합니다.
서비스도 TZ=UTC와 -Duser.timezone=UTC를 적용했습니다. 운영 DB의 기존 메타데이터는
이미 UTC이므로 데이터 변경·마이그레이션 없이 적용했습니다.
이전 로컬 DB 데이터는 서버로 이전하지 않았습니다. 이전 로컬 메타데이터는 당시의
호스트 시간대에 따라 저장됐을 수 있으므로 나중에 이전할 때 UTC 변환 여부를 확인해야 합니다.

자정의 오늘 기록·생성일 이전 기록 거부·연간 달력 상태·월 경계 실제 DB 집계와
호스트 시간대 변경 시 사진 UTC 저장을 추가 검증했습니다. 전체 앱 테스트 204개 통과,
실패/오류/건너뜀 0개입니다.
실제 배포 ID: `20261002T061454Z-7975c409`. 배포 전 백업과 내부/공개 API 확인 성공.

2026-10-01 테스트 사진의 실제 24시간 경과 삭제는 10월 2일 21:18 KST 이후
DB와 S3를 함께 확인해야 하며 이번 시간대 수정으로 유예기간을 단축하지 않았습니다.

## 공개 포트와 SSH

| 항목 | 점검 결과 |
| --- | --- |
| HTTP 80·HTTPS 443 | 공개 주소에서 접속 성공 |
| 앱 8080·MySQL 3306 | 127.0.0.1 바인딩, 외부 TCP 접속 불가 확인 |
| SSH 비밀번호/키보드 대화식 인증 | 모두 비활성화, 공개 키 인증 사용 |
| 서버 UFW | 비활성화, 공개 IP 접근 범위는 Lightsail 방화벽에서 관리 |
| Lightsail SSH 접근 범위 | 내 IPv4/32 제한·SSH Anywhere IPv6 제거·브라우저 SSH 유지 안내 후 사용자 변경 완료 확인. 15:53 KST 새 SSH 접속 성공 |
| AWS 방화벽 직접 조회 | 앱 IAM의 get_instance_port_states 요청은 AccessDeniedException |

SSH 제한 설정:

1. Lightsail → photo-calendar-server → 네트워킹 → IPv4 SSH/TCP22 규칙 편집.
2. IP 주소로 제한을 선택하고 현재 맥이 사용하는 인터넷의 공인 IP를 입력합니다.
   내 IP 추가 버튼이 있으면 사용하거나 `curl -s https://checkip.amazonaws.com`으로 확인합니다.
3. Allow Lightsail browser SSH를 체크한 채 저장합니다.
4. 기존 SSH 창을 유지한 상태로 새 터미널 SSH 접속을 확인합니다.
5. IPv6 SSH 전체 허용 규칙도 있으면 함께 제한합니다. IPv6 SSH를 사용하지 않으면
   IPv6의 SSH 규칙만 제거하고 웹 서비스 규칙은 유지합니다.

인터넷 회선의 공인 IP가 바뀌면 허용 IP를 업데이트합니다.
서버 IP인 15.165.115.190과 접속하는 맥의 공인 IP는 서로 다른 역할입니다.
브라우저 SSH는 IP 변경 시에도 콘솔에서 연결하는 경로로 유지합니다.
UFW를 별도로 켜거나 SSH 설정을 임의로 변경하지 않았습니다.

공식 안내: [Lightsail 방화벽 규칙 편집](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-editing-firewall-rules.html).

2026-10-02 사용자가 위 변경 완료를 확인했습니다. 변경 후 새 SSH 연결과 앱·Nginx active를
직접 확인했습니다. 클라우드 규칙은 IAM 조회 권한 부족으로 직접 재조회하지 못했으므로
허용 범위의 설정 내용은 사용자 확인 기준입니다.

## 메모리와 읽기 부하

2026-10-02 15:17 KST 사용자가 공개 HTTPS에서 실제 owner 로그인 후
읽기 조회 24회, 최대 동시 요청 3회 검사를 실행했습니다.
카테고리·일별 기록·월간 통합 달력만 조회하고 데이터를 생성/수정/삭제하지 않았습니다.

| 항목 | 결과 |
| --- | --- |
| API 성공 | HTTP 200, 24/24 |
| 응답 시간 | 평균 1.047초, 최대 1.827초 |
| 앱 메모리(15:19 KST 조회) | 현재 약 343MiB, 기동 후 최고 약 348MiB, 제한 850MiB |
| MySQL 컨테이너 | 267.3MiB / 768MiB |
| 서버 메모리 | 1.9GiB 중 약 803MiB 사용 가능 |
| 앱 자동 재시작 | NRestarts=0 |
| 조회 구간 오류 | 앱 ERROR/OOM 및 커널 OOM 기록 없음 |
| 디스크 | 58G 중 5.3G 사용, 52G 여유, 10% |
| 스왑 | 없음 |

이번 소규모 읽기 부하에서는 여유가 있어 현재 메모리 제한을 유지하고 스왑/서버 확장은
추가하지 않았습니다. 장시간 부하·큰 사진 업로드·큰 데이터셋 부하를 검증한 결과는 아닙니다.
사진 사용량이나 데이터가 늘면 MemoryPeak·MemAvailable·응답 시간을 다시 확인합니다.
이후 메모리 부족이 반복되면 스왑/제한 조정/서버 확장을 검토합니다.

```bash
# 서버에서 자원·기동 상태·백업 시각 확인
sudo /usr/local/sbin/photo-calendar-status
# 맥에서 실제 계정 비밀번호를 터미널에만 입력하여 읽기 부하 확인
python3 scripts/check-read-load.py --base-url https://photo-calendar.15-165-115-190.sslip.io
```

타이머와 인증서도 정상입니다. 인증서 만료 시각은 2026-12-30 11:06:35 UTC이며,
certbot.timer와 백업 timer가 다음 실행을 예약하고 있습니다.

## 프론트엔드 연결

현재 프론트엔드 주소/구현은 없습니다. 기본 연결 방식은 같은 HTTPS origin에서
프론트엔드를 제공하고 `/api/...`를 상대 경로로 호출하는 방식으로 준비합니다.
Nginx의 정적 화면/프론트엔드 프록시 경로는 실제 프론트엔드가 만들어졌을 때 추가합니다.
현재 임의의 CORS 허용 설정은 추가하지 않았습니다.
허용하지 않은 Origin의 CSRF GET/OPTIONS 응답에는 Access-Control-Allow-Origin이 없음을
공개 서버에서 확인했습니다. 응답 200만으로 다른 origin의 브라우저가 읽을 수 있는 것은 아닙니다.

프론트엔드 구현 시 확인할 흐름:

1. GET /api/auth/csrf로 쿠키와 토큰을 준비합니다.
2. POST /api/auth/login에 URL 인코딩 username/password와 X-CSRF-TOKEN을 전달합니다.
3. 성공 후 CSRF 토큰을 다시 받아 변경 요청에 사용합니다.
4. 401은 재로그인, 403은 인증/CSRF 상태 확인, 429는 Retry-After를 확인해 대기합니다.
5. 사진 URL은 로그인 세션 쿠키를 S3로 보내지 않고 만료 시 새 URL을 받습니다.
6. 로그아웃과 30분 유휴 만료, 새로고침/재시작 이후 재로그인을 브라우저에서 확인합니다.

recordDate는 YYYY-MM-DD, recordTime은 HH:mm:ss 문자열을 사용합니다.
달력 날짜 문자열을 UTC Date로 바꿨다가 재변환하지 않고 날짜 값 그대로 처리합니다.
오늘 날짜가 필요하면 Asia/Seoul을 명시해 계산하며 new Date().toISOString()의 날짜 부분은
UTC 날짜이므로 한국시간 오늘 계산에 그대로 사용하지 않습니다.
expiresAt은 UTC Instant이므로 남은 유효시간 계산이나 한국시간 표시 시 변환합니다.

별도 origin을 선택하면 실제 프론트엔드 주소를 정한 뒤에만 CORS 허용 origin을 명시하고
credentials: include, CSRF 및 쿠키 전송을 확인합니다. credentials 허용과 wildcard origin은
같이 사용하지 않습니다. 서로 다른 site이면 SameSite 설정과 브라우저의 타사 쿠키 정책도
검토해야 합니다. 운영 Secure 쿠키는 HTTPS에서 유지합니다.

공식 안내: [Spring CORS와 credentials](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html).
실제 프론트엔드 연결·브라우저 검증은 주소가 정해진 뒤 수행할 후속 작업입니다.
