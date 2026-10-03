# DB 백업

서버 로컬 백업은 매일 한국시간 03:30 이후 최대 5분 내 실행됩니다.
서버가 꺼져 있었다면 타이머는 기동 후 누락된 실행을 보충합니다.
`/var/backups/photo-calendar`에 압축 SQL 및 SHA-256 파일을 저장하며 약 15일간 보관합니다.
백업 디렉터리는 700, 파일은 600 권한입니다. DB 덤프에는 사용자 기록이 포함됩니다.

```bash
sudo systemctl start photo-calendar-backup.service
sudo systemctl list-timers photo-calendar-backup.timer
sudo journalctl -u photo-calendar-backup.service --no-pager
```

백업은 InnoDB의 single-transaction을 사용합니다. 백업 중 테이블 변경을 수행하는
배포·마이그레이션은 피합니다. DB 계정·서버 설정·S3 사진 원본은 이 SQL 백업에 포함되지 않습니다.

복원 확인은 검증용 DB를 새로 만들고 가져온 뒤 운영 DB와 결정적인 SQL 덤프 해시를 비교합니다.
검증용 DB는 종료 시 삭제하고 운영 DB를 덮어쓰지 않습니다.
비교 중 운영 데이터가 바뀌면 차이가 발생할 수 있습니다.

```bash
sudo bash ~/photo-calendar/verify-restore.sh /var/backups/photo-calendar/백업파일.sql.gz
```

2026-10-01 첫 로컬 백업과 실제 복원·스키마·데이터 일치 확인이 성공했습니다.
2026-10-01 S3 `backups/*` 업로드·읽기 권한을 연결하고 매일 백업에 S3 업로드를 추가했습니다.
S3 백업은 SSE-S3 암호화를 사용하고 다운로드한 바이트의 SHA-256 일치를 실제 확인했습니다.
S3 업로드·검증 실패는 백업 서비스 실패로 기록하며 새 로컬 파일은 남겨둡니다.
2026-10-02 자동 재시도와 최종 성공 시각 기록을 서버에 적용하고 SNS 이메일 알림의 실제 수신을 확인했습니다.
2026-10-02 사용자가 S3 `backups/` 30일 만료 규칙 적용 완료를 확인했습니다.

## S3 백업 30일 만료 규칙

2026-10-02 서버의 앱용 IAM 계정으로 버전 관리와 기존 수명 주기 규칙을
조회했으나 두 요청 모두 `AccessDenied`였습니다. 이후 사용자가 버전 관리 비활성화 상태를
확인하고 아래 안내에 따른 콘솔 업데이트 완료를 전달했습니다. 직접 재조회 검증은 하지 못했습니다.
앱 계정에 버킷 관리 권한을 추가하지 않고 관리자 AWS 콘솔에서 설정했습니다(사용자 확인 기준).

1. S3 콘솔에서 `photo-calendar-mj-20261001-a7` → **속성 → 버킷 버전 관리**를 확인합니다.
2. **관리 → 수명 주기 규칙**에서 기존 규칙을 확인합니다. 기존 규칙은 유지합니다.
3. 버전 관리가 **비활성화**라면 다음 규칙을 생성합니다.
   - 규칙 이름: `expire-backups-after-30-days`
   - 범위: 필터로 제한, 접두사 `backups/` (앞에 `/`를 넣지 않음)
   - 작업: 현재 버전의 객체 만료
   - 객체 생성 후 일수: `30`
   - 상태: 활성화
4. 저장 후 접두사 `backups/`, 만료 `30일`, 활성화 상태를 다시 확인합니다.

비활성화 버킷용 규칙은 `deploy/aws/photo-calendar-backup-lifecycle.json`에 있습니다.
이 파일을 API로 적용하면 기존 수명 주기 구성이 전체 교체되므로 기존 규칙과 병합한 뒤 적용해야 합니다.
버전 관리가 활성화 또는 일시 중지 상태라면 이 규칙만으로 이전 버전을 정리할 수 없습니다.
그 경우 현재 버전 만료 외에 이전 버전 만료와 만료된 삭제 마커 정리도 구성해야 하며,
이전 버전 만료 일수는 객체 생성 시점이 아닌 이전 버전이 된 시점부터 계산합니다.

이 규칙은 `photos/`에 적용되지 않습니다. 30일은 만료 대상이 되는 기준이며
실제 삭제는 S3의 비동기 처리에 따라 이후 이루어질 수 있습니다.
실제 30일 경과 삭제는 별도 후속 확인 사항입니다.

공식 설명: https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-expire-general-considerations.html

## 실패 재시도와 상태 확인

`photo-calendar-backup-retry`가 백업을 최대 3회, 실패 후 60초 간격으로 실행합니다.
각 시도는 최대 15분(종료 유예 30초), 서비스 전체 제한은 50분입니다.
실패한 S3 업로드의 로컬 백업은 유지하고 다음 시도에서 새 덤프를 생성합니다.
중복 실행은 잠금으로 막습니다. 실제 덤프가 잠금 때문에 실행되지 않으면 성공으로 기록하지 않습니다.
성공은 로컬 생성과 S3 다운로드 해시 검증까지 모두 통과한 경우입니다.

```bash
sudo /usr/local/sbin/photo-calendar-status
sudo journalctl -u photo-calendar-backup.service --no-pager -n 30
```

`/var/lib/photo-calendar-backup`의 last-attempt, last-success, last-failure는 UTC 시각입니다.
last-failure는 이후 성공해도 과거 실패 기록으로 유지합니다. 최신 상태는 각 시각을 비교합니다.
2026-10-02 14:16:30 KST 실제 백업이 성공했습니다.
격리된 가짜 명령으로 두 번 실패 후 성공, 세 번 모두 실패와 기록 상태를 검증했습니다.

## 이메일 알림 — 연결 및 수신 검증 완료

1. AWS SNS 서울 리전에서 **표준(Standard)** 주제 `photo-calendar-alerts`를 생성합니다.
2. 구독 생성에서 프로토콜 **이메일**, 수신 이메일을 입력합니다.
3. 수신한 인증 메일에서 Confirm subscription을 눌러 구독을 확정합니다.
4. 주제 ARN을 확인합니다. ARN은 비밀 토큰이 아니며 연결 설정에 필요합니다.
5. `deploy/aws/photo-calendar-sns-publish-policy.json`을 별도 정책으로 앱 IAM 사용자
   `photo-calendar-app`에 추가합니다. 실제 ARN을 반영했으며 기존 S3 정책은 유지합니다.
6. 서버의 600 권한 `alerts.env`에 `ALERT_SNS_TOPIC_ARN=실제ARN`을 설정합니다.
7. notify-operation.py 및 백업 실패 알림 unit을 설치하고 daemon-reload합니다.

백업 서비스 OnFailure가 3회 모두 실패한 후 이메일 발행을 요청합니다.
2026-10-02 사용자가 전달한 ARN으로 서버 알림 파일·unit·alerts.env(600 권한)를 설치했습니다.
systemd unit 검증 및 백업 서비스 OnFailure 연결 확인을 통과했습니다.
최초 테스트 발행은 AuthorizationError로 거부됐으나 사용자 IAM Publish 권한 연결 후
2026-10-02 14:31:49 KST 격리 실패 → OnFailure → SNS 발행 성공을 확인했습니다.
알림 서비스 Result=success / ExecMainStatus=0이며 사용자가 TEST 이메일 실제 수신을 확인했습니다.
연결 후 아래 격리된 테스트 서비스 실패로 OnFailure와 이메일 수신을 확인하며 운영 DB는 변경하지 않습니다.

```bash
# 의도적 실패이므로 start 명령의 종료 코드는 0이 아닙니다.
sudo systemctl start photo-calendar-backup-alert-test.service
sudo journalctl -u photo-calendar-backup-alert-test-notify.service --no-pager -n 20
sudo systemctl reset-failed photo-calendar-backup-alert-test.service
```

테스트 메일은 TEST 표시를 포함하며 운영 장애가 아님을 명시합니다.
서버 전체 중단은 서버 내부 알림만으로 감지할 수 없으므로 외부 감시는 별도 작업입니다.

공식 안내: https://docs.aws.amazon.com/sns/latest/dg/sns-create-subscribe-endpoint-to-topic.html

## 배포와 S3 복구

2026-10-02 배포 전 새 백업 생성과 S3 검증을 자동 배포 도구에 연결했습니다.
S3 다운로드·SHA-256/gzip 검증·새 DB 복원과 기존 DB 재사용 거부도 실제 확인했습니다.
명령 및 새 서버 설정 파일 목록은 [DEPLOY_RECOVERY.md](DEPLOY_RECOVERY.md)를 참고합니다.
