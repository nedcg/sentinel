FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/verdun-app-0.0.1-SNAPSHOT-standalone.jar /verdun-app/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/verdun-app/app.jar"]
