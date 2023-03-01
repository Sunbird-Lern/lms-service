# sunbird-course-service
​
Repository for Batch Service
​
## sunbird-course-Service local setup
This readme file contains the instruction to set up and run the sunbird-course-service in local machine.
​
### Prerequisites:
Java 11
Maven - Latest
Cassandra 3.11.6
ES 6.8.11
​
### Create folders for database data and logs
​
```shell
mkdir -p ~/sunbird-dbs/cassandra ~/sunbird-dbs/es
export sunbird_dbs_path=~/sunbird-dbs
```
​
### Cassandra database setup in docker:
​
i. Copy the sunbird_courses.cql file from github (https://github.com/Sunbird-Lern/sunbird-course-service/blob/bootcamp/scripts/sunbird_courses.cql) to /sunbird-dbs/cassandra/backups folder.
​
ii. Get the cassandra image using below command
```shell
docker pull cassandra:3.11.6
```
iii. For the network, we can use the existing network or create a new network using the following command and use it.
```shell
docker network create sunbird_db_network
```
iii. Run Cassandra
```shell
docker run -p 9042:9042 --name sunbird_cassandra -v $sunbird_dbs_path/cassandra/data:/var/lib/cassandra -v $sunbird_dbs_path/cassandra/logs:/opt/cassandra/logs -v $sunbird_dbs_path/cassandra/backups:/mnt/backups --network sunbird_db_network -d cassandra:3.11.6
```
iv. To start the Cassandra cypher shell run the below command.
```shell
docker exec -it sunbird_cassandra cqlsh
```
v. Load database schema by executing below command in cypher shell.
```shell
source '/mnt/backups/sunbird_courses.cql'
```
### Elasticsearch database setup in docker:
i. Get the ES image using below command:
```shell
docker pull elasticsearch:6.8.11
```
ii. Run ES
```shell
docker run -p 9200:9200 --name sunbird_es -v $sunbird_dbs_path/es/data:/usr/share/elasticsearch/data -v $sunbird_dbs_path/es/logs://usr/share/elasticsearch/logs -v $sunbird_dbs_path/es/backups:/opt/elasticsearch/backup -e "discovery.type=single-node" --network sunbird_db_network -d docker.elastic.co/elasticsearch/elasticsearch:6.8.11
```
iii. ES Indices and Mappings
Create course_batch index and mapping using the /indices/course-batch.json and /mappings/course-batch-mapping.json in below link
https://github.com/project-sunbird/sunbird-devops/tree/master/ansible/roles/es-mapping/files
PUT http://localhost:9200/<indices_name> Body : <indices_json_content>
PUT http://localhost:9200/<indices_name>/_mapping/_doc Body : <mapping_json_content>
​
### Batch Service Repo setup:
Repository url : https://github.com/Sunbird-Lern/sunbird-course-service
Branch : bootcamp
​
i. Checkout the bootcamp branch code from github after forking the repository.
​
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
​
The code in this repository is licensed under MIT License unless otherwise noted. Please see the [LICENSE](https://github.com/project-sunbird/sunbird-lms-service/blob/master/LICENSE) file for details.