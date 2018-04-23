FROM openjdk:8-slim

ARG FLEET_VERSION=compiler-fleet-1.0-SNAPSHOT

ADD ./build/distributions/${FLEET_VERSION}.tar  .

WORKDIR ./${FLEET_VERSION}/bin/

ENTRYPOINT ["./compiler-fleet"]