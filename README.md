# be — MWT Tour Server

미스터 월드 테마 여행 서비스(MWT)의 서버(Tour Server)입니다. Spring Boot 애플리케이션 하나로 배포하고, 그 안을 기능별 모듈(패키지)로 나눕니다(모듈러 모놀리스). 구조와 규칙은 [설계산출물](https://github.com/UOS-SE-TOUR/docs/tree/main/설계산출물) v0.4의 4장을 따릅니다.

## 실행

준비물은 JDK 21과 Docker(로컬 MySQL용)입니다.

```bash
docker compose up -d   # 로컬 MySQL 8.4를 띄운다 (DB 이름 mwt, 계정 mwt / 비밀번호 mwt)
./gradlew bootRun      # http://localhost:8080
```

테스트는 Testcontainers로 MySQL 8.4 컨테이너를 따로 띄워서 돕니다. 위의 로컬 MySQL은 필요 없고 Docker만 켜져 있으면 됩니다. Docker가 꺼져 있으면 DB가 필요한 테스트는 건너뛰고(skipped), CI에서는 항상 돕니다.

```bash
./gradlew test
```

## 환경변수

| 이름 | 기본값 | 뜻 |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/mwt` | MySQL 주소 |
| `DB_USERNAME` | `mwt` | MySQL 계정 |
| `DB_PASSWORD` | `mwt` | MySQL 비밀번호 |

모듈을 만들면서 직원 코드(member), JWT 비밀 키(common.security), OpenAI API 키(voice), NCP SENS 키(notification)를 더합니다. 이런 값은 코드나 커밋에 넣지 않고 환경변수로 받습니다. 내 컴퓨터에서만 쓰는 `.env`와 `application-local.yml`은 `.gitignore`에 들어 있어 커밋되지 않습니다.

## 화면과 연결

화면(React)은 정적 호스팅에 따로 배포하고, 호스팅의 프록시(rewrite) 기능으로 `/api/*` 요청을 이 서버로 넘깁니다. 브라우저에는 화면과 서버가 같은 주소로 보이므로 로그인 쿠키(HttpOnly, SameSite=Lax)가 그대로 실리고, 서버에는 CORS 설정을 두지 않습니다.

로컬에서는 화면 개발 서버의 프록시로 `/api`를 `http://localhost:8080`에 넘깁니다.

## 패키지

모든 코드는 `com.mwt` 아래에 있습니다. 모듈마다 `package-info.java`에 하는 일을 적어 두었습니다.

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

## 코드 규칙

- **DB 테이블은 Flyway로만 바꿉니다.** 엔티티를 추가하거나 고치면 `src/main/resources/db/migration/`에 SQL 파일을 하나 더합니다. 파일 이름은 `V<번호>__<설명>.sql`(예: `V1__create_member.sql`)이고, 이미 머지된 파일은 고치지 않고 새 번호로 더합니다. JPA는 `ddl-auto: validate`라 엔티티와 테이블이 안 맞으면 서버가 뜨지 않습니다.
- **마이그레이션 번호는 머지 직전에 맞춥니다.** 다른 PR이 같은 번호를 먼저 머지했으면 main의 마지막 번호 다음으로 바꿉니다.
- **Lombok은 이것만 씁니다:** `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`(엔티티), `@RequiredArgsConstructor`(의존성 주입), `@Slf4j`, 필요하면 `@Builder`. 엔티티에 `@Setter`·`@Data`는 쓰지 않습니다. 상태는 `Tour.addApplicants()`처럼 의미 있는 메소드로만 바꿉니다.
- **요청·응답 DTO는 `record`로** 씁니다.

## 배포

서버 한 대(Lightsail)에서 Docker Compose로 띄웁니다. Nginx(HTTPS 처리, `/api` 요청을 서버로 전달), 이 서버, MySQL 세 컨테이너입니다. HTTPS 인증서는 Certbot으로 Let's Encrypt에서 받고 갱신합니다. 배포 설정은 첫 기능을 만든 뒤 따로 PR로 올립니다.

## 작업 규칙

- main이 깨지지 않도록 모두 브랜치를 만들어 PR로만 올립니다. 무료 조직 플랜이라 GitHub이 비공개 레포의 main 직접 push를 막아 주지 않으니 서로 지킵니다.
- PR은 CI(`./gradlew build`)가 통과한 뒤 "Squash and merge"로 머지합니다.
- API 키와 비밀번호는 커밋하지 않습니다.
