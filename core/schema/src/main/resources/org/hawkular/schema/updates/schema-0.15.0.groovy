/*
 * Copyright 2014-2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

schemaChange {
  version '1.0'
  author 'jsanda'
  tags '0.15.x'
  cql """
CREATE TABLE system_settings (
    key text PRIMARY KEY,
    value text
) WITH compaction = { 'class': 'LeveledCompactionStrategy' }
"""
}

schemaChange {
  version '1.1'
  author 'jsanda'
  tags '0.15.x'
  description 'See https://issues.jboss.org/browse/HWKMETRICS-367 for details'
  cql "ALTER TABLE data WITH gc_grace_seconds = 86400"
}

schemaChange {
  version '1.2'
  author 'jsanda'
  tags '0.15.x'
  description "Add support for data point tags. See HWKMETRICS-368 for details"
  cql "ALTER TABLE data ADD tags map<text,text>"
}