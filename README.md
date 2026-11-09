# be — MWT Tour Server

미스터 월드 테마 여행 서비스(MWT)의 서버(Tour Server)입니다. Spring Boot 애플리케이션 하나로 배포하고, 그 안을 기능별 모듈(패키지)로 나눕니다(모듈러 모놀리스). 구조와 규칙은 [설계산출물](https://github.com/UOS-SE-TOUR/docs/tree/main/설계산출물) v0.5의 4장을 따릅니다.

## 실행

준비물은 JDK 21과 Docker(로컬 MySQL용)입니다.

```bash
# 로컬 MySQL 8.4를 띄우고 접속 준비가 끝날 때까지 기다린다 (DB 이름 mwt, 계정 mwt / 비밀번호 mwt)
docker compose up -d --wait
# 로그인 토큰 서명 키를 .env에 만들어 둔다 (없으면 서버가 뜨지 않는다. 이미 있으면 그대로 둔다)
grep -qs '^JWT_SECRET=' .env || printf '\nJWT_SECRET=%s\n' "$(openssl rand -hex 32)" >> .env
# 서버를 띄운다 (http://localhost:8080)
./gradlew bootRun
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
| `JWT_SECRET` | (없음, 꼭 넣어야 함) | 로그인 토큰(JWT) 서명 키. 32바이트(영문·숫자 32자) 이상으로, `openssl rand -hex 32`로 만듭니다. 비어 있거나 짧으면 서버가 뜨지 않습니다. 서버(로컬·운영 등)마다 다른 값을 씁니다 — 같으면 한 서버에서 받은 토큰이 다른 서버에서도 통합니다 |
| `SENS_ACCESS_KEY` | (없음) | NCP API 인증키의 Access Key ID(문자 발송) |
| `SENS_SECRET_KEY` | (없음) | 위 Access Key의 Secret Key |
| `SENS_SERVICE_ID` | (없음) | SENS SMS 프로젝트의 서비스 ID(`ncp:sms:kr:…` 꼴) |
| `SENS_SENDER_NUMBER` | (없음) | SENS 콘솔에 등록한 발신 번호(하이픈은 있어도 됩니다) |

`SENS_`로 시작하는 네 값이 모두 비어 있으면 문자를 보내지 않고 보낼 내용을 서버 로그에만 남깁니다(가짜 발송기, 테스트·시연 연습용). 일부만 넣으면 서버가 뜨지 않습니다.

`JWT_SECRET`을 바꾸면 그 전에 받은 로그인 토큰이 모두 무효가 되어 다시 로그인해야 합니다. 테스트는 이 값 대신 `src/test/resources/config/application.yml`의 테스트용 키를 씁니다.

모듈을 만들면서 직원 코드(member), OpenAI API 키(voice)를 더합니다. 이런 값은 코드나 커밋에 넣지 않고 환경변수로 받습니다.

내 컴퓨터에서 값을 바꿀 때는 둘 중 하나를 씁니다. 둘 다 `.gitignore`에 들어 있어 커밋되지 않습니다.

- **`.env`:** 레포 맨 위에 `DB_PASSWORD=secret`처럼 위 표의 이름을 한 줄에 하나씩 적으면 `./gradlew bootRun` 할 때 읽습니다(테스트에는 쓰이지 않습니다).
  - `.env`는 Java properties 파일로 읽습니다. 위 표의 이름과 `server.port=9090`처럼 점으로 쓴 Spring 설정 이름은 적용되고, `SERVER_PORT`·`SPRING_PROFILES_ACTIVE`처럼 환경변수 꼴(대문자·밑줄)로 쓴 Spring 설정 이름은 적용되지 않습니다. 그런 값은 명령 앞에 환경변수로 줍니다.
  - 따옴표나 `export` 없이 `이름=값`으로만 쓰고, 값을 비워 두지 않으며, 값에는 공백·`#`·`\`·`$`를 쓰지 않습니다. 서버(Java properties 문법)와 `docker compose`가 같은 파일을 읽는데, 이런 문자는 둘이 다르게 해석합니다.
  - `DB_USERNAME`·`DB_PASSWORD`는 `docker compose`도 읽어 MySQL 계정을 만듭니다. `DB_USERNAME`에 `root`는 쓸 수 없습니다(MySQL 이미지가 거부). MySQL은 데이터 볼륨을 처음 만들 때만 이 값을 쓰므로, 바꾼 뒤에는 `docker compose down -v`로 볼륨을 지우고 다시 띄웁니다. DB 이름(`mwt`)과 포트(3306)는 `compose.yaml`에 고정이라, `DB_URL`을 바꾸면 `compose.yaml`도 함께 고칩니다.
- **`application-local.yml`:** 레포 맨 위에 두고 `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`처럼 local 프로필을 켜야 읽습니다. `src/main/resources/` 아래(하위 폴더 포함)에 두면 비밀 값이 실행 파일(jar)에 들어가지 않도록 빌드에서 빠지므로 읽히지 않습니다. `.yaml`·`.properties`로 만들어도 커밋되지 않습니다.

## 화면과 연결

화면(React)은 정적 호스팅에 따로 배포하고, 호스팅의 프록시(rewrite) 기능으로 `/api/*` 요청을 이 서버로 넘깁니다. 브라우저에는 화면과 서버가 같은 주소로 보이므로 로그인 쿠키(HttpOnly, SameSite=Lax)가 그대로 실리고, 서버에는 CORS 설정을 두지 않습니다.

로컬에서는 화면 개발 서버의 프록시로 `/api`를 `http://localhost:8080`에 넘깁니다.

**서버 API 주소는 모두 `/api/v1`로 시작합니다**(설계산출물 4.9 서버 API 표, 예: `/api/v1/members/login`). 그래서 화면 호스팅의 프록시, 서버 앞 웹 서버, 로컬 화면 개발 서버의 프록시는 모두 `/api`로 시작하는 경로를 떼지 않고 그대로 넘깁니다. 어느 한 곳이라도 앞부분을 떼면 서버에서 404가 납니다.

## API 문서(Swagger)

`./gradlew bootRun`으로 서버를 띄운 뒤 브라우저에서 http://localhost:8080/api/swagger-ui.html 을 엽니다. 문서 원본(OpenAPI JSON)은 `/api/docs`이고, `/api/v1/...` 주소만 문서에 실립니다. 문서도 `/api` 아래에 두어서 화면 호스팅이나 Nginx 프록시를 거쳐도 같은 주소로 열립니다.

## 오류 응답

API가 실패하면 상태 코드와 함께 본문을 늘 아래 모양 하나로 돌려줍니다. 화면은 `message`를 그대로 보여 주면 됩니다.

```json
{"message": "남은 자리보다 인원이 많습니다."}
```

| 상태 코드 | 언제 |
| --- | --- |
| 400 | 입력 오류와 업무 규칙 위반, 요청 본문(JSON) 형식 오류, 필수 요청 값이 없거나 형식이 틀림 |
| 401 | 로그인하지 않았거나 로그인이 만료됨 |
| 403 | 권한이 없음 |
| 404 | 없는 주소(권한 규칙에 없는 주소는 401·403 — 아래 "로그인과 권한") |
| 500 | 서버 오류. 문구는 늘 "서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요."이고 자세한 내용은 서버 로그에만 남습니다 |

업무에 따라 다른 4xx(409 등)를 쓰기도 합니다. 서버 코드는 이렇게 씁니다.

- 업무 규칙을 어기면 `throw new BusinessException("남은 자리보다 인원이 많습니다.");`처럼 예외를 던지기만 합니다(400). 다른 4xx가 필요하면 `new BusinessException(HttpStatus.CONFLICT, "...")`로 상태를 줍니다. 응답 모양은 `com.mwt.common.exception`이 정하므로 Controller에서 오류 응답을 직접 만들지 않습니다.
- 요청 DTO의 검증 애너테이션에는 화면에 보일 문구를 `message`로 적습니다(예: `@NotBlank(message = "아이디를 입력해 주세요.")`). 검증 오류가 여럿이면 필드 이름 순으로 첫 문구 하나만 돌려줍니다.

## 로그인과 권한

로그인하면 서버가 로그인 토큰(JWT)을 `accessToken` 쿠키(HttpOnly)로 내려 주고, 브라우저는 요청마다 이 쿠키를 실어 보냅니다. 토큰에는 회원 ID와 회원 구분(고객·직원)이 들어 있고 24시간 뒤 만료됩니다(설계산출물 4.10). 서버는 요청마다 쿠키의 토큰을 확인하고, 설계산출물 4.9 서버 API 표의 "쓸 수 있는 사람"대로 주소마다 막습니다.

| 주소(`/api/v1/` 뒤) | 쓸 수 있는 사람 |
| --- | --- |
| `POST members/signup`, `POST members/login`, `POST members/logout` | 누구나(로그아웃은 토큰이 만료되었어도 됩니다) |
| `members/me` | 로그인한 회원 |
| `tours/**`, `bookings/**`, `voice/**` | 고객 |
| `staff/**` | 직원 |
| 위에 없는 주소 | 아무도 쓸 수 없음 |

API 문서(Swagger) 주소는 로그인 없이 열립니다.

- 로그인하지 않았거나 토큰이 만료되었거나 틀리면 401 `{"message": "로그인이 필요합니다."}`, 로그인했지만 권한이 없으면 403 `{"message": "권한이 없습니다."}`입니다.
- **위 표에 없는 주소는 Controller가 있어도 막습니다**(로그인 전 401, 로그인 뒤 403). 표의 꼴 밖에 API를 새로 만들면 `common.security`의 `SecurityConfig`에 규칙을 더합니다.
- Controller는 로그인한 회원의 ID를 `@AuthenticationPrincipal Long memberId`로 받습니다.
- Controller를 `@WebMvcTest`로 테스트할 때는 `@Import({SecurityConfig.class, JwtTokenProvider.class, ClockConfig.class})`를 붙여야 이 규칙이 적용됩니다. 빠뜨리면 스프링 부트 기본 보안(CSRF 켜짐, 모든 주소 로그인 필요)으로 테스트하게 됩니다. 요청에는 `JwtTokenProvider.createToken(...)`으로 만든 토큰을 `accessToken` 쿠키로 실습니다(`common.security`의 `SecurityConfigTest` 참고).
- 비밀번호는 `PasswordEncoder` 빈(BCrypt)으로 암호화합니다.
- CSRF 토큰은 쓰지 않습니다. 쿠키가 SameSite=Lax라 다른 사이트에서 보낸 POST에는 쿠키가 실리지 않기 때문입니다(설계산출물 4.10). 그러니 상태를 바꾸는 API는 GET으로 만들지 않습니다.

## 패키지

모든 코드는 `com.mwt` 아래에 있습니다. 모듈마다 `package-info.java`에 하는 일을 적어 두었습니다.

| 패키지 | 하는 일 | 설계산출물 v0.5 |
| --- | --- | --- |
| `member` | 회원가입, 로그인, 로그아웃, 직원 코드 확인 | 4.2 |
| `common.security` | 로그인 토큰(JWT) 만들기와 확인, API별 권한 검사, 비밀번호 암호화 | 4.3 |
| `common.exception` | 오류 응답 형식(`{"message"}`)과 전역 예외 처리 | 4.9 |
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
- **Lombok은 이것만 씁니다:** `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`(엔티티), `@RequiredArgsConstructor`(의존성 주입), `@Slf4j`, 필요하면 `@Builder`. `@Builder`는 클래스가 아니라 필요한 필드만 받는 생성자에 붙입니다(빈 생성자와 함께 클래스에 붙이면 컴파일되지 않습니다). 엔티티에 `@Setter`·`@Data`는 쓰지 않습니다. 상태는 `Tour.addApplicants()`처럼 의미 있는 메소드로만 바꿉니다.
- **요청·응답 DTO는 `record`로** 씁니다.
- **"오늘"과 현재 시각은 주입한 `Clock`에서만 얻습니다.** `ClockConfig`가 서울 시간대 시계를 하나 만들어 둡니다. `LocalDate.now()`·`Instant.now()`처럼 인자 없는 `now()`를 부르지 않고 `LocalDate.now(clock)`처럼 시계를 넘기며, 예약 작업(`@Scheduled`)에는 `zone = "Asia/Seoul"`을 적습니다.
- **시각(신청 시각 등)은 `Instant`로 둡니다.** Hibernate가 DB에 UTC로 저장합니다. 날짜만 필요한 값(출발일 등)은 `LocalDate`로 둡니다. `LocalDateTime`은 저장값이 서버 시간대에 따라 달라질 수 있어 시각 저장에 쓰지 않습니다.
- **DB가 필요한 테스트**에는 `@Testcontainers(disabledWithoutDocker = true)`와 `@Import(TestcontainersConfiguration.class)`를 함께 붙입니다. 앞의 것은 Docker가 없을 때 실패 대신 건너뛰게 하고, 뒤의 것은 테스트용 MySQL을 붙입니다.

## 배포

서버 한 대(Lightsail)에서 Docker Compose로 띄웁니다. Nginx(HTTPS 처리, `/api` 요청을 서버로 전달), 이 서버, MySQL 세 컨테이너입니다. HTTPS 인증서는 Certbot으로 Let's Encrypt에서 받고 갱신합니다. 배포 설정은 첫 기능을 만든 뒤 따로 PR로 올립니다.

## 작업 규칙

- main이 깨지지 않도록 모두 브랜치를 만들어 PR로만 올립니다. 무료 조직 플랜이라 GitHub이 비공개 레포의 main 직접 push를 막아 주지 않으니 서로 지킵니다.
- PR은 CI(`./gradlew build`)가 통과한 뒤 "Squash and merge"로 머지합니다.
- API 키와 비밀번호는 커밋하지 않습니다.
