#!/bin/sh

# Aumenta limite de arquivos abertos
ulimit -n 1048576

# Ajusta limites de inotify (se permitido pelo host)
sysctl -w fs.inotify.max_user_watches=524288
sysctl -w fs.inotify.max_user_instances=512

# Executa o Traccar
exec java -Djava.net.preferIPv6Addresses=false -jar tracker-server.jar conf/traccar.xml
