# CLAUDE.md

## 프로젝트

- **MWT Tour Server** — 미스터 월드 테마 여행 서비스의 서버. 소프트웨어공학(26-2) 팀 최백문 과제.
- Spring Boot 4.1.2 / Java 21 / Gradle 래퍼 / MySQL 8.4. 기본 패키지 `com.mwt`.
- 설계 정본은 [UOS-SE-TOUR/docs](https://github.com/UOS-SE-TOUR/docs)의 설계산출물(4장 클래스도·5장 교류도·6장 상태도)과 분석산출물(유즈케이스). 클래스·메소드·API 이름은 설계를 그대로 쓴다.

## 아키텍처: 모듈러 모놀리스

- 모듈 = 패키지: `member` · `common.security` · `tour` · `booking` · `notification` · `voice` · `warehouse` (설계 4.1).
- 모듈 안은 Controller → Service → Repository, 업무 규칙은 엔티티(도메인 객체)에 둔다.
- 서버 API 주소는 설계 4.9 서버 API 표대로 모두 `/api/v1/...`로 시작한다(앞단 프록시들은 `/api`로 시작하는 경로를 떼지 않는다).
- 다른 모듈의 기능은 그 모듈이 공개한 Service·인터페이스로만 부른다. 다른 모듈의 Repository는 직접 부르지 않는다.
- 다른 모듈 엔티티의 상태 변경은 그 모듈의 Service를 거친다. 상태를 바꾸지 않는 확인·계산 메소드와 열거형은 직접 써도 된다.

## 코딩 규칙

- 주석·문서는 한국어로 쓰고, 주석은 최소화한다 — 코드를 읽으면 알 수 있는 건 안 쓴다.
- Lombok은 `@Getter`·`@NoArgsConstructor(access = AccessLevel.PROTECTED)`·`@RequiredArgsConstructor`·`@Slf4j`·`@Builder`만 쓴다. `@Builder`는 클래스가 아니라 생성자에 붙인다. 엔티티에 `@Setter`·`@Data` 금지.
- 요청·응답 DTO는 `record`로 쓴다.
- 오류는 `BusinessException`(`com.mwt.common.exception`, 기본 400)을 던지기만 한다(외부 서비스를 쓸 수 없을 때만 `ResponseStatusException` 503). 문구는 화면에 그대로 보이니 사용자가 읽을 말로 쓰고, 검증 애너테이션에도 `message`를 적는다.
- DB 스키마는 Flyway(`src/main/resources/db/migration/V<번호>__<설명>.sql`)로만 바꾼다. 머지된 마이그레이션은 고치지 않고 새 번호로 더한다(`ddl-auto: validate`).
- "오늘"과 현재 시각은 주입한 `Clock`(`ClockConfig`, Asia/Seoul)에서만 얻는다. 인자 없는 `now()`(`LocalDate.now()`·`Instant.now()` 등) 직접 호출 금지, `@Scheduled`에는 `zone = "Asia/Seoul"`.
- 시각 값은 `Instant`, 날짜만이면 `LocalDate`로 두고 `LocalDateTime`으로 시각을 저장하지 않는다.
- 비밀 값(API 키·JWT 비밀 키·직원 코드)은 환경변수로만 받는다. `.env`·`application-local.*`은 커밋 금지(.gitignore에 있음).
- 도메인 규칙은 스프링 없는 단위 테스트로, DB가 필요한 테스트는 `@Testcontainers(disabledWithoutDocker = true)`와 `@Import(TestcontainersConfiguration.class)`를 함께 붙인다. CI는 건너뛴 테스트가 있으면 실패하니 `@Disabled`로 끈 테스트를 남기지 않는다.

## 빌드 / 실행

- 실행: `docker compose up -d --wait`(로컬 MySQL) → `./gradlew bootRun`.
- 배포 설정은 `Dockerfile`·`deploy/`(운영 Compose·Nginx)에 있다. 바꾸면 README 배포 절도 함께 고친다.
- AI 기본 게이트: `src/`·`build.gradle`·`settings.gradle`·`gradle/`·`gradlew`·`.github/`를 고쳤으면 `./gradlew build`(컴파일+테스트). Docker가 꺼져 있으면 DB 테스트는 SKIPPED로 끝나니 그 사실을 PR에 적고 CI 결과로 넘긴다.
- `.claude/skills/`를 고쳤으면 `bash .claude/skills/issue/tests/run.sh`를 통과시킨다.

## Git / 협업 규칙

- `main` 직접 push 금지. 흐름: **이슈 먼저** → 최신 `main`에서 `<타입>/<기능>` 브랜치 → PR 본문 첫 줄에 `Closes #<이슈번호>`.
- 기능 작업은 `/issue <번호> be`로 한다(`.claude/skills/issue`).
- **Squash 머지 전용**(merge commit·rebase 비활성, 머지하면 브랜치 자동 삭제) — PR 제목이 곧 `main`의 커밋 메시지이므로 PR 제목은 커밋 컨벤션(`feat: ...`)을 따른다.
- 커밋 메시지·이슈·PR은 한국어로 쓴다. 커밋·PR 어디에도 AI 관여 표기(Co-Authored-By, Generated with 등)를 넣지 않는다.
- PR 본문에 머지 조건·머지 담당 문장을 넣지 않는다.
- 스테이징은 파일을 지정해서 한다 — `git add -A` 금지.
- 이슈를 새로 만들기 전에 기존 이슈와 중복되는지 확인한다. 이슈·PR 본문은 올리기 전에 처음 읽는 팀원 기준으로 가독성을 점검한다.

## 타입 / 영역 라벨

커밋·브랜치·PR·라벨에 **같은 타입 어휘**를 쓴다.

- 타입: `feat` · `fix` · `refactor` · `docs` · `chore` — 라벨은 이 5개. 커밋에는 필요하면 `build` · `ci` · `style` · `test` · `perf`도 쓴다.
- 영역(모듈과 같다): `area: member` · `area: security` · `area: tour` · `area: booking` · `area: notification` · `area: voice` · `area: warehouse`
- 이슈·PR에는 **타입 라벨 1개**를 붙이고, 작업이 한 모듈에 분명히 속하면 그 **영역 라벨 1개**를 더 붙인다. 여러 모듈에 걸치거나 모듈 밖(빌드 설정·문서·스킬) 작업은 영역 라벨 없이 둔다.
- 상태·메타 라벨: 위 타입·영역이 아닌 라벨(GitHub 기본 `bug`·`enhancement`·`question` 등).
