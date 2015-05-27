FROM java:8-jre
MAINTAINER Kai Hudalla <kai.hudalla@bosch-si.com>
#ENV http_proxy http://cache.innovations.de:3128
ENV https_proxy https://cache.innovations.de:3128
#ENV no_proxy .innovations.de,.bosch-si.com,localhost

RUN mkdir /opt/scandium && \
  curl -o /opt/scandium/element-connector-1.0.0-M5.jar \
    https://repo.eclipse.org/service/local/repositories/californium-releases/content/org/eclipse/californium/element-connector/1.0.0-M5/element-connector-1.0.0-M5.jar
RUN curl -o /opt/scandium/scandium-1.0.0-M4.jar \
  https://repo.eclipse.org/service/local/repositories/californium-releases/content/org/eclipse/californium/scandium/1.0.0-M4/scandium-1.0.0-M4.jar
COPY scandium.jmeter-0.0.1-SNAPSHOT.jar /opt/scandium/

EXPOSE 5684
WORKDIR /opt/scandium
CMD java -cp element-connector-1.0.0-M5.jar:scandium-1.0.0-M4.jar:scandium.jmeter-0.0.1-SNAPSHOT.jar org.eclipse.californium.scandium.PskDtlsServer
