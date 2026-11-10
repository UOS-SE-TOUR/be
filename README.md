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
| 503 | 외부 서비스를 쓸 수 없음(음성으로 고르기에 OpenAI 키가 없음 등). 운영 서버에서는 서버가 잠시 멈췄을 때(다시 배포 중)도 앞단 Nginx가 같은 모양으로 503을 돌려줍니다 |
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

배포한 뒤에는 `https://<DOMAIN>/api/swagger-ui.html`에서 API 문서가 열립니다. 화면 호스팅의 프록시는 `/api/*`를 `https://<DOMAIN>/api/*`로 넘기면 됩니다(위 "화면과 연결").

### 운영 값(`deploy/.env`)

`deploy/.env.example`을 같은 폴더의 `.env`로 복사해 채웁니다. 이름마다 설명이 붙어 있습니다. 레포 맨 위의 `.env`(내 컴퓨터용)와는 다른 파일이고, 둘 다 커밋되지 않습니다.

- `DOMAIN`(서버 주소), `DB_USERNAME`·`DB_PASSWORD`·`MYSQL_ROOT_PASSWORD`, `JWT_SECRET`·`STAFF_CODE` 가운데 하나라도 비어 있으면 `docker compose`가 아무것도 띄우지 않고 어느 값이 비었는지 알려 줍니다.
- `JWT_SECRET`·`STAFF_CODE`는 내 컴퓨터의 값을 옮기지 않고 새로 만듭니다. DB 비밀번호도 `openssl rand -hex 16`처럼 무작위로 만듭니다.
- 확정 문자를 보내려면 솔라피 콘솔에서 그 API Key의 허용 IP에 서버 고정 IP를 더합니다.
- MySQL은 데이터 볼륨을 처음 만들 때만 DB 계정을 만듭니다. 계정 값은 처음 정한 대로 둡니다 — 바꾸려면 운영 DB(볼륨)를 지워야 합니다.

### 서버 준비 (처음 한 번)

1. Lightsail에서 Ubuntu 24.04 LTS, 메모리 2GB 인스턴스를 만들고 고정 IP를 붙입니다. 인스턴스의 네트워킹 탭에서 IPv4 방화벽에 HTTPS(443)를 더합니다(SSH 22와 HTTP 80은 처음부터 열려 있습니다).
2. DuckDNS 같은 곳에서 서버 주소(서브도메인)를 만들어 고정 IP를 가리키게 합니다.
3. SSH로 접속해 스왑을 만들고 Docker를 설치합니다. 서버에서 이미지를 빌드할 때 메모리 2GB로는 모자랄 수 있어 스왑 2GB를 더합니다.

   ```bash
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker "$USER"   # 한 번 나갔다가 다시 접속하면 sudo 없이 docker를 씁니다
   ```

4. 레포를 받습니다. 비공개 레포라 서버 전용 키를 만들어, 공개 키를 GitHub 레포의 Settings → Deploy keys에 붙여 넣습니다("Allow write access"는 켜지 않습니다).

   ```bash
   ssh-keygen -t ed25519 -N "" -f ~/.ssh/id_ed25519
   cat ~/.ssh/id_ed25519.pub
   git clone git@github.com:UOS-SE-TOUR/be.git ~/be
   ```

5. 운영 값을 채웁니다: `cd ~/be/deploy && cp .env.example .env && chmod 600 .env` 뒤 `.env`를 고칩니다(비밀 값이 든 파일이라 나만 읽게 합니다).

### 처음 배포

Nginx는 인증서가 있어야 뜨므로 인증서부터 받습니다. 아직 Nginx가 없으니 certbot이 80번 포트를 직접 엽니다. 서버 주소가 이 서버의 고정 IP를 가리키고 있어야 받을 수 있습니다.

```bash
cd ~/be/deploy
docker compose run --rm -p 80:80 --entrypoint sh certbot -c 'certbot certonly --standalone --non-interactive --agree-tos --register-unsafely-without-email -d "$DOMAIN"'
docker compose up -d --build
```

인증서보다 `docker compose up`을 먼저 했다면 Nginx가 인증서를 못 찾아 다시 뜨기를 되풀이하며 80번 포트를 잡으니, `docker compose stop nginx` 뒤에 인증서를 받고 다시 `up` 합니다. `DOMAIN`을 바꿀 때도 같은 순서로 새 주소의 인증서를 받습니다.

첫 빌드는 Gradle과 의존성을 받느라 몇 분 걸립니다. 다 뜨면 확인합니다.

```bash
# 서버가 뜰 때까지 3분까지 다시 물어보고, 마지막 상태 코드만 찍습니다(200이어야 합니다)
curl -s -o /dev/null -w '%{http_code}\n' --retry 60 --retry-delay 3 --retry-all-errors "https://<DOMAIN>/api/docs"
# 인증서 갱신이 될지 미리 봅니다(실제로 갱신하지 않습니다). 갱신은 처음 받을 때와 달리 Nginx를 거쳐 확인합니다
docker compose run --rm --entrypoint certbot certbot renew --dry-run --webroot -w /var/www/certbot
```

인증서를 standalone으로 받았으므로 갱신 설정에도 standalone이 적혀 있습니다. 갱신을 손으로 돌릴 때는 위처럼 늘 `--webroot -w /var/www/certbot`을 붙입니다. 붙이지 않으면 certbot이 80번 포트를 직접 열려다 Nginx와 부딪쳐 실패합니다. `certbot` 컨테이너는 이 옵션을 붙여 돌고, 첫 갱신에 성공하면 설정도 webroot로 바뀝니다.

### 다시 배포

main에 머지된 코드를 받아 다시 빌드합니다. DB 테이블 변경(Flyway)은 서버가 뜰 때 저절로 적용되는데, 마이그레이션이 중간에 실패하면 되돌릴 수 없으니 **배포 전에 아래 "로그와 백업"의 백업 명령으로 DB를 받아 둡니다.** 빌드하는 동안에는 앞의 서버가 그대로 응답하고, 서버 컨테이너를 바꿔 띄우는 몇십 초 동안만 503(`{"message": "서버에 잠시 연결할 수 없습니다. …"}`)이 납니다.

```bash
cd ~/be && git pull && cd deploy
# 받아 온 Nginx 설정(nginx/templates)에 틀린 곳이 없는지 먼저 봅니다. "test is successful"이 나와야 합니다
docker compose run --rm --no-deps --entrypoint /docker-entrypoint.sh nginx nginx -t
docker compose up -d --build
# Nginx 설정은 컨테이너가 시작할 때만 채워지므로, 끊지 않고 다시 채워 읽게 합니다
docker compose exec nginx sh -c '/docker-entrypoint.d/20-envsubst-on-templates.sh && nginx -t && nginx -s reload'
# 앞 배포의 이미지와 일주일 넘게 안 쓴 빌드 캐시를 지워 디스크가 차지 않게 합니다
docker image prune -f && docker builder prune -f --filter until=168h
# 서버가 떴는지 봅니다(200이어야 합니다). 아니면 docker compose ps 와 docker compose logs app nginx 로 이유를 봅니다
# (서버가 못 뜨면 다시 뜨기를 되풀이합니다)
curl -s -o /dev/null -w '%{http_code}\n' --retry 60 --retry-delay 3 --retry-all-errors "https://<DOMAIN>/api/docs"
```

### 로그와 백업

```bash
cd ~/be/deploy
docker compose ps             # 컨테이너 상태
docker compose logs -f app    # 서버 로그(문자 발송 실패 등)
# DB를 파일로 받아 둡니다. 파일에는 DB를 지우고 원래 문자셋으로 다시 만드는 문장까지 들어갑니다.
# 이름에 받은 시각(서울, 서버 로그와 같은 시간대)을 넣어 앞서 받은 백업을 덮지 않고, 회원 정보가 든 파일이라 나만 읽을 수 있게(600) 만듭니다
(umask 077; docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --no-tablespaces --single-transaction --add-drop-database --databases mwt -u"$MYSQL_USER"' > ~/mwt-$(TZ=Asia/Seoul date +%Y%m%d-%H%M%S).sql)
```

받아 둔 파일로 되살릴 때는 서버를 멈추고 넣습니다. 파일이 DB를 통째로 지우고 다시 만들므로 백업 뒤에 생긴 테이블은 남지 않습니다(남으면 서버가 뜰 때 마이그레이션이 그 테이블을 다시 만들려다 실패합니다). 아래 명령은 백업 파일이 끝까지 받아진 것(마지막 줄 `-- Dump completed`)을 확인한 뒤에만 넣고, 넣기가 성공했을 때만 서버를 다시 띄웁니다.

```bash
F=~/mwt-<받은 시각>.sql
tail -n 1 "$F" | grep -q '^-- Dump completed' \
  && docker compose stop app \
  && docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER"' < "$F" \
  && docker compose start app \
  || echo "되살리지 못했습니다. 위 출력을 봅니다. 서버를 멈춘 뒤에 실패했으면 DB가 지워졌거나 반쯤 들어갔을 수 있으니, 서버를 띄우지 말고 원인을 고쳐 이 명령을 다시 돌립니다"
```

서버가 다시 켜지면 컨테이너도 저절로 다시 뜹니다. `docker compose down`은 컨테이너만 지우고 DB와 인증서(볼륨)는 남깁니다. `docker compose down -v`는 운영 DB까지 지우니 쓰지 않습니다.

## 작업 규칙

- main이 깨지지 않도록 모두 브랜치를 만들어 PR로만 올립니다. 무료 조직 플랜이라 GitHub이 비공개 레포의 main 직접 push를 막아 주지 않으니 서로 지킵니다.
- PR은 CI(`./gradlew build`)가 통과한 뒤 "Squash and merge"로 머지합니다.
- API 키와 비밀번호는 커밋하지 않습니다.
