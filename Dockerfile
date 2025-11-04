FROM ibm-semeru-runtimes:open-11-jre
WORKDIR /app
COPY target/mkm-fatjar.jar /app
COPY src/main/resources/ /app/src/main/resources/

CMD [ "/bin/sh", "-c", "java -Xmx64m -jar mkm-fatjar.jar 2>&1 | tee logs/full.logs" ]