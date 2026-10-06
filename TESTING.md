# Repository 통합 테스트

Docker Desktop을 실행한 상태에서 다음 명령을 사용합니다.

```bash
./gradlew test
```

Repository 테스트만 실행하려면:

```bash
./gradlew test --tests '*RepositoryTest'
```

Java 17 toolchain과 최초 실행 시 의존성 및 Docker 이미지 다운로드가 필요합니다.
Testcontainers가 `mysql:8.4` 컨테이너를 임의의 포트로 실행합니다.
기존 `.env`나 개발용 MySQL 대신 컨테이너 접속 정보를 사용합니다.
Flyway가 V1~V5 마이그레이션을 적용하고 Hibernate가 엔티티와 스키마를 검증합니다.
컨테이너는 테스트 JVM에서 공유하며 각 테스트의 변경은 트랜잭션 롤백으로 격리합니다.
Testcontainers가 실행 종료 후 컨테이너를 정리합니다.

| 검증 항목 | 테스트 및 의미 |
| --- | --- |
| 유니크 제약 | `DailyRecordRepositoryTest`: 같은 카테고리·날짜의 두 번째 기록은 DB에서 거부합니다. 카테고리 또는 날짜가 다르면 저장됩니다. |
| 실제 정렬 | `DailyRecordRepositoryTest`: 입력 순서를 섞어도 당일 기록은 시간 오름차순, 범위 조회는 날짜 오름차순입니다. `CategoryRepositoryTest`: 활성 카테고리는 표시 순서 오름차순입니다. |
| 날짜 양 끝 포함 | `DailyRecordRepositoryTest`: 시작일과 종료일을 포함하고 범위 밖 및 다른 카테고리의 기록은 제외합니다. 시작일과 종료일이 같은 조회도 확인합니다. |
| soft delete 조회 | `CategoryRepositoryTest`: 삭제된 카테고리를 목록, 활성 ID 조회, 최대 표시 순서에서 제외합니다. 활성 데이터가 없으면 최대 순서는 0입니다. |
| 활성 카테고리 집계 | `CategoryRepositoryTest`: `createdAt < nextDay`와 `deletedAt IS NULL OR deletedAt >= startOfDay`의 경계를 확인합니다. 당일 생성·삭제는 포함하고 다음 날 0시 생성과 전날 삭제는 제외합니다. |
| 복합키 완료 이력 | `CompletionHistoryRepositoryTest`: 저장 후 새로운 `(categoryId, recordDate)` 키로 조회하고 두 키 요소를 각각 구분합니다. 날짜별 집계에는 soft delete된 카테고리의 완료 이력도 포함됩니다. |

조회 전 `flush()` 및 `clear()`로 SQL 실행과 영속성 컨텍스트 초기화를 수행합니다.
유니크 제약 테스트는 `saveAndFlush()`에서 `DataIntegrityViolationException`이 발생하는지 확인합니다.
활성 집계의 고정 시각 데이터는 테스트 안에서 SQL로 설정하여 현재 시각에 의존하지 않습니다.

HTML 결과는 `build/reports/tests/test/index.html`에 생성됩니다.

## API 예외 처리 테스트

Docker 없이 서비스 및 HTTP 오류 응답을 확인하려면:

```bash
./gradlew test --tests '*ServiceTest' --tests '*GlobalExceptionHandlerTest'
```

`GlobalExceptionHandlerTest`는 MockMvc로 400/404/409/500 응답과 공통 JSON 형식,
필드 검증 오류, 잘못된 JSON·파라미터, 월·연도 경계, 405 상태 유지 등을 검증합니다.
서비스는 mock으로 대체합니다. 예상하지 못한 오류의 상세 메시지는 응답에 포함하지 않습니다.
`DailyRecordRepositoryTest`의 중복 저장 테스트는 실제 MySQL의 제약 위반이
`DUPLICATE_DAILY_RECORD`(409)로 변환되는지도 확인합니다. 이 검증에는 Docker가 필요합니다.

## 컨트롤러 테스트

Docker 없이 정상 요청과 입력 검증을 확인하려면:

```bash
./gradlew test --tests '*ControllerTest'
```

`@WebMvcTest`와 `@MockitoBean`으로 MVC 구성과 실제 컨트롤러를 실행하고 서비스를 대체합니다.
13개 엔드포인트의 HTTP 상태, JSON 응답, 날짜·시간 변환, 서비스 전달값을 검증합니다.
삭제·순서 변경은 204와 빈 본문, 카테고리·일별·월간 목록의 빈 결과는 `[]`를 확인합니다.

입력 검증에는 이름·색상·메모의 누락과 공백, 필수 기록 필드 누락,
빈 순서 목록과 null 요소, 잘못된 날짜·시간, 연도·월 누락과 범위 초과가 포함됩니다.
잘못된 요청은 400으로 거부되고 서비스가 호출되지 않는지 확인합니다.
본문 필드 검증은 `VALIDATION_FAILED`와 필드 오류 목록,
형식·조회 파라미터 오류는 `INVALID_REQUEST`를 검증합니다.
비즈니스 규칙과 실제 저장은 서비스 및 Repository 테스트에서 확인합니다.

서비스 및 공통 예외 처리와 함께 회귀 검증하려면:

```bash
./gradlew test --tests '*ControllerTest' --tests '*ServiceTest' --tests '*GlobalExceptionHandlerTest'
```

## 사진 업로드 테스트

```bash
./gradlew test --tests '*Photo*Test'
```

`PhotoControllerTest`는 multipart 파일 전달, 201 응답과 이미지 키,
파일 누락 400, 업로드 용량 초과 오류의 413 변환을 확인합니다.
`MockMvc` multipart 테스트는 실제 HTTP 서버의 용량 제한 동작을 재현하지 않으므로
실제 서버에서 20MB 초과 업로드 검증은 별도로 필요합니다.
`PhotoServiceTest`는 실제 JPEG·PNG 이미지, 파일명·선언 타입에 의존하지 않는 내용 검사,
고유 키, 빈 파일·텍스트·GIF 거부, 20MB와 5천만 화소 제한을 확인합니다.
`S3PhotoStorageTest`는 SDK mock으로 버킷·키·타입·내용 전달,
공개 ACL을 요청하지 않는 동작, SDK 오류 변환을 확인합니다.
실제 S3 접속은 테스트에 포함하지 않습니다. 설정과 현재 구현 범위는 `PHOTO_STORAGE.md`를 참고합니다.

## 사진과 기록 연결 테스트

Docker가 필요한 실제 MySQL 검증:

```bash
./gradlew test --tests '*PhotoRecordIntegrationTest'
```

`PhotoRecordIntegrationTest`는 서비스의 실제 커밋·롤백을 확인하기 위해
테스트 전체를 감싸는 트랜잭션을 사용하지 않으며, 각 테스트 후 자체 데이터를 정리합니다.
사진이 있는 기록과 없는 기록의 생성, 단건·일별·월별 사진 키 조회,
사진 유지·교체·제거와 기록 삭제에 따른 연결 해제를 검증합니다.
미등록·미완료·다른 기록에 연결된 사진의 거부와 기록 생성·수정 롤백도 확인합니다.
실제 DB의 한 기록당 한 사진 유니크 제약, 별도 스레드·트랜잭션의 동시 사진 연결 및
같은 기록에 대한 동시 사진 교체를 확인합니다.

`DailyRecordControllerTest`는 사진 키와 제거 플래그 전달, `imageKey` 응답,
키 형식·동시 교체/제거 검증과 404/409 오류 응답을 확인합니다.
`PhotoServiceTest`는 메타데이터 등록 → S3 전송 → 완료 정보 저장 순서와
등록 실패 시 전송 차단, S3 실패 시 미완료 메타데이터 보존을 확인합니다.

## 비공개 사진 조회 URL 테스트

`PhotoReadControllerTest`는 기록 ID 전달, URL·UTC 만료 시각, 응답 캐시 차단,
잘못된 ID의 400, 사진 미존재 404 및 서명 실패 503을 검증합니다.
`PhotoReadServiceTest`는 DB 조회로 선택한 사진만 서명하고 조회 결과가 없으면
저장소를 호출하지 않는지 확인합니다.
`S3PhotoStorageTest`는 버킷·키·GET 서명 요청, 10분 유효시간,
이미지 응답의 캐시 차단 옵션, SDK 만료 시각 반환과 실패 변환을 검증합니다.
테스트용 고정 자격 증명으로 실제 Presigner도 실행하여 서명 파라미터와 만료 시각을 확인합니다.
이 검증은 AWS 네트워크 호출을 하지 않습니다.

`PhotoRecordIntegrationTest`는 MySQL에서 현재 연결된 사진만 조회되는지 확인합니다.
교체·제거·기록 삭제, 카테고리 soft delete, 사진 없는 기록, 미완료 사진과
키 불일치의 조회 제외를 검증합니다. 실제 S3 파일 존재·권한·만료 후 다운로드는 별도 검증 대상입니다.

## 사진 자동 정리와 재시도 테스트

```bash
./gradlew test --tests '*PhotoCleanup*Test'
```

`PhotoCleanupIntegrationTest`는 Docker의 실제 MySQL과 S3 mock을 사용합니다.
테스트 자체를 감싸는 트랜잭션 없이 서비스의 실제 커밋을 검증하고 자체 데이터를 정리합니다.
24시간 유예기간의 경계, 업로드 미완료·미연결 사진 정리,
사진 교체·기록 삭제 시 유예기간 초기화, 연결된 사진과 기록 참조가 있는 사진의 보호를 확인합니다.
S3 호출 전에 삭제 상태가 커밋되고 S3 호출 중 DB 트랜잭션이 없는지도 확인합니다.
삭제 실패 상태 보존, 작업 객체 재생성 후 재시도, 지수형 재시도 간격과 6시간 상한,
중단된 작업 재획득 및 이전 작업자의 결과 무시를 검증합니다.
실제 별도 스레드·트랜잭션에서 삭제 획득과 사진 연결을 동시에 실행하고 결과의 일관성을 확인합니다.
삭제 중·삭제된 사진은 조건부 업로드 완료 갱신으로 되살릴 수 없음을 확인합니다.

`PhotoCleanupWorkerTest`는 최대 100개 후보 처리, 획득 실패·S3 실패·DB 실패의 격리,
S3 성공 이후 DB 삭제 실패의 재시도 흐름을 확인합니다.
`PhotoCleanupConfigurationTest`는 실제 스케줄러 등록, 저장소 및 정리 기능 비활성화를 확인합니다.
`S3PhotoStorageTest`는 DeleteObject의 버킷·키 전달, 반복 삭제 호출과 SDK 실패 변환을 확인합니다.
`PhotoServiceTest`는 완료 정보의 조건부 갱신 실패 시 키를 반환하지 않는지도 확인합니다.
실제 AWS 파일 삭제와 버전 관리 동작은 실행하지 않습니다.

## 로그인과 소유자 검증 테스트

```bash
./gradlew test --tests '*AuthIntegrationTest' --tests '*OwnershipIntegrationTest' --tests '*SecurityConfigurationTest' --tests '*CurrentOwnerTest'
```

인증·소유자 통합 테스트는 Docker의 실제 MySQL을 사용합니다.
`AuthIntegrationTest`는 필터가 활성화된 MockMvc에서 실제 BCrypt 계정 로그인을 실행합니다.
미인증 API의 401, CSRF 누락·옛 토큰의 403, 틀린 이름·비밀번호의 공통 401,
세션 ID 변경, 로그인 후 CSRF 토큰 갱신, 인증된 변경 요청과 로그아웃을 확인합니다.
실제 세션으로 다른 소유자의 기록 조회·수정·삭제를 거부하는지도 확인합니다.

`OwnershipIntegrationTest`는 카테고리·기록 목록, 표시 순서, 월간·연간 조회,
통합 달력의 카테고리·기록·완료 이력 집계를 소유자별로 격리하는지 확인합니다.
미연결 사진의 타인 연결 거부, 사진·기록 소유자 일치에 따른 URL 조회,
인증 주체의 사진 등록과 카테고리 삭제 시 사진 연결 해제를 검증합니다.
`SecurityConfigurationTest`는 미설정 계정 차단과 BCrypt 설정 검증,
`CurrentOwnerTest`는 미인증·잘못된 주체를 기본 소유자로 처리하지 않는지 확인합니다.

기존 `@WebMvcTest` 컨트롤러 테스트는 `addFilters=false`로 HTTP 입력·응답 계약만 확인합니다.
인증 동작을 검증하는 AuthIntegrationTest는 필터를 활성화합니다.
기존 서비스 단위 테스트는 CurrentOwner mock으로 소유자 1을 지정하고,
MySQL 테스트는 기본 인증 주체를 설정한 뒤 종료 시 정리합니다.
동시성 테스트는 DelegatingSecurityContextExecutorService로 각 작업에 인증 주체를 전달합니다.

## 한국시간 날짜와 UTC 저장 검증

ApplicationTimeTest는 UTC와 한국시간의 날짜 차이, 자정 경계 및 호스트 시간대 변경 시
사진 메타데이터 UTC 저장을 확인합니다. 서비스 테스트는 자정의 오늘 기록 허용과
카테고리 생성일 이전 기록 거부, 연간 달력의 오늘/미래 구분을 검증합니다.
CalendarTimeIntegrationTest는 실제 MySQL에서 한국시간 월 경계로 카테고리를 집계합니다.
사진 정리 통합 테스트의 기준 시각도 UTC로 맞췄습니다.
2026-10-02 전체 ./gradlew test 204개 통과, 실패/오류/건너뜀 0개.

읽기 운영 확인은 scripts/check-read-load.py로 실제 계정 로그인 후 조회 24회를 최대
동시 3회 실행합니다. 데이터는 생성/수정/삭제하지 않으며 비밀번호·쿠키·토큰은 출력하지 않습니다.
장시간/큰 사진/대용량 데이터 부하 시험은 아닙니다. 결과는 deploy/lightsail/OPERATIONS.md에 기록했습니다.
