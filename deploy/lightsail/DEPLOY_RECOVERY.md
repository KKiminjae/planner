# 배포·롤백·DB 복구

## 현재 구성과 검증 결과

현재 배포는 맥에서 JAR를 빌드·전송하고 서버의 배포 도구가 백업과 교체를 수행합니다.
2026-10-02 현재 운영 버전으로 실제 배포 및 보관본 롤백을 확인했습니다.
S3 백업 다운로드·SHA-256 및 gzip 검증·별도 DB 복원·운영 DB와 스키마/데이터 일치도 확인했습니다.
운영 DB는 덮어쓰지 않았으며 새 AWS 서버를 생성하지 않았습니다.

| 도구 | 실행 위치 | 역할 |
| --- | --- | --- |
| `scripts/deploy-lightsail.py` | 맥 | 빌드·SHA-256 계산·SSH/SCP 전송·서버 배포 호출 |
| `/usr/local/sbin/photo-calendar-deploy` | 서버, sudo | 배포 전 백업·JAR 보관·교체·API 확인·롤백 |
| `/usr/local/sbin/photo-calendar-download-backup` | 서버, sudo | 정확한 S3 백업 키 다운로드·SHA-256/gzip 확인 |
| `/usr/local/sbin/photo-calendar-verify-restore` | 서버, sudo | 임시 DB 복원·운영 DB와 비교·임시 DB 삭제 |
| `/usr/local/sbin/photo-calendar-restore-new-db` | 서버, sudo | 존재하지 않는 `recovery_...` DB에 복원·복원 DB 보존 |
| `/usr/local/sbin/photo-calendar-status` | 서버, sudo | 앱·Nginx·백업 시각·메모리·디스크 확인 |

서버 설치 원본은 이 디렉터리에 있습니다. Python은 맥 3.11 이상, 서버 Ubuntu 24.04의
Python 3.12를 사용합니다. S3 다운로드에는 서버의 기존 python3-boto3가 필요합니다.

## 평소 배포

앱 코드 변경 시 관련 테스트를 통과시킨 뒤 프로젝트 폴더에서 실행합니다.

```bash
python3 scripts/deploy-lightsail.py
```

이미 빌드한 JAR를 사용하려면 다음처럼 실행합니다.

```bash
python3 scripts/deploy-lightsail.py --jar build/libs/photo_calendar-0.0.1-SNAPSHOT.jar
```

동작 순서:

1. JAR를 서버 `incoming/release-.../candidate.jar`로 전송합니다.
2. 배포 잠금을 획득하고 기존 JAR와 새 JAR를 `releases/<release-id>/`에 보관합니다.
3. 전송된 SHA-256·JAR 구조·포함된 Flyway 마이그레이션을 확인합니다.
4. 백업 서비스를 실행하고 **로컬 생성 + S3 다운로드 해시 확인**까지 성공해야 진행합니다.
5. 백업 잠금을 획득하여 앱 기동/Flyway 실행 중 새 백업·복원이 겹치지 않게 합니다.
6. 앱을 중지하고 JAR를 임시 파일에서 원자적으로 교체한 뒤 기동합니다.
7. 최대 120초 동안 앱 서비스와 내부/공개 HTTPS API를 검사합니다.
8. 성공 여부·이전/새 JAR 해시·백업 이름/해시·마이그레이션 변경 여부를 manifest.json에 기록합니다.

배포 중 짧은 접속 중단이 있으며 메모리 세션이 초기화돼 재로그인이 필요합니다.
배포 확인은 CSRF 발급 200과 미인증 `/api/auth/me` 401을 검사합니다.
앱 코드의 기능 변경은 별도 기능 테스트로 확인해야 합니다.
매일 03:30~03:35 KST 자동 백업 시간대는 배포 시간을 피합니다.
공개 HTTPS 확인이 실패하면 DB 변경이 없는 배포도 롤백 대상으로 처리합니다.
전원 장애/강제 종료까지 자동 복귀를 보장하지 않으므로 manifest와 실제 서비스 상태를 확인합니다.

## 자동 및 수동 롤백

JAR 내 SQL 파일의 이름과 SHA-256이 이전 버전과 같으면 기동/API 확인 실패 시
기존 JAR로 복귀하고 내부/공개 API를 다시 확인합니다. 복귀에도 실패하면 manifest에
`rollback-failed`를 남기며 성공으로 처리하지 않습니다. DB 데이터는 자동으로 되돌리지 않습니다.

수동으로 성공한 배포의 직전 JAR에 복귀하려면 서버에서 해당 배포 ID를 사용합니다.
다음 명령의 ID는 실제 배포 로그에 나온 값으로 바꿉니다.

```bash
sudo /usr/local/sbin/photo-calendar-deploy rollback 20261002T054825Z-ab9d2174
sudo /usr/local/sbin/photo-calendar-status
```

수동 롤백도 먼저 현재 DB를 백업하고 하나의 새 배포 기록으로 남깁니다.
현재 JAR와 복귀 대상의 마이그레이션이 다르면 JAR만의 복귀를 거부합니다.
동일한 SQL이라도 앱의 데이터 형식 변경까지 보장하는 것은 아니므로 코드 변경 시
이전 앱 버전의 데이터 호환성을 검토해야 합니다.
초기화/오염된 DB를 이전 JAR로 되돌리는 것으로 복구할 수 없습니다.

검증한 배포 ID:

- 현재 버전 배포: `20261002T054825Z-ab9d2174`
- 직전 보관본 수동 롤백 실행 기록: `20261002T054929Z-f5adf63c`
- 새 백업 생성 확인 조건을 포함한 최종 배포: `20261002T055635Z-8debbc5a`
- 두 기록의 JAR는 동일한 현재 버전이며, 운영 서버에 실패 JAR를 넣지는 않았습니다.
- 실패 시 자동 복귀·백업 실패/새 백업 없음 차단·마이그레이션 변경 차단 등 9개 격리 테스트를 맥과 서버에서 통과했습니다.
- 새 DB 복원 도구의 내용 일치·기존 DB 재사용 거부, 다운로드의 잘못된 예상 해시 거부·기존 파일 보존도 서버에서 검증했습니다.

기록과 이전 JAR는 루트 전용 `~/photo-calendar/releases/`에 남습니다.
자동 삭제하지 않으므로 디스크 상태를 확인하고 검증된 복구본을 남긴 뒤 운영자가 정리합니다.

## Flyway 변경이 포함된 배포

기존 SQL 수정/삭제는 항상 거부합니다. 새 SQL 추가는 기본적으로 거부하고 다음 항목을
검토한 뒤에만 `--reviewed-migrations`로 진행합니다.

- 기존 데이터를 보존하며 이전 앱도 새 스키마를 사용할 수 있는지 확인합니다.
- 컬럼/테이블 삭제·이름 변경 등 파괴적 변경은 앱 변경과 단계적으로 분리합니다.
- 복제/검증 DB에서 새 JAR 기동과 마이그레이션 실패 시 복구를 시험합니다.
- 백업과 그 백업에 맞는 JAR를 확보하고 복구 시점 이후 데이터 손실 범위를 확인합니다.

```bash
python3 scripts/deploy-lightsail.py --reviewed-migrations
```

이 옵션으로 SQL 변경이 있는 배포가 실패하면 앱을 중지하고 기존 JAR로 자동 복귀하지 않습니다.
MySQL의 DDL은 실패 중 일부가 반영될 수 있으므로 DB 상태를 먼저 확인해야 합니다.
현재 저장소에는 V1~V5 순방향 마이그레이션만 있으며 자동 DB undo는 구성하지 않았습니다.
Flyway 이력을 임의로 삭제하거나 실패를 숨기기 위해 repair를 실행하지 않습니다.
별도 복원 DB로 복구하거나 검토된 전진 수정 마이그레이션을 사용합니다.

공식 참고: [Flyway undo의 한계](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/undo-migrations).

## S3 백업 다운로드와 복원 확인

앱 IAM 계정은 GetObject만 허용하므로 S3 전체 목록을 조회하지 못합니다.
정확한 키는 배포 manifest의 backup 필드 또는 관리자 S3 콘솔 `backups/` 목록에서 찾습니다.
30일 만료 대상 백업을 복구에 사용하려면 만료 전에 확보해야 합니다.

```bash
# 실제 검증한 예시. 원하는 복구 시점의 정확한 키로 바꿉니다.
sudo /usr/local/sbin/photo-calendar-download-backup backups/db-20261002T054826-20595.sql.gz
sudo /usr/local/sbin/photo-calendar-verify-restore /var/backups/photo-calendar/db-20261002T054826-20595.sql.gz
```

다운로드 도구는 S3 metadata의 SHA-256과 다운로드 내용을 비교하고 gzip 전체를 읽어 검증합니다.
manifest의 독립된 해시가 있다면 `--sha256 <backup_sha256>`로 함께 대조합니다.
같은 이름의 로컬 파일은 동일한 내용일 때만 재사용하며 다른 내용은 덮어쓰지 않습니다.
복원 확인 도구는 운영 DB를 건드리지 않고 임시 DB를 만들어 비교한 뒤 삭제합니다.
오래된 백업 또는 비교 중 기록 변경이 있으면 현재 DB와 다를 수 있습니다.

## 새 DB 또는 새 서버로 복구

기존 운영 DB를 덮어쓰는 대신 새 `recovery_...` DB로 복원합니다.
실제 운영 전환은 복원 결과를 확인한 뒤 수행하며 아래는 절차 문서입니다.
현재 서버의 운영 DB 전환이나 새 서버 생성은 실행하지 않았습니다.

새 서버를 사용할 경우 먼저 다음을 준비합니다.

| 파일/설정 | 확보/설정 방법 |
| --- | --- |
| 복구 시점과 호환되는 JAR | releases의 candidate.jar/previous.jar 또는 해당 소스의 검증된 빌드 |
| compose.yml·MySQL 데이터 볼륨 | 새 서버에는 Java 17·Docker/Compose·Python boto3를 설치하고 MySQL 8.4를 기동 |
| .env | 새 서버는 init-db-env.sh로 새 DB 계정/비밀번호 생성. 기존 서버는 현재 값 유지 |
| auth.env | 기존 파일을 안전한 경로로 이전하거나 configure-login.sh로 새 로그인 비밀번호 설정 |
| aws.credentials | 기존 앱 IAM 프로필을 안전하게 이전하거나 키를 재발급. 채팅/저장소에 넣지 않음 |
| alerts.env | SNS 주제 ARN. 알림에 SNS Publish 권한 필요 |
| systemd·백업 도구/unit·timer | deploy/lightsail의 원본에서 설치. 복원 중 앱/백업 timer는 중지 |
| Nginx·HTTPS | 새 주소에 맞춰 server_name과 인증서 경로 설정. Certbot으로 인증서 발급·갱신 설정 |
| 네트워크 | SSH 접근 범위 제한, 80/443 공개, 8080/3306은 내부 바인딩 |

앱 폴더·자격 증명 파일은 700/600 권한을 유지합니다. 설정 파일은 DB 덤프에 포함되지 않습니다.
프로젝트 파일이나 평문 S3 백업에 비밀 설정 파일을 추가하지 않습니다.
기존 IP/주소를 사용할지 새 주소로 전환할지는 실제 서버 복구 시 결정합니다.

서버에서 복구할 때:

1. `sudo systemctl stop photo-calendar.service photo-calendar-backup.timer`로 앱과 새 백업을 중지합니다.
2. 진행 중 백업은 완료될 때까지 기다립니다. S3 다운로드 도구로 원하는 백업을 확보합니다.
3. 존재하지 않는 새 이름으로 복원합니다. 예시의 이름/파일을 실제 값으로 바꿉니다.

```bash
sudo /usr/local/sbin/photo-calendar-restore-new-db \
  /var/backups/photo-calendar/db-20261002T054826-20595.sql.gz recovery_20261002
```

CREATE DATABASE에 IF NOT EXISTS를 사용하지 않으므로 기존 DB는 재사용/덮어쓰기하지 않습니다.
실패하면 새 DB를 보존하고 앱은 중지 상태로 유지하여 원인을 확인합니다.
복원 후 Flyway 버전/성공 여부와 테이블 수를 출력하며 기록 내용·비밀번호는 출력하지 않습니다.

4. 복원 DB의 스키마·기록·사진 연결을 검토합니다. DB 전환 전 복구 시점 이후 손실될 기록을 확인합니다.
5. 앱 DB 사용자에게 **복원 DB만** 접근하도록 권한을 부여합니다. 현재 기본 DB 사용자 기준 예시:

```bash
cd /home/ubuntu/photo-calendar
sudo docker compose exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot' <<'SQL'
GRANT ALL PRIVILEGES ON `recovery_20261002`.* TO 'photo_calendar'@'%';
SQL
```

6. `.env`의 MYSQL_DATABASE를 복원 DB 이름으로 변경합니다. 다른 자격 증명 값은 유지합니다.
7. `sudo docker compose up -d --force-recreate mysql`로 컨테이너 환경도 새 DB 이름에 맞춥니다.
   이 단계가 없으면 백업 도구가 컨테이너의 옛 MYSQL_DATABASE로 백업할 수 있습니다.
   기존 데이터 볼륨은 유지하며 `down -v`는 사용하지 않습니다. MySQL healthy를 기다립니다.
8. 호환되는 JAR를 배치하고 앱을 기동합니다. systemd 환경 파일은 시작 시 새로 읽습니다.
9. HTTPS의 CSRF200·미인증401, 정상 로그인, 달력/기록 조회 및 현재 존재하는 사진 다운로드를 확인합니다.
10. 새 DB의 백업 서비스를 실행해 S3 검증까지 확인한 다음 timer를 다시 시작합니다.

원래 DB는 검증과 운영 전환 후에도 보존합니다. DB 전환 후 이전 DB로 다시 돌아가려면
새 DB에 생긴 기록을 보존/병합할지 먼저 결정해야 합니다.

## 사진 복구의 범위

SQL 백업에는 사진 키만 포함되며 S3 사진 원본은 포함되지 않습니다.
미연결 사진은 24시간 후 자동 삭제되므로 오래된 DB 백업을 복원해도 삭제된 사진은 돌아오지 않습니다.
현재 S3 버전 관리는 비활성화이며 사진 버전/별도 사진 백업은 구성하지 않았습니다.
사진 보관 정책을 바꾸려면 보관기간·비용을 따로 결정해야 합니다. 이번 작업에서 정책은 변경하지 않았습니다.
