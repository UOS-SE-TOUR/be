# 운영 서버 이미지. deploy/compose.yaml이 서버에서 이 파일로 빌드한다(README 배포 절).
# 빌드도 컨테이너 안에서 하므로 서버에 JDK를 깔지 않아도 되고, 빌드하는 컴퓨터와 서버의 CPU 종류가 달라도 된다.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# 빌드에 쓰지 않는 것(테스트·문서·배포 설정·비밀 값)은 .dockerignore가 뺀다. 레포 맨 위에 gradle.properties나
# lombok.config 같은 빌드 설정이 새로 생겨도 그대로 들어가 CI·내 컴퓨터와 같은 jar가 나온다
COPY . .
RUN false
# 테스트는 CI가 돌린다(DB 테스트는 Testcontainers가 Docker를 써야 해서 이미지 빌드 안에서는 돌 수 없다).
# Gradle 캐시를 빌드 사이에 남겨, 다시 배포할 때 Gradle과 의존성을 처음부터 받지 않는다
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar && mv build/libs/*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home mwt
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER mwt
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
