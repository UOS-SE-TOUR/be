# be — MWT Tour Server

미스터 월드 테마 여행 서비스(MWT)의 서버(Tour Server)입니다. Spring Boot 애플리케이션 하나로 배포하고, 그 안을 기능별 모듈(패키지)로 나눕니다(모듈러 모놀리스). 구조와 규칙은 [설계산출물](https://github.com/UOS-SE-TOUR/docs/tree/main/설계산출물) v0.4의 4장을 따릅니다.

## 실행

준비물은 JDK 21과 Docker(로컬 MySQL용)입니다.

```bash
docker compose up -d   # 로컬 MySQL 8.4를 띄운다 (DB 이름 mwt, 계정 mwt / 비밀번호 mwt)
./gradlew bootRun      # http://localhost:8080
```

테스트는 메모리 DB(H2)로 돌기 때문에 MySQL 없이도 됩니다.

```bash
./gradlew test
```

## 환경변수

| 이름 | 기본값 | 뜻 |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/mwt` | MySQL 주소 |
| `DB_USERNAME` | `mwt` | MySQL 계정 |
| `DB_PASSWORD` | `mwt` | MySQL 비밀번호 |
| `JPA_DDL_AUTO` | `update` | 테이블을 엔티티에 맞춰 고칠지(`update`), 확인만 할지(`validate`) |

모듈을 만들면서 직원 코드(member), JWT 비밀 키(common.security), OpenAI API 키(voice), NCP SENS 키(notification)를 더합니다. 이런 값은 코드나 커밋에 넣지 않고 환경변수로 받습니다. 내 컴퓨터에서만 쓰는 `.env`와 `application-local.yml`은 `.gitignore`에 들어 있어 커밋되지 않습니다.

## 패키지

모든 코드는 `kr.ac.uos.mwt` 아래에 있습니다. 모듈마다 `package-info.java`에 하는 일을 적어 두었습니다.

| 패키지 | 하는 일 | 설계산출물 v0.4 |
| --- | --- | --- |
| `member` | 회원가입, 로그인, 로그아웃, 직원 코드 확인 | 4.2 |
| `common.security` | 로그인 토큰(JWT) 만들기와 확인 | 4.3 |
| `tour` | 여행 상품, 등급별 가격, 옵션, 상품 기획, 매일 상품 상태 바꾸기 | 4.4 |
| `booking` | 가격 계산, 단골 할인, 신청, 확정 문자 요청, 이전 여행 | 4.5 |
| `notification` | 문자 발송과 실패 때 재시도 | 4.6 |
| `voice` | 음성에서 나온 글자로 테마·출발일·등급·인원 채우기 | 4.7 |
| `warehouse` | 창고 물품 목록과 추가 | 4.8 |

모듈 사이에서는 다음을 지킵니다(설계산출물 4.1).

- 다른 모듈의 기능은 그 모듈이 공개한 Service나 인터페이스로만 부릅니다. 다른 모듈의 Repository는 직접 부르지 않습니다.
- 다른 모듈 엔티티의 상태를 바꾸는 일은 그 모듈의 Service를 거칩니다. 열거형(Theme, TourLevel)은 그대로 써도 됩니다.

## 작업 규칙

- main에 직접 push하지 않고, 브랜치를 만들어 PR로 올립니다. 무료 조직 플랜이라 비공개 레포에는 브랜치 보호를 걸 수 없어서 약속으로 지킵니다.
- PR은 CI(`./gradlew build`)가 통과한 뒤 "Squash and merge"로 머지합니다.
- API 키와 비밀번호는 커밋하지 않습니다.
