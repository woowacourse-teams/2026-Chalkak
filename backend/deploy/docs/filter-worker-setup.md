# 필터 워커 인프라 준비

작가 필터의 견본 추출 워커가 쓰는 AWS 자원과 권한을 정리한다. 공유 계정에서 새 Lambda를 만들 수 없어 micro EC2 한 대가 워커를 돌린다. dev·prod는 SQS 큐 하나를 함께 쓰고, 워커는 S3 키의 환경(`dev`/`prod`)을 보고 콜백할 백엔드를 고른다. 기존 [이미지 처리 Lambda](../../lambda/image-processor/README.md)의 공용 큐와 같은 방식이다.

워커 코드 배포와 실행 설정은 워커 연동 작업에서 이 문서에 이어 쓴다.

## 흐름

```text
앱 ──presigned PUT──▶ S3 chalkak/filter-probes/{env}/{probeId}.jpg
                         │ ObjectCreated (알림 chalkak-filter-probes)
                         ▼
                      SQS chalkak-filter-extraction (dev·prod 공용)
                         │ 긴 폴링
                         ▼
                      EC2 ec2-chalkak-filter-worker (인스턴스 역할로 S3 직접 읽기·쓰기)
                         │ S3 chalkak/filters/{env}/recipes/{recipeId}.json
                         ▼
                      백엔드 /internal/v1 콜백 (HMAC)
```

## 1. EC2

| 항목 | 값 |
| --- | --- |
| 이름 | `ec2-chalkak-filter-worker` (`i-03c1a7907dd03b6bb`) |
| AMI | Amazon Linux 2023 arm64 (`ami-0e576970884d94ada`, 백엔드 서버와 같음) |
| 유형 | `t4g.micro` (1GiB) |
| 인스턴스 프로파일 | 회사 제공 `ec2-project` |
| 접속 | Session Manager만. 키 페어 없음 |
| 보안 그룹 | 인바운드 없음. 아웃바운드 HTTPS(SQS·S3·SSM·백엔드) |
| 메타데이터 | IMDSv2 필수, **응답 홉 제한 2** |
| 태그 | `Service=techcourse`, `Role=techcourse-etc`, `ProjectTeam=chalkak` |

- 홉 제한이 1이면 Docker 컨테이너 안에서 인스턴스 메타데이터에 닿지 못해 역할 자격 증명을 받지 못한다. 권한이 있어도 SQS·S3 호출이 실패한다. 인스턴스 → 작업 → 인스턴스 설정 → 인스턴스 메타데이터 옵션 수정에서 바꾼다.

서버 준비는 Session Manager에서 다음을 실행했다. 세션은 `ssm-user`로 들어가므로 `sudo bash`로 넘긴다.

```bash
sudo bash <<'EOF'
dnf -y install docker amazon-cloudwatch-agent
if ! grep -q '^/swapfile ' /proc/swaps; then
  [ -f /swapfile ] || { dd if=/dev/zero of=/swapfile bs=1M count=1024 status=none; chmod 0600 /swapfile; mkswap /swapfile; }
  swapon /swapfile
fi
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf && sysctl -q -p /etc/sysctl.d/99-swappiness.conf
systemctl enable --now docker
EOF
```

swap 1GiB·swappiness 10은 AL2023 백엔드 서버와 같은 값이다.

## 2. SQS

| 항목 | 값 |
| --- | --- |
| 이름 | `chalkak-filter-extraction` (dev·prod 공용) |
| URL | `https://sqs.ap-northeast-2.amazonaws.com/843255971531/chalkak-filter-extraction` |
| 유형 | 표준 |
| 표시 제한 시간 | 180초 |
| 수신 대기 시간 | 20초 |
| 보존 기간 | 4일 |
| DLQ | 없음 |

- DLQ를 두지 않는 대신 워커가 메시지를 받자마자, 추출 전에 `ApproximateReceiveCount`를 본다. 5 이상이면 추출하지 않고 실패 콜백을 보낸 뒤 지운다. 추출 중 메모리 부족으로 계속 죽는 메시지도 5번째 수신에서 정리된다.
- 워커가 오래 꺼져 있던 동안 쌓인 메시지는 보존 기간이 지나면 사라진다. 작가 화면은 그 전에 백엔드의 추출 제한 시간으로 실패를 보여 준다.
- 기존 이미지 처리 큐(`chalkak-image-processing`)는 쓰지 않는다. 그 큐는 Lambda가 모든 메시지를 소비하고, 알림 접두사 `chalkak/staging/`은 lifecycle로 지워지는데 견본은 보관해야 한다.

액세스 정책은 계정 소유자 기본 문장에 버킷의 `SendMessage` 허용을 더한다. 이 문장이 없으면 S3 이벤트 알림 저장이 실패한다.

```json
{
  "Version": "2012-10-17",
  "Id": "__default_policy_ID",
  "Statement": [
    {
      "Sid": "__owner_statement",
      "Effect": "Allow",
      "Principal": { "AWS": "843255971531" },
      "Action": "SQS:*",
      "Resource": "arn:aws:sqs:ap-northeast-2:843255971531:chalkak-filter-extraction"
    },
    {
      "Sid": "AllowFilterProbeEvents",
      "Effect": "Allow",
      "Principal": { "Service": "s3.amazonaws.com" },
      "Action": "SQS:SendMessage",
      "Resource": "arn:aws:sqs:ap-northeast-2:843255971531:chalkak-filter-extraction",
      "Condition": {
        "ArnEquals": { "aws:SourceArn": "arn:aws:s3:::techcourse-project-2026" },
        "StringEquals": { "aws:SourceAccount": "843255971531" }
      }
    }
  ]
}
```

## 3. S3

| 항목 | 값 |
| --- | --- |
| 이벤트 알림 | `chalkak-filter-probes`: 모든 객체 생성 이벤트, 접두사 `chalkak/filter-probes/` → SQS `chalkak-filter-extraction` |
| 견본 원본 | `chalkak/filter-probes/{env}/{probeId}.jpg`. 보관한다. lifecycle 규칙 없음 |
| 레시피 | `chalkak/filters/{env}/recipes/{recipeId}.json` |

- 기존 `chalkak/staging/` 알림과 접두사가 겹치지 않는다.
- 버킷은 버전 관리가 켜져 있다. 객체를 지워도 이전 버전이 남는다.
- CloudFront 원본 경로가 `chalkak/` 전체라 레시피 경로도 별도 설정 없이 CDN으로 제공된다. 견본 경로도 키를 알면 CDN으로 받을 수 있으므로, 견본 키(`probeId`)는 올린 작가 본인 외에는 API로 내주지 않는다.

## 4. 권한

`ec2-project` 역할은 직접 고치지 않는다. 2026-11-11 워커 EC2에서 시험한 결과, 기존 권한만으로 아래가 모두 됐다. 별도 요청은 하지 않았다.

| 시험 | 결과 |
| --- | --- |
| 인스턴스 역할 | `assumed-role/ec2-project/i-03c1a7907dd03b6bb` |
| 견본 경로 쓰기·읽기 (`chalkak/filter-probes/dev/`) | 성공 |
| 레시피 경로 쓰기 (`chalkak/filters/dev/recipes/`) | 성공 |
| 견본 업로드 → 알림 → 큐 메시지 | 성공 |
| SQS 수신·숨김 연장·삭제·속성 조회 | 성공 |
| Docker 컨테이너 안에서 역할 획득 | 성공 |

- prod 경로(`.../prod/`)는 아직 시험하지 않았다. 워커 배포 때 확인한다.
- 워커와 백엔드 서버는 같은 역할을 쓴다. 워커에 필요한 권한은 백엔드 서버에도 있다.

권한을 다시 확인할 때는 Session Manager에서 다음을 실행한다. 마지막에 테스트 객체를 지운다.

```bash
B=s3://techcourse-project-2026
Q=https://sqs.ap-northeast-2.amazonaws.com/843255971531/chalkak-filter-extraction
aws sts get-caller-identity --query Arn --output text
echo test | aws s3 cp - $B/chalkak/filter-probes/dev/perm-test.txt && echo PROBE_PUT_OK
aws s3 cp $B/chalkak/filter-probes/dev/perm-test.txt - && echo PROBE_GET_OK
echo '{}' | aws s3 cp - $B/chalkak/filters/dev/recipes/perm-test.json && echo RECIPE_PUT_OK
sleep 5
aws sqs receive-message --region ap-northeast-2 --queue-url $Q --wait-time-seconds 10 --query 'Messages[].Body' --output text
sudo docker run --rm public.ecr.aws/aws-cli/aws-cli:latest sts get-caller-identity --query Arn --output text
aws s3 rm $B/chalkak/filter-probes/dev/perm-test.txt
aws s3 rm $B/chalkak/filters/dev/recipes/perm-test.json
```

받은 테스트 메시지는 SQS 콘솔의 메시지 폴링 화면에서 지운다.

## 5. 로그

| 항목 | 값 |
| --- | --- |
| 로그 그룹 | `/chalkak/filter-worker` (dev·prod 공용, 로그에 환경을 함께 남긴다) |

CloudWatch Agent 수집 설정은 워커 배포 때 이 문서에 더한다.
