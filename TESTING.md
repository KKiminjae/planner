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
Flyway가 V1, V2 마이그레이션을 적용하고 Hibernate가 엔티티와 스키마를 검증합니다.
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
