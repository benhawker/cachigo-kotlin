FROM gradle:7.4.2-jdk11-alpine as builder
USER root
WORKDIR /builder
ADD . /builder
RUN gradle shadowJar --stacktrace

FROM adoptopenjdk/openjdk11:jre-11.0.9_11.1-alpine 
WORKDIR /app
EXPOSE 9000

COPY /suppliers.yml .
COPY --from=builder /builder/build/libs/cachigo-kotlin-all.jar .
ENTRYPOINT ["java", "-jar", "cachigo-kotlin-all.jar"]
