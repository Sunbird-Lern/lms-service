# sunbird-course-service

Repository for Batch Service

## sunbird-course-Service local setup
This readme file contains the instruction to set up and run the sunbird-course-service in local machine.

### Prerequisites:
Java 11,
Maven - Latest,
Cassandra 3.11.6,
ES 6.8.11,
Redis 4.0.0,
GIT,
Docker - Latest

### Create folders for database data and logs

```shell
mkdir -p ~/sunbird-dbs/cassandra ~/sunbird-dbs/es
export sunbird_dbs_path=~/sunbird-dbs
```

### Cassandra database setup in docker:



i. Get the cassandra image using below command
```shell
docker pull cassandra:3.11.6
```
ii. For the network, we can use the existing network or create a new network using the following command and use it.
```shell
docker network create sunbird_db_network
```
iii. Run Cassandra
```shell
docker run -p 9042:9042 --name sunbird_cassandra -v $sunbird_dbs_path/cassandra/data:/var/lib/cassandra -v $sunbird_dbs_path/cassandra/logs:/opt/cassandra/logs -v $sunbird_dbs_path/cassandra/backups:/mnt/backups --network sunbird_db_network -d cassandra:3.11.6
```
Fork the below projects and clone it from git,
```shell
git clone https://github.com/Sunbird-Lern/sunbird-utils/<latest-branch>
```
Open a new Terminal In the path,
#### (Project base path)/sunbird-utils
Run the below command,
```shell
mvn clean install -DskipTests
``` 
Make sure the build is success and then,
open a new Terminal In the path,
#### (Project base path)/sunbird-utils/sunbird-cassandra-migration/cassandra-migration,
Run below command,
```shell
mvn clean install -DskipTests
``` 
## One should execute only one of the commands listed below.
### Command 1:
```shell
java -jar \
-Dcassandra.migration.scripts.locations=filesystem:<absolute or relative path>/db/migration/cassandra \
-Dcassandra.migration.cluster.contactpoints=localhost \
-Dcassandra.migration.cluster.port=9042 \
-Dcassandra.migration.cluster.username=username \
-Dcassandra.migration.cluster.password=password \
-Dcassandra.migration.keyspace.name=keyspace_name \
target/*-jar-with-dependencies.jar migrate
``` 
### replace keyspace_name as sunbird_courses and sunbird
### Command 2:
```shell
java -cp "cassandra-migration-0.0.1-SNAPSHOT-jar-with-dependencies.jar" com.contrastsecurity.cassandra.migration.utils.MigrationScriptEntryPoint
```
The system environment listed below is required for command 2.
### System Env
```shell
sunbird_cassandra_keyspace=<keyspace_name>
sunbird_cassandra_migration_location="filesystem:<absolute or relative path>/db/migration/cassandra"
``` 

### Elasticsearch database setup in docker:
i. Get the ES image using below command:
```shell
docker pull elasticsearch:6.8.11
```
ii. Run ES
```shell
docker run -p 9200:9200 --name sunbird_es -v $sunbird_dbs_path/es/data:/usr/share/elasticsearch/data -v $sunbird_dbs_path/es/logs://usr/share/elasticsearch/logs -v $sunbird_dbs_path/es/backups:/opt/elasticsearch/backup -e "discovery.type=single-node" --network sunbird_db_network -d docker.elastic.co/elasticsearch/elasticsearch:6.8.11

chmod -R 777 sunbird-dbs/es
```
iii. ES Indices and Mappings
Get the index and mapping from these folders and create them using postman.
https://github.com/project-sunbird/sunbird-devops/tree/master/ansible/roles/es-mapping
PUT http://localhost:9200/<indices_name> Body : <indices_json_content>
PUT http://localhost:9200/<indices_name>/_mapping/_doc Body : <mapping_json_content>

### Redis setup in docker:
i. Get the Redis image using below command:
```shell
docker pull redis:4.0.0
```
ii. Run Redis
```shell
docker run --name sunbird_redis -d -p 6379:6379 redis:4.0.0
```
​
### Batch Service Repo setup:
Repository url : git clone https://github.com/<YOUR_FORK>/sunbird-course-service.git
Branch : <required_branch>

i. Checkout the <required_branch> branch code from github after forking the repository.

ii. Update the lms-service.sh file in the scripts folder with configuration values to setup environment variables and run it to export the values or add the variables to bashrc or bashprofile.
```shell
./scripts/lms-service.sh
```
iii. Build using maven from sunbird-course-service folder
```shell
mvn clean install -DskipTests
```
iii. Run the service from sunbird-course-service/service folder
```shell
mvn play2:run
```
iv. Use the below curl to check the service health
```shell
curl --location --request GET 'http://localhost:9000/health'
```

The code in this repository is licensed under MIT License unless otherwise noted. Please see the [LICENSE](https://github.com/project-sunbird/sunbird-lms-service/blob/master/LICENSE) file for details.