# be — MWT Tour Server

미스터 월드 테마 여행 서비스(MWT)의 서버(Tour Server)입니다. Spring Boot 애플리케이션 하나로 배포하고, 그 안을 기능별 모듈(패키지)로 나눕니다(모듈러 모놀리스). 구조와 규칙은 [설계산출물](https://github.com/UOS-SE-TOUR/docs/tree/main/설계산출물) v0.5의 4장을 따릅니다.

## 실행

준비물은 JDK 21과 Docker(로컬 MySQL용)입니다.

```bash
# 로컬 MySQL 8.4를 띄우고 접속 준비가 끝날 때까지 기다린다 (DB 이름 mwt, 계정 mwt / 비밀번호 mwt)
docker compose up -d --wait
# 로그인 토큰 서명 키를 .env에 만들어 둔다 (없으면 서버가 뜨지 않는다. 이미 있으면 그대로 둔다)
grep -qs '^JWT_SECRET=' .env || printf '\nJWT_SECRET=%s\n' "$(openssl rand -hex 32)" >> .env
# 직원 코드도 같은 식으로 만들어 둔다 (직원으로 가입할 때 .env에 적힌 이 값을 입력한다)
grep -qs '^STAFF_CODE=' .env || printf '\nSTAFF_CODE=%s\n' "$(openssl rand -hex 8)" >> .env
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
| `STAFF_CODE` | (없음, 꼭 넣어야 함) | 직원으로 가입할 때 입력하는 직원 코드. 회사가 직원에게만 알려 주는 값이라 짐작할 수 없는 무작위 값(영문·숫자 16자 이상)으로, `openssl rand -hex 8`로 만듭니다 — 가입 API에 시도 횟수 제한이 없습니다. 비어 있거나 16바이트보다 짧으면 서버가 뜨지 않습니다(단어를 이어 붙인 값처럼 길이만 채운 것은 막지 못합니다). 앞뒤 공백은 떼고 비교합니다 |
| `SOLAPI_API_KEY` | (없음) | 솔라피 API Key(문자 발송). 솔라피 콘솔의 API Key 관리에서 만듭니다 |
| `SOLAPI_API_SECRET` | (없음) | 위 API Key의 API Secret |
| `SOLAPI_SENDER_NUMBER` | (없음) | 솔라피 콘솔에 등록한 발신 번호(하이픈은 있어도 됩니다) |
| `OPENAI_API_KEY` | (없음) | 음성으로 고르기(`POST /api/v1/voice/parse`)가 부르는 OpenAI API 키. OpenAI 대시보드의 API keys에서 만듭니다 |

`SOLAPI_`로 시작하는 세 값이 모두 비어 있으면 문자를 보내지 않고 보낼 내용을 서버 로그에만 남깁니다(가짜 발송기, 테스트·시연 연습용). 일부만 넣으면 서버가 뜨지 않습니다. 솔라피가 접수하지 못한 번호(발신 번호 미등록 등)는 이유와 함께 서버 로그(ERROR)에 남습니다. 접수된 뒤 통신사 단계에서 실패한 문자(예: 번호도용문자 차단)는 서버 로그에 남지 않으니 솔라피 콘솔의 발송 결과에서 확인합니다.

`JWT_SECRET`을 바꾸면 그 전에 받은 로그인 토큰이 모두 무효가 되어 다시 로그인해야 합니다. 테스트는 이 값 대신 `src/test/resources/config/application.yml`의 테스트용 키를 씁니다.

`STAFF_CODE`를 바꿔도 이미 가입한 직원은 그대로입니다. 테스트는 테스트용 직원 코드(위 테스트 설정 파일)를 씁니다.

`OPENAI_API_KEY`가 비어 있어도 서버는 뜹니다. 그때는 음성으로 고르기만 503을 돌려주고, 화면에서 직접 고르는 흐름은 그대로 씁니다. 테스트는 OpenAI를 부르지 않습니다 — 셸에 이 값이 있어도 테스트 설정 파일이 비워 둡니다.

API 키·서명 키·직원 코드 같은 값은 코드나 커밋에 넣지 않고 환경변수로 받습니다. 운영 서버에서는 이 값들을 `deploy/.env`에 넣습니다(아래 "배포").

내 컴퓨터에서 값을 바꿀 때는 둘 중 하나를 씁니다. 둘 다 `.gitignore`에 들어 있어 커밋되지 않습니다.

- **`.env`:** 레포 맨 위에 `DB_PASSWORD=secret`처럼 위 표의 이름을 한 줄에 하나씩 적으면 `./gradlew bootRun` 할 때 읽습니다(테스트에는 쓰이지 않습니다).
  - `.env`는 Java properties 파일로 읽습니다. 위 표의 이름과 `server.port=9090`처럼 점으로 쓴 Spring 설정 이름은 적용되고, `SERVER_PORT`·`SPRING_PROFILES_ACTIVE`처럼 환경변수 꼴(대문자·밑줄)로 쓴 Spring 설정 이름은 적용되지 않습니다. 그런 값은 명령 앞에 환경변수로 줍니다.
  - 따옴표나 `export` 없이 `이름=값`으로만 쓰고, 값을 비워 두지 않으며, 값에는 공백·`#`·`\`·`$`를 쓰지 않습니다. 서버(Java properties 문법)와 `docker compose`가 같은 파일을 읽는데, 이런 문자는 둘이 다르게 해석합니다.
  - `DB_USERNAME`·`DB_PASSWORD`는 `docker compose`도 읽어 MySQL 계정을 만듭니다. `DB_USERNAME`에 `root`는 쓸 수 없습니다(MySQL 이미지가 거부). MySQL은 데이터 볼륨을 처음 만들 때만 이 값을 쓰므로, 바꾼 뒤에는 `docker compose down -v`로 볼륨을 지우고 다시 띄웁니다. DB 이름(`mwt`)과 포트(3306)는 `compose.yaml`에 고정이라, `DB_URL`을 바꾸면 `compose.yaml`도 함께 고칩니다.
- **`application-local.yml`:** 레포 맨 위에 두고 `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`처럼 local 프로필을 켜야 읽습니다. `src/main/resources/` 아래(하위 폴더 포함)에 두면 비밀 값이 실행 파일(jar)에 들어가지 않도록 빌드에서 빠지므로 읽히지 않습니다. `.yaml`·`.properties`로 만들어도 커밋되지 않습니다.

## 화면과 연결

화면(React)은 정적 호스팅에 따로 배포하고, 호스팅의 프록시(rewrite) 기능으로 `/api/*` 요청을 이 서버로 넘깁니다. 브라우저에는 화면과 서버가 같은 주소로 보이므로 로그인 쿠키(HttpOnly, SameSite=Lax)가 그대로 실리고, 서버에는 CORS 설정을 두지 않습니다.

로컬에서는 화면 개발 서버의 프록시로 `/api`를 `http://localhost:8080`에 넘깁니다. 로그인 쿠키는 Secure라 브라우저가 HTTPS에서만 저장하는데, Chrome·Firefox는 `http://localhost`만 예외로 받아 줍니다. 그래서 로컬 화면은 `localhost` 주소로 엽니다 — 같은 와이파이의 다른 기기에서 `http://192.168.…` 같은 IP 주소로 열면 로그인 응답은 성공해도 쿠키가 저장되지 않아 다음 요청이 401입니다.

**서버 API 주소는 모두 `/api/v1`로 시작합니다**(설계산출물 4.9 서버 API 표, 예: `/api/v1/members/login`). 그래서 화면 호스팅의 프록시, 서버 앞 웹 서버, 로컬 화면 개발 서버의 프록시는 모두 `/api`로 시작하는 경로를 떼지 않고 그대로 넘깁니다. 어느 한 곳이라도 앞부분을 떼면 서버에서 404가 납니다.

## API 문서(Swagger)

`./gradlew bootRun`으로 서버를 띄운 뒤 브라우저에서 http://localhost:8080/api/swagger-ui.html 을 엽니다. 문서 원본(OpenAPI JSON)은 `/api/docs`이고, `/api/v1/...` 주소만 문서에 실립니다. 문서도 `/api` 아래에 두어서 화면 호스팅이나 Nginx 프록시를 거쳐도 같은 주소로 열립니다.

API마다 한 줄 설명 끝 괄호에 부를 수 있는 사람(누구나·로그인한 회원·고객·직원)이 있고, 실패 응답의 상태 코드와 그 뜻이 적혀 있습니다. 실패 응답의 본문은 모두 아래 "오류 응답"의 `{"message"}` 한 가지입니다. 500과, 운영 서버 앞단 Nginx가 내는 503·504는 API마다 적지 않고 아래 표에 둡니다.

**로그인한 채로 시험하기:** Swagger 화면에서 `POST /api/v1/members/login`을 "Try it out"으로 먼저 실행하면, 브라우저가 로그인 쿠키를 저장해 그 뒤 실행하는 API에 함께 실어 보냅니다. Swagger 화면과 서버가 같은 주소라서 따로 설정할 것은 없습니다. 로그아웃은 `POST /api/v1/members/logout`을 실행합니다. 쿠키는 HttpOnly라 Swagger 화면에도 보이지 않습니다. 로컬에서는 http://localhost 로 열어야 합니다(위 "화면과 연결"의 쿠키 이야기와 같습니다).

운영 서버의 Swagger(`https://<서버 주소>/api/swagger-ui.html`)에서 시험하면 운영 DB에 그대로 남습니다. 운영에서는 시험용 계정으로 `GET /api/v1/members/me`처럼 읽기만 하는 API로 시험합니다. 신청(`POST /api/v1/bookings`)을 실행하면 상품의 남은 자리가 줄고, 그 신청으로 상품이 확정되거나 이미 확정된 상품이면 실제 확정 문자가 나갑니다.

API를 새로 만들면 `@Operation(summary = "<한 줄 설명> (<부를 수 있는 사람>)")`과 실패 응답마다 `@ApiResponse(responseCode = "...", description = "...")`를 답니다. 실패 응답의 본문은 `OpenApiConfig`가 한 번에 붙이고, 여러 곳에 같은 설명(로그인 필요, 상품 이름 등)은 `common.docs`의 `ApiDescriptions` 상수를 씁니다. 한 줄 설명이나 끝 괄호가 없거나, 로그인이 필요한 API에 401·403 설명이 빠졌거나, 끝 괄호가 실제 권한 규칙(아래 "로그인과 권한")과 다르면 `ApiDocsTest`가 실패합니다. 응답·요청에 새로 쓰는 열거형에는 `@Schema(enumAsRef = true, description = "...")`로 값마다 뜻을 적고, 값을 더하면 설명에도 뜻을 더합니다(빠지면 같은 테스트가 실패합니다). 응답에 `null`이 올 수 있는 칸에는 `@Schema(nullable = true)`를 답니다.

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
| 409 | 가격 계산·신청에서 그 상품을 더는 신청할 수 없음(마감, 출발일이 오늘이거나 지남, 출발했거나 끝남). 화면은 상품 목록으로 돌아갑니다. 고른 내용만 고치면 되는 거부(남은 자리보다 많은 인원 등)는 400입니다 |
| 500 | 서버 오류. 문구는 늘 "서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요."이고 자세한 내용은 서버 로그에만 남습니다 |
| 503 | 외부 서비스를 쓸 수 없음(음성으로 고르기에 OpenAI 키가 없음 등). 운영 서버에서는 서버가 잠시 멈췄을 때(재배포 중)도 앞단 Nginx가 같은 모양으로 503을 돌려줍니다 |
| 504 | 운영 서버에서 서버 응답이 60초를 넘겨 앞단 Nginx가 끊었을 때. 요청이 이미 처리됐을 수 있으니 화면은 바로 다시 보내지 않고 결과(목록 등)를 다시 읽습니다 |

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

- 로그인(`POST members/login`) 응답 본문은 `{"memberType": "CUSTOMER", "name": "홍길동"}`이고, 새로고침 뒤 부르는 `GET members/me`도 같은 모양입니다. 화면은 쿠키의 토큰을 읽을 수 없으므로 이 값으로 로그인 상태를 둡니다. 로그아웃(`POST members/logout`, 204)은 같은 이름과 `Path=/`로 값 없이 `Max-Age=0`인 쿠키를 내려 브라우저에서 지웁니다.
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
| `common.docs` | API 문서(Swagger)에 여러 번 쓰는 설명 문구 | — |
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

운영 서버는 Lightsail 한 대이고, `deploy/compose.yaml`로 컨테이너 네 개를 띄웁니다. 서버 이미지는 레포 맨 위의 `Dockerfile`로 운영 서버 안에서 빌드합니다.

| 컨테이너 | 하는 일 |
| --- | --- |
| `nginx` | 80·443번 포트를 받습니다. HTTP는 HTTPS로 넘기고, `/api/`로 시작하는 요청만 경로를 떼지 않고 서버로 넘깁니다. 그 밖의 주소는 404입니다 |
| `app` | 이 서버. 바깥에 포트를 열지 않습니다 |
| `mysql` | MySQL 8.4. 바깥에 포트를 열지 않습니다 |
| `certbot` | HTTPS 인증서(Let's Encrypt)를 12시간마다 확인해 만료가 가까우면 갱신합니다. Nginx도 12시간마다 설정을 다시 읽어 새 인증서를 씁니다 |

배포한 뒤에는 `https://<서버 주소>/api/swagger-ui.html`에서 API 문서가 열립니다. 화면 호스팅의 프록시는 `/api/*`를 `https://<서버 주소>/api/*`로 넘기면 됩니다(위 "화면과 연결").

### 명령을 붙여 넣을 때

아래 코드 블록은 복사 버튼으로 통째로 복사해 운영 서버 터미널에 붙여 넣습니다. 앞 명령이 실패하면 뒤 명령이 돌면 안 되는 곳은 `&&`로 이어 두었습니다.

- 몇몇 줄 끝의 `< /dev/null`은 "이 명령에 넣을 입력이 없다"는 표시이니 지우지 않습니다. `docker compose exec`·`run`(컨테이너 안에서 명령을 돌리는 것)은 터미널 입력을 컨테이너로 넘기므로, 이 표시가 없으면 함께 붙여 넣은 다음 줄을 입력으로 가져가 그 줄이 돌지 않습니다.
- `docker compose logs -f`처럼 Ctrl-C를 누를 때까지 끝나지 않는 명령은 따로 칩니다.
- 명령 안의 `"<…>"`는 붙여 넣기 전에 실제 값으로 바꿉니다. 둘레의 따옴표는 그대로 둡니다. 복사해 메모장에서 고친 뒤 붙여 넣으면 편합니다. 바꾸지 않고 붙여 넣으면 그 명령이 오류를 내고 사슬이 멈춥니다.

| 자리 | 넣을 것 | 예 |
| --- | --- | --- |
| `<서버 주소>`·`<새 주소>`·`<지울 주소>` | `https://`와 `/` 없이 주소 이름만 | `abc.duckdns.org` |
| `<앞 커밋>` | 배포 기록(`~/mwt-deploys.log`)에서 고른 되돌아갈 커밋(아래 "배포 되돌리기" 1번) | `a1b2c3d4e5f6` |
| `<받은 시각>` | 백업 파일 이름의 시각. `ls -lt ~/mwt-*.sql`로 목록을 봅니다 | `20261110-143000` |

README에 명령을 새로 적을 때도 입력이 필요 없는 `exec`·`run`에는 `< /dev/null`을 붙입니다. `-T`는 가상 터미널만 끄고 입력은 그대로 넘기므로 이것으로는 막지 못합니다. 자리표시자는 따옴표 안에 둡니다. 따옴표 밖의 `<`·`>`는 셸이 파일 입출력으로 읽습니다. 여러 줄 사슬은 둘째 줄부터 `&&`나 `||`로 시작하게 씁니다. 터미널의 셸은 문법 오류가 난 줄만 버리고 다음 줄을 새 명령으로 돌리기 때문입니다.

### 운영 값(`deploy/.env`)

`deploy/.env.example`을 같은 폴더의 `.env`로 복사해 채웁니다. 이름마다 설명이 붙어 있습니다. 레포 맨 위의 `.env`(내 컴퓨터용)와는 다른 파일이고, 둘 다 커밋되지 않습니다.

- `DOMAIN`(서버 주소), `DB_USERNAME`·`DB_PASSWORD`·`MYSQL_ROOT_PASSWORD`, `JWT_SECRET`·`STAFF_CODE` 가운데 하나라도 비어 있으면 `docker compose`가 아무것도 띄우지 않고 어느 값이 비었는지 알려 줍니다.
- `JWT_SECRET`·`STAFF_CODE`는 내 컴퓨터의 값을 옮기지 않고 새로 만듭니다. DB 비밀번호도 `openssl rand -hex 16`처럼 무작위로 만듭니다.
- 확정 문자를 보내려면 솔라피 콘솔에서 그 API Key의 허용 IP에 서버 고정 IP를 더합니다.
- MySQL은 데이터 볼륨을 처음 만들 때만 DB 계정을 만듭니다. 계정 값은 처음 정한 대로 둡니다 — 바꾸려면 운영 DB(볼륨)를 지워야 합니다.

### 서버 준비 (처음 한 번)

1. Lightsail에서 Ubuntu 24.04 LTS, 메모리 2GB 인스턴스를 만들고 고정 IP를 붙입니다. 인스턴스의 네트워킹 탭에서 IPv4 방화벽과 IPv6 방화벽 둘 다에 HTTPS(443)를 더합니다(SSH 22와 HTTP 80은 처음부터 열려 있습니다).
2. DuckDNS 같은 곳에서 서버 주소(서브도메인)를 만들어 고정 IP를 가리키게 합니다.
3. SSH로 접속해 스왑을 만들고 Docker를 설치합니다. 서버에서 이미지를 빌드할 때 메모리 2GB로는 모자랄 수 있어 스왑 2GB를 더합니다.

   ```bash
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker "$USER"   # 한 번 나갔다가 다시 접속하면 sudo 없이 docker를 씁니다
   ```

4. 레포를 받습니다. 비공개 레포라 서버 전용 키를 만들고, 찍힌 공개 키를 GitHub 레포의 Settings → Deploy keys에 붙여 넣습니다("Allow write access"는 켜지 않습니다).

   ```bash
   test -f ~/.ssh/id_ed25519 || ssh-keygen -t ed25519 -N "" -f ~/.ssh/id_ed25519   # 키가 없을 때만 만듭니다
   cat ~/.ssh/id_ed25519.pub
   ```

   공개 키를 등록한 뒤에 레포를 받습니다(처음 붙을 때 GitHub 서버를 믿을지 물으면 `yes`):

   ```bash
   git clone git@github.com:UOS-SE-TOUR/be.git ~/be
   ```

5. 운영 값을 채웁니다: `cd ~/be/deploy && cp .env.example .env && chmod 600 .env` 뒤 `.env`를 고칩니다(비밀 값이 든 파일이라 나만 읽게 합니다).

### 처음 배포

Nginx는 인증서가 있어야 뜨므로 인증서부터 받습니다. 아직 Nginx가 없으니 certbot이 80번 포트를 직접 열어 받습니다(standalone 방식). 서버 주소가 이 서버의 고정 IP를 가리키고 있어야 받을 수 있습니다.

```bash
cd ~/be/deploy
docker compose run --rm -p 80:80 --entrypoint sh certbot -c 'certbot certonly --standalone --non-interactive --agree-tos --register-unsafely-without-email -d "$DOMAIN"' < /dev/null \
  && docker compose up -d --build
```

| 찍힌 것 | 할 일 |
| --- | --- |
| 인증서 받기에서 오류(서버 주소가 아직 이 서버를 가리키지 않는 등) | 뒤의 `up`은 돌지 않았습니다. 원인을 고친 뒤 블록을 다시 붙여 넣습니다 |
| `Successfully received certificate` 뒤에 `up`이 실패 | `docker compose up -d --build`만 다시 돌립니다. 블록 전체를 다시 돌리면 이미 뜬 Nginx가 80번 포트를 잡고 있어 인증서 받기에서 멈출 수 있습니다 |
| 인증서보다 `docker compose up`을 먼저 해 버림 | Nginx가 인증서를 못 찾아 다시 뜨기를 되풀이하며 80번 포트를 잡습니다. `docker compose stop nginx` 뒤에 블록을 다시 붙여 넣습니다 |

첫 빌드는 Gradle과 의존성을 받느라 몇 분 걸립니다. 다 뜨면 확인합니다.

```bash
cd ~/be/deploy
# 서버(app)가 뜰 때까지 3분까지 다시 물어보고, 마지막 상태 코드만 찍습니다(200이어야 합니다)
D=$(docker compose exec -T nginx printenv DOMAIN < /dev/null); curl -s -o /dev/null -w '%{http_code}\n' --resolve "$D:443:127.0.0.1" --max-time 10 --retry 60 --retry-delay 3 --retry-max-time 180 --retry-all-errors "https://$D/api/docs"
# 인증서 갱신이 될지 미리 봅니다(실제로 갱신하지 않습니다)
docker compose run --rm --entrypoint certbot certbot renew --dry-run --webroot -w /var/www/certbot < /dev/null
```

첫 줄이 `200`이 아니면(`000`은 연결 자체가 안 된 것) `docker compose ps`로 컨테이너가 떠 있는지 보고 `docker compose logs app nginx`로 원인을 봅니다. 갱신 미리 보기는 마지막에 `Congratulations, all simulated renewals succeeded`가 나와야 합니다.

바깥에서도 열리는지(서버 주소·방화벽)는 내 컴퓨터에서 `curl -I "https://<서버 주소>/api/docs"`로 봅니다(`HTTP/2 200`).

처음 인증서는 standalone 방식으로 받았지만, 갱신은 Nginx가 내주는 폴더에 확인 파일을 두는 방식(webroot)으로 합니다. 갱신 설정에는 처음 받은 방식이 적혀 있으므로, 갱신을 손으로 돌릴 때는 위처럼 늘 `--webroot -w /var/www/certbot`을 붙입니다. 붙이지 않으면 certbot이 80번 포트를 직접 열려다 Nginx와 부딪쳐 실패합니다. `certbot` 컨테이너는 이 옵션을 붙여 돌고, 첫 갱신에 성공하면 설정도 webroot로 바뀝니다.

### 재배포

main에 머지된 코드를 받아 다시 빌드해 띄웁니다. 운영 서버에서 아래 한 줄을 돌립니다.

```bash
~/be/deploy/deploy.sh
```

배포 스크립트(`deploy/deploy.sh`)는 아래 일곱 단계를 차례로 하며, 단계마다 `== 1/7 …`처럼 몇째 단계인지 찍습니다. 다 되면 "배포했습니다: <커밋> (배포 전 백업: <파일>)"을 찍습니다. 한 단계라도 실패하면 거기서 멈추고, 새 버전을 띄우기 전에 멈췄는지(운영은 앞 버전 그대로) 띄운 뒤에 멈췄는지를 찍습니다. 성공하든 멈추든 결과는 배포 기록에 한 줄 남습니다(아래 "배포 기록").

1. main을 받습니다(`git pull --ff-only`). 받지 못한 채 이어 가면 지금 있는 코드를 그대로 재배포하게 되므로 여기서 멈춥니다. 배포를 되돌렸거나 앞 배포가 띄우기 전에 멈춰 서버의 코드가 main에 있지 않으면, 그렇다고 알린 뒤 묻지 않고 main으로 돌아가 받습니다. 그러니 배포를 되돌린 뒤에는 고친 버전이 main에 머지된 뒤에만 돌립니다(아래 "배포 되돌리기").
2. Nginx·Certbot 이미지를 새로 받고, 받아 온 Nginx 설정(`nginx/templates`)에 틀린 곳이 없는지 그 새 이미지로 봅니다.
3. 서버 이미지를 베이스(JRE)까지 새로 받아 빌드합니다(보안 수정이 들어가게). 빌드하는 동안에는 앞의 서버가 그대로 응답합니다.
4. 새 서버를 띄우기 바로 전에 DB를 백업하고 끝까지 받아졌는지 봅니다. DB 테이블 변경(Flyway)은 서버가 뜰 때 저절로 적용되는데, 마이그레이션이 중간에 실패하면 되돌릴 수 없기 때문입니다. 빌드 뒤에 받아야 빌드하는 몇 분 동안 들어온 가입·신청도 백업에 들어갑니다. 끝까지 받아진 것을 확인한 뒤에야 백업 파일에 `~/mwt-<받은 시각>.sql` 이름을 붙입니다.
5. 바뀐 컨테이너를 다시 만들고, Nginx는 늘 새로 만듭니다. Nginx 설정은 컨테이너가 시작할 때만 채워지기 때문이고, 끊기는 것은 0.2초쯤입니다. 서버 컨테이너를 바꿔 띄우는 몇십 초 동안은 503(`{"message": "서버에 잠시 연결할 수 없습니다. …"}`)이 납니다.
6. 서버(`app`)가 뜰 때까지 3분까지 기다려 봅니다. 바깥 DNS를 거치지 않고 이 서버의 Nginx로 바로 묻습니다.
7. 다 됐으면 "배포했습니다"를 찍은 뒤, 앞 배포의 이미지와 일주일 넘게 안 쓴 빌드 캐시를 지우고 백업 파일은 최근 10개만 남겨 디스크가 차지 않게 합니다. 이 정리만 실패하거나 정리 도중 Ctrl-C로 끊어도 배포는 끝난 것입니다.

알아 둘 것:

- 서버 계정으로 sudo 없이 돌립니다. root로 돌리면 잠금·배포 기록·백업이 `/root` 아래로 갈라지므로 스크립트가 거절합니다.
- 다른 배포나 백업 되살리기가 돌고 있으면 "다른 배포나 백업 되살리기가 돌고 있습니다"를 찍고 아무것도 하지 않습니다. 그 작업이 끝난 뒤 다시 돌립니다.
- 새 버전을 띄우기 전에 멈추면 서버의 코드와 서버 이미지 이름(`mwt-app`)을 배포 전으로 돌려놓습니다. 코드나 서버 이미지가 떠 있는 버전과 어긋나면, 그 뒤 `~/be/deploy`에서 치는 `docker compose` 명령(백업 되살리기의 `up -d app` 등)이 다른 설정이나 배포하지 않은 서버를 쓰기 때문입니다. 2단계에서 새로 받은 Nginx·Certbot 이미지는 돌려놓지 않습니다 — 같은 버전 줄의 패치라, 뒤에 `docker compose up -d`를 치면 그 패치로 다시 만들어질 뿐입니다. main을 받아 앞으로 간 뒤에 멈췄으면 코드는 main 브랜치가 아니라 배포 전 커밋에 머뭅니다. 다음 배포가 알아서 main으로 돌아갑니다.
- SSH 연결이 끊겨도 배포는 서버에서 이어 갑니다. 다시 접속하면 먼저 배포가 끝났는지 봅니다. 끝나기 전에는 백업 되살리기 같은 다른 작업을 하지 않습니다.

  ```bash
  flock -n ~/.mwt-deploy.lock true && echo "배포가 끝났습니다" || echo "배포(또는 백업 되살리기)가 아직 돌고 있습니다"
  ```

  끝났으면 배포 기록(`tail -n 5 ~/mwt-deploys.log`)으로 결과를, 배포 출력(`tail -n 30 ~/mwt-deploy-output.log`)으로 마지막 배포가 찍은 내용을 봅니다.
- 스크립트는 시작할 때 자신을 임시 파일로 복사해 그 사본으로 끝까지 돕니다. 그래서 1번에서 받은 코드가 배포 스크립트를 바꿨어도 이번 배포는 받기 전의 스크립트로 돌고, 바뀐 스크립트는 다음 배포부터 씁니다. 배포를 되돌린 뒤의 첫 배포도 되돌린 커밋에 있던 스크립트로 돕니다.
- 서버에 `deploy/deploy.sh`가 없다고 나오면(이 스크립트가 들어오기 전의 코드에 있을 때) 먼저 `cd ~/be && git checkout main && git pull --ff-only`로 main을 받은 뒤 다시 돌립니다.

스크립트가 끝날 때 내는 종료 코드는 아래와 같습니다. 사람은 찍힌 문구를 보면 되고, 종료 코드는 스크립트를 부르는 쪽(GitHub Actions 배포 버튼, #53)이 봅니다.

| 종료 코드 | 뜻 | 배포 기록 |
| --- | --- | --- |
| 0 | 성공(정리만 실패했거나 정리 도중 Ctrl-C로 끊었어도) | 남음 |
| 1 | 단계가 실패해 멈춤 | 남음 |
| 2 | 시작하기 전에 거절함(사용법이 틀림, 되돌릴 커밋을 받을 수 없음, root로 돌림) | 남지 않음 |
| 3 | 다른 배포나 백업 되살리기가 돌고 있음 | 남지 않음 |

드물게 시작 준비(임시 사본·잠금 파일 만들기)에서 실패해도 1로 끝나는데, 이때는 기록이 남지 않습니다. 서버가 꺼지는 등으로 스크립트가 강제로 끝나도 마찬가지입니다.

MySQL 이미지는 재배포 때 받지 않습니다. 새 패치를 받으면 배포 도중에 DB가 다시 켜지며 그동안 서버가 500을 내기 때문입니다. MySQL 보안 수정을 넣을 때는 쓰는 사람이 적은 때에 백업(아래 "DB 백업")을 받은 뒤 `docker compose pull mysql && docker compose up -d mysql`을 따로 돌립니다. 다시 켜지는 동안(보통 몇십 초) 서버는 DB에 못 붙다가 저절로 다시 붙습니다.

### 배포 기록

배포 스크립트는 배포·되돌리기마다 결과를 `~/mwt-deploys.log`에 한 줄씩 남깁니다(배포 기록). 마지막 배포가 찍은 내용 전체는 따로 `~/mwt-deploy-output.log`에 남습니다(배포 출력, 마지막 한 번 것만).

```bash
tail -n 20 ~/mwt-deploys.log
```

한 줄의 칸은 탭으로 나뉘고, 차례로 시각(서울), 종류(배포·되돌리기), 커밋(12자리 해시), 결과, 배포(되돌리기) 전 백업 파일입니다. 아래 예는 읽기 쉽게 칸을 맞춰 보였습니다.

```
2026-11-12 14:30:05  배포      a1b2c3d4e5f6  성공            /home/ubuntu/mwt-20261112-142950.sql
2026-11-13 10:02:11  배포      d4e5f6a7b8c9  띄우기 전 멈춤  -
2026-11-13 10:20:40  배포      d4e5f6a7b8c9  성공            /home/ubuntu/mwt-20261113-102012.sql
2026-11-13 15:40:02  배포      d4e5f6a7b8c9  성공            /home/ubuntu/mwt-20261113-153930.sql
```

| 결과 칸 | 뜻 |
| --- | --- |
| `성공` | 그 커밋이 운영에 떴습니다 |
| `띄운 뒤 멈춤` | 그 커밋을 띄우다 멈췄습니다. 반쯤 떠 있을 수 있습니다 |
| `띄우기 전 멈춤` | 운영은 바뀌지 않았습니다. 커밋 칸은 배포하려던 커밋입니다(재배포가 main을 받기 전에 멈췄으면 배포 전 커밋) |

결과 끝에 `(중단)`이 붙은 줄은 Ctrl-C 등으로 멈춘 배포입니다. `(중단)`을 뺀 결과와 뜻이 같습니다.

### 배포 되돌리기

새 버전에 문제가 있으면 앞 버전을 재배포합니다. 서버만이 아니라 Nginx 설정과 compose 설정도 앞 버전으로 돌아갑니다. 아래 순서대로 합니다.

**1. 되돌아갈 커밋을 고릅니다.** 문제가 생긴 버전 바로 앞에 운영에서 잘 돌던 버전입니다. 배포 기록(위 "배포 기록")을 맨 아래에서 위로 읽어, 문제가 생긴 커밋이 아닌 커밋의 `성공` 줄을 처음 만나면 그 커밋으로 되돌립니다. 되돌리기 줄도 배포 줄과 똑같이 셉니다. 위 예에서 d4e5f6a7b8c9에 문제가 있으면, 아래에서부터 d4e5f6a7b8c9 줄 셋을 지나 처음 만나는 a1b2c3d4e5f6으로 되돌립니다. 커밋 칸의 12자리 해시를 그대로 옮겨 씁니다(짧게 줄이면 커밋이 늘수록 여러 커밋에 걸릴 수 있습니다).

문제가 생긴 배포가 `띄우기 전 멈춤`이면 운영은 앞 버전 그대로이니 되돌리지 않습니다.

기록에 그 배포가 없으면(배포 스크립트가 들어오기 전의 배포) `cd ~/be && git reflog -10`을 봅니다. `pull`이나 `checkout`이라고 적힌 줄이 코드가 그 커밋으로 옮겨 간 기록입니다. 받기만 하고 배포는 멈춘 커밋도 섞여 있으니, 운영에서 잘 돌았던 커밋인지는 그 배포를 한 사람에게 확인합니다.

**2. 앞 버전이 모르는 DB 테이블 변경이 DB에 있는지 봅니다.** 서버가 뜰 때 적용한 DB 테이블 변경(마이그레이션)은 DB 안의 Flyway 기록(`flyway_schema_history` 테이블)에 남습니다. Flyway는 서버가 뜰 때 마이그레이션을 적용하는 도구입니다. 아래 명령은 그 기록과 앞 커밋의 마이그레이션 파일을 견주어, DB에는 적용됐는데 앞 버전은 모르는 것을 찍습니다. 커밋끼리 견주지 않고 DB를 보는 것은, 띄우기 전에 멈춘 배포가 있었거나 DB를 되살리지 않은 채 거듭 되돌린 경우에도 DB에 실제로 적용된 것을 빠짐없이 보려는 것입니다. 3번 뒤에 다시 봐도 결과는 같습니다.

```bash
cd ~/be/deploy && docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -N -u"$MYSQL_USER" mwt -e "select script from flyway_schema_history"' < /dev/null > /tmp/mwt-db-migrations.txt \
  && git -C ~/be ls-tree --name-only "<앞 커밋>" src/main/resources/db/migration/ > /tmp/mwt-old-migrations.txt \
  && [ -s /tmp/mwt-db-migrations.txt ] && [ -s /tmp/mwt-old-migrations.txt ] \
  && comm -23 <(sort /tmp/mwt-db-migrations.txt) <(sed 's#.*/##' /tmp/mwt-old-migrations.txt | sort) \
  && echo "(여기까지가 앞 버전이 모르는 마이그레이션입니다)"
```

- 마지막 줄 "(여기까지가 …)"만 찍히면 3번만 하면 됩니다.
- 그 위에 파일 이름(`V5__…sql` 꼴)이 찍히면, 그 마이그레이션을 `cd ~/be && git show main:src/main/resources/db/migration/<파일>`로 열어 보고 아래 표에서 고릅니다.
- 마지막 줄이 찍히지 않았으면 명령이 중간에 멈춘 것입니다(MySQL이 떠 있지 않음, `<앞 커밋>`을 잘못 채움, 읽어 온 목록이 비었음 등). 이때는 결과를 믿지 말고, 위 오류를 고친 뒤 다시 돌립니다.

Flyway는 앞 버전이 모르는 새 마이그레이션이 적용돼 있어도 경고만 남기고 넘어가므로, 앞 버전 서버가 그 DB로 뜰 수는 있습니다.

| 새 마이그레이션이 한 일 | 되돌린 앞 버전 서버 | 할 일 |
| --- | --- | --- |
| 테이블을 더하거나, 비어 있어도 되는(또는 기본값이 있는) 칸을 더함 | 그 DB를 그대로 씁니다 | 3번만 합니다. DB는 그대로 둡니다 |
| 기본값 없는 `NOT NULL` 칸을 더함 | 뜨고 확인도 200이지만, 신청 같은 쓰기가 500입니다(서버 로그에 `Field '…' doesn't have a default value`) | 3번 뒤에 새 버전 배포 때 받은 백업을 되살립니다 |
| 앞 버전이 쓰는 칸·테이블의 이름을 바꾸거나 지움 | 시작할 때 테이블 확인에 실패해 뜨지 못합니다(서버 로그에 `Schema validation: missing …`). 3번 명령이 "되돌리기가 앞 버전을 띄운 뒤에 멈췄습니다"로 끝납니다 | 3번을 다시 돌리지 말고 새 버전 배포 때 받은 백업을 되살립니다. 되살리기가 앞 버전 서버를 띄웁니다 |
| 새 버전의 마이그레이션이 중간에 실패함(새 버전 서버 로그에 `Migration … failed`) | MySQL은 테이블 변경을 중간에 되돌리지 않아 반쯤 바뀐 테이블과 실패 기록이 남고, 앞 버전도 그 때문에 뜨지 못하거나 쓰기가 실패할 수 있습니다 | 바로 위 줄과 같이 3번 뒤에 새 버전 배포 때 받은 백업을 되살립니다 |
| 판단이 서지 않음 | | 3번 뒤에 가입이나 신청을 한 번 해 봅니다 |

백업을 되살려야 하면 "새 버전 배포 때 받은 백업"을 이렇게 고릅니다. 되살리는 법은 아래 "백업 되살리기"에 있습니다.

- **고르는 법:** 배포 기록에서 1번에 고른 `성공` 줄 아래로 처음 나오는 `성공`이나 `띄운 뒤 멈춤` 줄의 마지막 칸 파일입니다. 위 예에서는 셋째 줄의 `mwt-20261113-102012.sql`입니다.
- **그 줄인 까닭:** 고른 버전 다음으로 DB 테이블을 처음 바꿨을 수 있는 배포가 그 줄입니다. 그 뒤 줄들의 백업에는 바뀐 테이블이 이미 들어 있습니다. 그래서 그 줄의 커밋이 문제가 생긴 커밋과 달라도 그 줄을 씁니다.
- **파일이 없을 때:** 배포는 끝날 때 `mwt-*.sql`을 어디서 받은 것이든(배포·되돌리기·손으로 받은 백업) 최근 10개만 남기므로, 그 뒤로 백업이 열 개 넘게 쌓인 채 배포했으면 파일이 지워졌을 수 있습니다. 기록에 그 줄이 없으면(배포 스크립트가 들어오기 전의 배포) `ls -lt ~/mwt-*.sql`에서 파일 이름의 시각이 새 버전을 배포한 때와 맞는 것을 고릅니다. 목록의 몇째 줄인지로 고르지 않습니다 — 그 사이에 받은 백업이 있으면 줄이 밀립니다.
- **쓰면 안 되는 백업:** 3번 명령이 받는 "되돌리기 전 백업"에는 새 버전의 테이블이 이미 들어 있어, 넣어도 소용이 없습니다.
- **넣은 뒤 확인:** 백업을 되살린 뒤 2번 명령을 다시 돌립니다. 찍히는 마이그레이션이 없거나, 모두 위 표에서 "DB는 그대로 둡니다"에 드는 것이면 됩니다. 아니면 그 백업에도 앞 버전이 못 견디는 변경이 들어 있는 것이니, 배포 기록에서 그보다 위 줄의 백업을 고릅니다.

백업을 넣으면 그 백업 뒤에 들어온 가입·신청이 사라집니다(확정 문자가 이미 나간 신청도). 사라질 기록은 3번 명령이 받은 "되돌리기 전 백업"에만 남으니, 넣기 전에 그 파일을 `mwt-`로 시작하지 않는 이름으로 복사해 둡니다(재배포가 `mwt-*.sql`을 최근 10개만 남깁니다).

**3. 앞 버전을 재배포합니다.** `"<앞 커밋>"`을 채워 돌립니다.

```bash
~/be/deploy/deploy.sh "<앞 커밋>"
```

"재배포"와 같은 단계를 거치되, 1번에서 main 대신 그 커밋으로 옮기고 되살릴 백업이 지워지지 않게 오래된 백업은 정리하지 않습니다. 이때 받는 백업은 되돌리기 직전의 DB입니다.

- 다 되면 "배포했습니다: <커밋> (앞 버전으로 되돌림, 되돌리기 전 백업: <파일>)"을 찍습니다.
- 멈추면 "되돌리기가 앞 버전을 띄우기 전에/띄운 뒤에 멈췄습니다"를 찍습니다. 띄우기 전에 멈췄으면 서버의 코드와 이미지도 되돌리기 전으로 돌려놓습니다.
- 배포 스크립트가 들어오기 전의 커밋으로 되돌려도 끝까지 돕니다.
- main에 없는 커밋(머지되지 않은 커밋), 운영 배포 설정(`deploy/compose.yaml`)이 들어오기 전의 커밋, 브랜치·태그 이름과 겹치는 해시를 주면 까닭을 찍고 아무것도 하지 않습니다.

고친 버전이 main에 머지되면 `~/be/deploy/deploy.sh`를 그대로 돌립니다. 서버의 코드가 앞 커밋에 있어도 main으로 돌아가 배포합니다. 그러니 고친 버전이 머지되기 전에는 돌리지 않습니다 — 문제가 있는 main이 재배포됩니다. 앞 커밋에 배포 스크립트가 없어 `No such file`이 나오면 위 "재배포"의 알아 둘 것대로 main을 먼저 받습니다.

### 상태와 로그

```bash
cd ~/be/deploy
docker compose ps                    # 컨테이너 상태
tail -n 5 ~/mwt-deploys.log          # 최근 배포 결과(위 "배포 기록")
tail -n 30 ~/mwt-deploy-output.log   # 마지막 배포가 찍은 내용
```

서버 로그(문자 발송 실패 등)는 `docker compose logs -f app`으로 봅니다. 끝나지 않고 계속 따라가므로 따로 치고 Ctrl-C로 멈춥니다. 다른 명령과 함께 붙여 넣으면 그 뒤의 명령이 돌지 않습니다.

Lightsail 인스턴스가 다시 켜지면 컨테이너도 저절로 다시 뜹니다. 이때 서버(`app`)가 MySQL보다 먼저 뜨면 DB에 붙지 못해 몇 번 꺼졌다 다시 뜬 뒤 붙고, 그동안은 503이 납니다. `docker compose down`은 컨테이너만 지우고 DB와 인증서(볼륨)는 남깁니다. **`docker compose down -v`는 운영 DB까지 지우니 쓰지 않습니다.**

### DB 백업

DB는 아래 명령으로 파일로 받아 둡니다. 배포 스크립트도 배포 전에 같은 mysqldump 명령으로 받습니다(끝까지 받아진 것을 확인한 뒤에 이름을 붙이는 것만 다릅니다). 파일에는 DB를 지우고 원래 문자셋으로 다시 만드는 문장까지 들어갑니다. 이름에 받은 시각(서울, 서버 로그와 같은 시간대)을 넣어 앞서 받은 백업을 덮지 않고, 회원 정보가 든 파일이라 나만 읽을 수 있게(600) 만듭니다.

```bash
cd ~/be/deploy
B=~/mwt-$(TZ=Asia/Seoul date +%Y%m%d-%H%M%S).sql; { (umask 077; docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --no-tablespaces --single-transaction --add-drop-database --databases mwt -u"$MYSQL_USER"' < /dev/null > "$B") \
  && tail -n 1 "$B" | grep -q '^-- Dump completed' || { rm -f "$B"; false; }; } && echo "백업했습니다: $B" || echo "백업하지 못했습니다(덜 받은 파일은 지웠습니다). 위 출력을 봅니다"
```

### 백업 되살리기

받아 둔 파일로 DB를 되살립니다. 파일이 DB를 통째로 지우고 다시 만들므로, 백업 뒤에 생긴 테이블은 남지 않습니다.

- **하는 일:** 배포가 돌고 있지 않고 백업 파일이 끝까지 받아진 것(마지막 줄 `-- Dump completed`)을 확인한 뒤, 서버를 멈추고 파일을 넣습니다. 넣기가 성공했을 때만 서버를 다시 띄웁니다.
- **붙여 넣는 법:** `<받은 시각>`에 되살릴 파일 이름의 시각을 넣고, 블록을 마지막 `)`까지 통째로 붙여 넣습니다. `)`까지 들어가야 돕니다.
- **잠금:** 되살리는 동안 배포 스크립트와 같은 잠금을 쥡니다. 그사이 누가 배포를 돌리면 "다른 배포나 백업 되살리기가 돌고 있습니다"로 끝납니다. 배포가 서버를 다시 띄우면 반쯤 넣은 DB에 새 버전이 붙기 때문입니다. 블록이 끝나면 잠금이 저절로 풀립니다.

```bash
cd ~/be/deploy
(
  exec 9>>~/.mwt-deploy.lock
  F="$HOME/mwt-<받은 시각>.sql" \
    && { flock -n 9 || { echo "배포나 다른 되살리기가 돌고 있습니다(위에 잠금 파일 오류가 찍혔으면 그 오류부터 봅니다). 끝난 뒤 다시 돌립니다"; false; }; } \
    && tail -n 1 "$F" | grep -q '^-- Dump completed' \
    && docker compose stop app \
    && docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER"' < "$F" \
    && docker compose up -d app \
    || echo "되살리지 못했습니다. 위 출력에서 어느 단계가 실패했는지 봅니다"
)
```

마지막 줄이 `start`가 아니라 `up -d`인 것은 서버 컨테이너를 지금 빌드된 이미지(배포를 되돌린 뒤라면 앞 버전)로 띄우기 위해서입니다.

| 보이는 것 | 할 일 |
| --- | --- |
| "배포나 다른 되살리기가 돌고 있습니다" 뒤에 "되살리지 못했습니다" | 아직 아무것도 하지 않았습니다. 그 작업이 끝난 뒤 같은 명령을 다시 돌립니다. 끝났는지는 위 "재배포"의 알아 둘 것에 있는 `flock -n …` 확인 명령으로 봅니다 |
| `No such file`·`tail: cannot open …` 뒤에, 또는 다른 오류 없이 "되살리지 못했습니다" | 파일 확인에서 멈췄습니다. DB와 서버는 그대로입니다. `ls -lt ~/mwt-*.sql`로 `<받은 시각>`을 맞게 채웠는지 보고, 파일이 있는데도 멈췄으면 끝이 잘린 파일이니 다른 백업을 고릅니다 |
| `docker compose stop app`의 오류 뒤 "되살리지 못했습니다" | 아직 DB에 아무것도 넣지 않았습니다. `docker compose ps`로 Docker와 컨테이너를 보고, 고친 뒤 같은 명령을 다시 돌립니다 |
| `ERROR`로 시작하는 MySQL 오류 뒤 "되살리지 못했습니다" | DB가 지워졌거나 반쯤 들어갔을 수 있습니다. 서버를 띄우지 말고 원인을 고친 뒤 같은 명령을 다시 돌립니다(파일이 DB를 처음부터 다시 만듭니다) |
| 서버(`app`)를 띄우다 난 오류 뒤 "되살리지 못했습니다" | 되살리기는 끝났습니다. `docker compose up -d app`만 다시 돌립니다 |

### 서버 주소 바꾸기

서버 주소(`deploy/.env`의 `DOMAIN`)는 아래 순서로 바꿉니다. 새 주소의 인증서를 먼저 받아 두고, 확인이 끝난 뒤에 쓰지 않는 인증서를 지웁니다.

1. 새 주소를 만들어(DuckDNS 등) 이 서버의 고정 IP를 가리키게 합니다. 가리키지 않으면 2번에서 인증서를 받지 못합니다.
2. 새 주소의 인증서를 받습니다. 80번 포트의 Nginx가 어느 주소로 오든 인증서 확인 파일을 내주므로 Nginx를 띄운 채 받습니다.

   ```bash
   cd ~/be/deploy
   docker compose run --rm --entrypoint certbot certbot certonly --webroot -w /var/www/certbot --non-interactive --agree-tos --register-unsafely-without-email -d "<새 주소>" < /dev/null
   ```

   받은 인증서의 이름은 `docker compose run --rm --entrypoint certbot certbot certificates < /dev/null`로 봅니다. 새 주소와 같아야 합니다.

3. `~/be/deploy/.env`(레포 맨 위의 `.env`가 아닙니다)의 `DOMAIN`을 새 주소로 고쳐 저장합니다.
4. Nginx와 certbot 컨테이너를 새 주소로 다시 만들고 열리는지 봅니다. 이때부터 Nginx는 새 주소의 인증서만 내주므로, 5번에서 화면 호스팅의 프록시 주소를 바꿀 때까지는 화면에서 서버를 부르지 못합니다(옛 주소로 부르면 인증서 이름이 맞지 않습니다). 쓰는 사람이 적은 때에 4·5번을 이어서 합니다.

   ```bash
   cd ~/be/deploy
   docker compose up -d \
     && D=$(docker compose exec -T nginx printenv DOMAIN < /dev/null) && echo "Nginx가 쓰는 주소: $D" \
     && curl -s -o /dev/null -w '%{http_code}\n' --resolve "$D:443:127.0.0.1" --max-time 10 --retry 60 --retry-delay 3 --retry-max-time 180 --retry-all-errors "https://$D/api/docs"
   ```

   | 찍힌 것 | 뜻 | 할 일 |
   | --- | --- | --- |
   | `Nginx가 쓰는 주소:` 뒤에 새 주소, 다음 줄에 `200` | 바뀌었습니다 | 5번으로 갑니다 |
   | `Nginx가 쓰는 주소:` 뒤에 옛 주소 | `.env`가 바뀌지 않았습니다 | 3번(저장했는지, 맞는 파일인지)을 보고 이 블록을 다시 붙여 넣습니다 |
   | 새 주소 다음 줄에 `000` | 새 주소로 HTTPS가 열리지 않습니다(인증서가 맞지 않거나 Nginx가 받지 않음) | 먼저 `docker compose logs nginx`로 원인을 봅니다(되돌리면 컨테이너가 새로 만들어져 이 로그가 사라집니다). 그다음 `.env`의 `DOMAIN`을 옛 주소로 되돌리고 `docker compose up -d` 합니다. 옛 인증서가 남아 있어 그대로 뜹니다 |
   | 새 주소 다음 줄에 `503` 같은 다른 숫자 | 새 주소의 HTTPS는 열렸고, 서버(`app`) 쪽 문제입니다 | 되돌리지 않습니다. `docker compose ps`·`docker compose logs app`으로 서버를 보고, 서버가 뜨면 이 블록을 다시 붙여 넣습니다 |
   | 주소도 숫자도 찍히지 않음 | `up`이 실패했거나 Nginx가 뜨지 못했습니다 | `000` 줄과 같습니다 |

5. 화면 호스팅의 프록시가 넘기는 주소를 새 주소로 바꿉니다(위 "화면과 연결"). 내 컴퓨터에서 `curl -I "https://<새 주소>/api/docs"`가 `HTTP/2 200`인지도 봅니다.
6. 쓰지 않게 된 주소의 인증서를 지웁니다. 주소를 바꿨으면 옛 주소를, 4번에서 바꾸기를 그만두고 옛 주소로 되돌렸으면 2번에서 받은 새 주소를 `<지울 주소>`에 넣습니다. 남겨 두면 그 주소가 이 서버를 가리키지 않게 된 뒤부터 12시간마다 갱신에 실패합니다. 지금 Nginx가 쓰는 주소나 `.env`의 `DOMAIN`(다음 `up`이 쓸 주소)을 넣으면 지우지 않고 멈춥니다.

   ```bash
   cd ~/be/deploy
   DEL="<지울 주소>" D= E= && D=$(docker compose exec -T nginx printenv DOMAIN < /dev/null) \
     && E=$(docker compose run --rm -T --entrypoint printenv certbot DOMAIN < /dev/null) \
     && [ -n "$DEL" ] && [ "$DEL" != "$D" ] && [ "$DEL" != "$E" ] \
     && docker compose run --rm --entrypoint certbot certbot delete --non-interactive --cert-name "$DEL" < /dev/null \
     || echo "지우지 않았습니다(지울 주소: ${DEL:-비어 있음}, Nginx가 쓰는 주소: ${D:-알 수 없음}, .env의 주소: ${E:-알 수 없음})"
   ```

   | 찍힌 것 | 할 일 |
   | --- | --- |
   | `Deleted all files relating to certificate <지울 주소>` | 끝났습니다 |
   | `Another instance of Certbot is already running` | 4번에서 새로 만든 certbot 컨테이너가 시작하며 갱신할 때가 됐는지 보는 중입니다. 잠시 뒤 다시 돌립니다 |
   | `No certificate found` | 인증서 이름이 틀렸습니다. 2번의 `certificates` 명령으로 이름을 봅니다 |
   | "지우지 않았습니다(…)"만 찍힘 | 괄호 안 주소들을 봅니다. 지울 주소가 `.env`의 주소와 같으면 `.env`를 먼저 되돌리고 `docker compose up -d` 합니다. 그 밖이면 `"<지울 주소>"`를 고쳐 다시 돌립니다 |

## 작업 규칙

- main이 깨지지 않도록 모두 브랜치를 만들어 PR로만 올립니다. 무료 조직 플랜이라 GitHub이 비공개 레포의 main 직접 push를 막아 주지 않으니 서로 지킵니다.
- PR은 CI가 통과한 뒤 "Squash and merge"로 머지합니다. CI는 모든 PR에서 `./gradlew build`를 돌리고, 운영 이미지나 운영 구성에 들어가는 파일(`Dockerfile`·`.dockerignore`·Gradle 빌드 설정·`deploy/`)을 바꾼 PR에서는 운영 이미지 빌드(`docker build .`)와 운영 Compose 확인도 돌립니다. 배포 스크립트(`deploy/deploy.sh`)를 바꾼 PR에서는 `shellcheck`로 스크립트를 봅니다(스크립트만 바뀌었으면 이미지 빌드는 돌지 않습니다).
- API 키와 비밀번호는 커밋하지 않습니다.
