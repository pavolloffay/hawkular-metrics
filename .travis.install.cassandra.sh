#!/bin/bash
#
# Copyright 2014-2016 Red Hat, Inc. and/or its affiliates
# and other contributors as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


set -xe

cd "${HOME}"

C_MAJOR="3"
C_MINOR="5"

# Find the closest Apache mirror
APACHE_MIRROR="$(curl --silent https://www.apache.org/dyn/closer.cgi?asjson=1 | grep "preferred" | sed -e 's/ *"preferred" *: *"\([^"]*\)"/\1/')"

VERSIONS_LIST="$(curl --silent ${APACHE_MIRROR}/cassandra/ | grep -o -E "href=\"${C_MAJOR}\.${C_MINOR}(\.[0-9]+)*/" | grep -o -E "${C_MAJOR}\.${C_MINOR}(\.[0-9]+)*")"
CASSANDRA_VERSION="$(echo "${VERSIONS_LIST}" | sort --version-sort --reverse | head --lines=1)"

CASSANDRA_BINARY="apache-cassandra-${CASSANDRA_VERSION}-bin.tar.gz"
CASSANDRA_DOWNLOADS="${HOME}/cassandra-downloads"

mkdir -p ${CASSANDRA_DOWNLOADS}

if [ ! -f "${CASSANDRA_DOWNLOADS}/${CASSANDRA_BINARY}" ]; then
  wget -O ${CASSANDRA_DOWNLOADS}/${CASSANDRA_BINARY} ${APACHE_MIRROR}/cassandra/${CASSANDRA_VERSION}/${CASSANDRA_BINARY}
else
  echo 'Using cached Cassandra archive'
fi

CASSANDRA_HOME="${HOME}/cassandra"
rm -rf "${CASSANDRA_HOME}"

tar -xzf ${CASSANDRA_DOWNLOADS}/${CASSANDRA_BINARY}
mv ${HOME}/apache-cassandra-${CASSANDRA_VERSION} ${CASSANDRA_HOME}

mkdir "${CASSANDRA_HOME}/logs"

export HEAP_NEWSIZE="100M"
export MAX_HEAP_SIZE="1G"

nohup sh ${CASSANDRA_HOME}/bin/cassandra -f -p ${HOME}/cassandra.pid > ${CASSANDRA_HOME}/logs/stdout.log 2>&1 &
