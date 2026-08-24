<div align="center">

# 🥐 남았당

### 마감 재고를 판매 기회로, 첫 방문을 단골로

동네 베이커리·디저트 매장의 마감 상품을<br>
새로운 고객과 연결하는 **할인 예약 플랫폼**입니다.

</div>

---


## 서비스 소개

남았당은 베이커리와 디저트 매장이 당일 남은 상품을 할인 판매하고, 고객은 원하는 상품을 예약해 매장에서 픽업할 수 있도록 연결하는 서비스입니다.

사장님에게는 단순한 재고 처리를 넘어 **남은 상품의 매출 전환, 신규 고객 유입, 재방문 고객과의 접점**을 제공합니다. 고객에게는 동네의 새로운 가게와 좋은 상품을 발견하고 합리적인 가격에 구매할 수 있는 경험을 제공합니다.

## 남았당이 만드는 가치

| 🏪 매장 사장님 | 🙋 고객                     |
| :--- |:--------------------------|
| 남은 재고를 할인 판매해 매출로 전환 | 베이커리·디저트 상품을 합리적인 가격에 구매  |
| 상품 폐기 및 재고 처리 부담 감소 | 판매 품목과 남은 수량, 픽업 시간 확인    |
| 마감 할인을 통한 신규 고객 방문 유도 | 원하는 여러 품목을 한 번에 예약        |
| 즐겨찾기와 알림을 통한 재방문 접점 형성 | 관심 가게의 새로운 할인 소식을 알림으로 확인 |

## 이용 흐름

1. 사장님이 마감 할인과 판매 품목을 등록합니다.
2. 할인이 공개되면 즐겨찾기한 고객에게 알림을 보냅니다.
3. 고객은 원하는 품목과 수량을 선택해 예약합니다.
4. 예약한 시간에 매장을 방문해 상품을 픽업합니다.

## 대표 이미지 S3 설정

가게와 할인 상품의 대표 이미지는 private S3 버킷에만 저장합니다. 서버 실행 전에
`IMAGE_S3_BUCKET`과 `AWS_REGION`을 설정해야 하며, 버킷 이름이 없으면 서버가
시작되지 않습니다.

AWS 액세스 키는 저장소나 `.env`에 넣지 않습니다. EC2 인스턴스 프로파일 또는 ECS
태스크 역할에 아래 최소 권한을 부여합니다.

- `s3:GetObject`
- `s3:PutObject`
- `s3:DeleteObject`

권한 리소스는 `arn:aws:s3:::<IMAGE_S3_BUCKET>/images/*`로 제한합니다. 버킷의 퍼블릭
액세스 차단은 유지하며, 고객에게는 백엔드의 버전이 포함된 이미지 URL을 제공합니다.

실제 AWS 리소스는 `infra/aws/image-storage.yaml`로 생성합니다. 배포 시 전역에서 고유한
버킷 이름과 현재 백엔드가 사용하는 EC2/ECS IAM 역할 이름을 전달합니다.

```bash
aws cloudformation deploy \
  --template-file infra/aws/image-storage.yaml \
  --stack-name namatdang-image-storage \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    ImageBucketName=<production-bucket-name> \
    BackendRoleName=<backend-runtime-role-name>
```

스택 출력의 `ImageBucketName`을 `IMAGE_S3_BUCKET`으로 설정합니다. 버킷은 삭제 방지,
퍼블릭 액세스 차단, TLS 강제, SSE-S3 암호화가 적용됩니다.

### 이미지 변환 런타임

최대 15MiB의 JPG·PNG·WebP를 업로드하면 EXIF 방향을 보정한 후 WebP quality 80의
`thumbnail`(320×320), `card`(768×432), `detail`(1600×1200) 3개 중앙 크롭
파생 이미지로 변환합니다. 해당 크기보다 작은 원본은 확대하지 않으며 원본 파일은
S3에 저장하지 않습니다. 서버에는
`cwebp`가 필요합니다.

- macOS(Homebrew): `brew install webp` (`/opt/homebrew/bin/cwebp` 자동 탐지)
- Docker: `Dockerfile`이 Alpine의 `libwebp-tools`를 설치합니다.
- 다른 경로를 쓰면 `MEDIA_IMAGES_PROCESSOR_CWEBP_PATH`에 절대 경로를
  설정합니다.

---
