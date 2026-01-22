# Base para rodar o Traccar (Java)
FROM eclipse-temurin:17-jre

# Criar diretório de trabalho
WORKDIR /opt/traccar

# Copiar todos os arquivos do Traccar para o container
COPY traccar/ /opt/traccar/
COPY entrypoint.sh /opt/traccar/entrypoint.sh

RUN chmod +x /opt/traccar/entrypoint.sh

# Expor as portas padrão do Traccar
EXPOSE 8082 9018

# Comando para iniciar o Traccar, especificando o arquivo de configuração e forçando IPv4
#CMD ["java", "-Djava.net.preferIPv6Addresses=false", "-jar", "tracker-server.jar", "conf/traccar.xml"]
ENTRYPOINT ["/opt/traccar/entrypoint.sh"]