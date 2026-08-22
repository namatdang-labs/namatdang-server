# 백엔드 배포

백엔드 CI/CD는 `main`의 성공한 커밋에서 만든 Docker 이미지를 단일 EC2에 배포한다.

## 흐름

1. Merge 또는 Push로 `main`이 변경된다.
2. `Backend CI`가 MySQL 8.4로 Gradle build를 실행하고 Docker 이미지를 만든다.
3. CI가 이미지를 `docker save`로 묶어 7일 보관 Artifact로 올린다.
4. 해당 CI가 성공한 경우에만 `Backend CD`가 실행된다.
5. CD가 GitHub OIDC로 AWS 임시 권한을 받아 Hosted Runner의 SSH만 잠시 허용한다.
6. SCP로 Release를 전달하고 EC2에서 `docker load`한다.
7. EC2가 `compose.prod.yml`을 실행하고 `GET /api/v1/stores?size=1`로 검증한다.
8. 시작에 실패하면 이전 Compose 설정과 이미지로 복구한다.
9. CD가 자신이 생성한 보안 그룹 규칙 ID를 정확히 제거한다.

컨테이너 Registry와 장기 AWS Access Key는 사용하지 않는다.

## EC2 파일 계약

첫 배포 전에 다음 파일이 있어야 한다.

```text
/opt/namatdang/app/runtime.env
/opt/namatdang/certs/rds-truststore.p12
/opt/namatdang/secrets/db-password
/opt/namatdang/secrets/jwt-private.b64
/opt/namatdang/secrets/jwt-public.b64
```

비밀이 아닌 런타임 설정은 [runtime.env.example](./runtime.env.example)을 참고한다. DB 비밀번호, JWT 키, AWS 키, Firebase 인증정보를 이 파일에 넣으면 안 된다.

JWT 파일은 DER 바이트를 Base64로 인코딩한 값이다.

- 개인키: PKCS#8 DER
- 공개키: X.509 SubjectPublicKeyInfo DER

애플리케이션 컨테이너는 `10001:10001`로 실행된다. 비밀 파일은 이 UID가 읽을 수 있어야 하며 다른 사용자의 접근은 제한한다.

SQS 자격 증명은 EC2 Instance Profile에서 공급한다. 런타임 환경에 `AWS_ACCESS_KEY_ID`나 `AWS_SECRET_ACCESS_KEY`를 넣지 않는다. Docker 브리지에서 역할을 사용하려면 IMDSv2를 필수로 유지하고 Response Hop limit을 `2`로 설정한다.

## GitHub Environment

`production` Environment를 만들고 배포 브랜치를 `main`으로 제한한다.

Variables:

| 이름 | 용도 |
|---|---|
| `AWS_ACCOUNT_ID` | 예상하지 않은 AWS 계정 자격 증명 거부 |
| `AWS_DEPLOY_ROLE_ARN` | Backend CD가 Assume할 OIDC 역할 |
| `EC2_HOST` | EC2 Elastic IP 또는 배포 호스트명 |
| `EC2_SECURITY_GROUP_ID` | EC2의 SSH를 보호하는 보안 그룹 |
| `EC2_SSH_USER` | 전용 배포 사용자, 초기에는 `ubuntu` 가능 |

Secrets:

| 이름 | 용도 |
|---|---|
| `EC2_SSH_PRIVATE_KEY` | EC2 배포 사용자의 개인키 |
| `EC2_KNOWN_HOSTS` | 미리 검증한 대상 EC2의 SSH Host Key 행 |

개발자의 EC2 생성 키를 재사용하기보다 GitHub 배포 전용 SSH 키를 사용한다.

## AWS OIDC 역할

계정에 GitHub OIDC Provider가 없다면 다음 값으로 등록한다.

```text
Provider URL: https://token.actions.githubusercontent.com
Audience:     sts.amazonaws.com
```

역할 신뢰 정책을 이 저장소의 `production` Environment로 제한한다.

이 저장소는 2026-07-15 이후 생성되어 GitHub의 immutable OIDC subject 형식을 사용한다. 조직과 저장소 이름 뒤의 숫자는 공개 GitHub ID이며 AWS 계정 ID가 아니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:namatdang-labs@313276229/namatdang-server@1331449040:environment:production"
        }
      }
    }
  ]
}
```

배포 역할에는 대상 EC2 보안 그룹 하나의 SSH 인바운드 변경만 허용한다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:AuthorizeSecurityGroupIngress",
        "ec2:RevokeSecurityGroupIngress"
      ],
      "Resource": "arn:aws:ec2:ap-northeast-2:<ACCOUNT_ID>:security-group/<SECURITY_GROUP_ID>"
    }
  ]
}
```

## 런타임 안전장치

- 8080은 `127.0.0.1`에만 바인딩하고 호스트 Reverse Proxy를 통해 공개한다.
- RDS는 `sslMode=VERIFY_IDENTITY`와 리전 CA TrustStore를 사용한다.
- Firebase 서비스 계정을 별도로 마운트하기 전까지 Push를 비활성화한다.
- Rollback을 위해 SHA 태그 Docker 이미지를 보관하므로 EBS 사용량을 확인한다.
- Workflow를 강제 취소했다면 22번 포트에 임시 GitHub Runner `/32` 규칙이 남지 않았는지 확인한다.
