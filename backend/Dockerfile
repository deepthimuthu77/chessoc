FROM gradle:8.10.2-jdk17 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN gradle --no-daemon clean installDist

FROM eclipse-temurin:17-jre
WORKDIR /opt/chessoc
COPY --from=build /home/gradle/src/build/install/chessoc-server ./
EXPOSE 8080 8081
ENTRYPOINT ["/opt/chessoc/bin/chessoc-server"]
