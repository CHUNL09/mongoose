#!/bin/sh
umask 0000
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/jvm/java-1.8-openjdk/jre/bin:/usr/lib/jvm/java-1.8-openjdk/bin:/bin/bash
java -Xms1g -Xmx1g -jar /opt/mongoose/mongoose.jar "$@"
