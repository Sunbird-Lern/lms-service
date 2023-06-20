# sunbird-lms-service

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/b963e5ed122f47b5a27b19a87d9fa6de)](https://app.codacy.com/app/sunbird-bot/sunbird-lms-service?utm_source=github.com&utm_medium=referral&utm_content=project-sunbird/sunbird-lms-service&utm_campaign=Badge_Grade_Settings)

This is the repository for Sunbird learning management system (lms) micro-service. It provides the APIs for lms functionality of Sunbird.

The code in this repository is licensed under MIT License unless otherwise noted. Please see the [LICENSE](https://github.com/project-sunbird/sunbird-lms-service/blob/master/LICENSE) file for details.
This readme file describes how to install and start course-service in your developement environment.
## Sunbird-course-service developement environment setup:
This readme file contains the instruction to set up and run the sunbird-course-service in local machine.

### System Requirements

### Prerequisites

- Java 11
- Latest Docker
- Latest Maven 
  (Only for Mac M1 use 3.8.8 Maven version)

### Prepare folders for database data and logs

To prepare folders for database data and logs, run the following command:

```shell
mkdir -p ~/sunbird-dbs/cassandra ~/sunbird-dbs/es 
export sunbird_dbs_path=~/sunbird-dbs
```

To verify the creation of folders, run:

```shell
echo $sunbird_dbs_path
```

### Cassandra database setup in Docker

1. To get the Cassandra image, use the following command:

```shell
docker pull cassandra:3.11.6 
```
For Mac M1 users follow the bellow command:
```shell
docker pull --platform=linux/amd64 cassandra:3.11.6 
```

For the network, you can either use an existing network or create a new one by executing the following command:
```shell
docker network create sunbird_db_network
```

2. To create the Cassandra instance, run the following command:
```shell
docker run -p 9042:9042 --name sunbird_cassandra \
 -v $sunbird_dbs_path/cassandra/data:/var/lib/cassandra \
 -v $sunbird_dbs_path/cassandra/logs:/opt/cassandra/logs \
 -v $sunbird_dbs_path/cassandra/backups:/mnt/backups \
 --network sunbird_db_network -d cassandra:3.11.6 
```


For Mac M1 users follow the below command:
```shell
docker run --platform=linux/amd64 -p 9042:9042 --name sunbird_cassandra \
 -v $sunbird_dbs_path/cassandra/data:/var/lib/cassandra \
 -v $sunbird_dbs_path/cassandra/logs:/opt/cassandra/logs \
 -v $sunbird_dbs_path/cassandra/backups:/mnt/backups \
 --network sunbird_db_network -d cassandra:3.11.6 
```

3. To verify the setup, run the following command, which will show the status of Cassandra as up and running:

```shell
docker ps -a | grep cassandra
```

## To create/load keyspaces and tables to Cassandra

Click the link [sunbird-utils-cassandra-setup](https://github.com/Sunbird-Lern/sunbird-utils/tree/release-5.3.0#readme) 
and follow the steps for creating/loading the Cassandra keyspaces and tables to your development environment.

Note: It is mandatory to follow the instructions provided in the link.

4. To verify the creation of keyspaces and tables, connect to the Cassandra Docker container using SSH and run the following command:

```shell
docker exec -it sunbird_cassandra /bin/bash
```

## Setting up Elastic Search in Docker

To set up Elastic Search in Docker, follow the below steps:

1. Obtain the Elastic Search image by executing the following command:

```shell
docker pull elasticsearch:6.8.11
```

For Mac M1 users follow the bellow command:
```shell
docker pull --platform=linux/amd64 elasticsearch:6.8.11
```

2. Create an Elastic Search instance by executing the following command to run it in a container:
```shell
docker run -p 9200:9200 --name sunbird_es -v 
$sunbird_dbs_path/es/data:/usr/share/elasticsearch/data -v 
$sunbird_dbs_path/es/logs://usr/share/elasticsearch/logs -v 
$sunbird_dbs_path/es/backups:/opt/elasticsearch/backup 
-e "discovery.type=single-node" --network sunbird_db_network 
-d docker.elastic.co/elasticsearch/elasticsearch:6.8.11
```
For Mac M1 users follow the bellow command::
```shell
docker run --platform=linux/amd64 -p 9200:9200 --name sunbird_es -v 
$sunbird_dbs_path/es/data:/usr/share/elasticsearch/data -v 
$sunbird_dbs_path/es/logs://usr/share/elasticsearch/logs -v 
$sunbird_dbs_path/es/backups:/opt/elasticsearch/backup 
-e "discovery.type=single-node" --network sunbird_db_network 
-d docker.elastic.co/elasticsearch/elasticsearch:6.8.11
```

The above command performs the following actions:
- "-p 9200:9200" maps the host's port 9200 to the container's port 9200, allowing access to the Elasticsearch API.
- "--name <container_name>" assigns a name to the container, which can be used to reference it in other Docker commands.
- "-v <host_directory_path>/es/data:/usr/share/elasticsearch/data" mounts the host's directory "<host_directory_path>/es/data" as the Elasticsearch data directory inside the container.
- "-v <host_directory_path>/es/logs://usr/share/elasticsearch/logs" mounts the host's directory "<host_directory_path>/es/logs" as the Elasticsearch logs directory inside the container.
- "-v <host_directory_path>/es/backups:/opt/elasticsearch/backup" mounts the host's directory "<host_directory_path>/es/backups" as the Elasticsearch backups directory inside the container.
- "-e "discovery.type=single-node"" sets an environment variable "discovery.type" with the value "single-node", which tells Elasticsearch to start as a single-node cluster.
- "--network <network_name>" assigns the container to a Docker network, which is used to connect the container to other containers in the same network.
- "-d" runs the container in detached mode, which allows it to run in the background.

To verify the setup, execute the following command. It will display the elastic search status as up and running.
```shell
docker ps -a | grep es
```

If you are using an Ubuntu system, perform the following step to ensure that the necessary permissions are created for the folder:
```shell
chmod -R 777 sunbird-dbs/es
```
### elastic search Indices and mappings setup

Create indices for,
To create indices, follow these steps:

1. Copy the JSON content of the index from the provided link below for each index.
2. Replace `<indices_name>` with the name of the index for which you want to create the mapping.
3. Replace `<respective_index_json_content>` with the JSON content you copied in step 1.

Use the following api to create each index:

```
PUT {{es_host}}/<indices_name>
Body : <respective_index_json_content>
```

Here's an example curl command for creating the `course-batch` index:

```
curl --location --request PUT 'localhost:9200/course-batch' \
--header 'Content-Type: application/json' \
--data '<course-batch_json_content>'
```

Make sure to replace `course-batch_json_content` with the name of the index JSON file for the corresponding index.

Here's the list of indices to create and their corresponding links:
- [Course-batch](https://github.com/project-sunbird/sunbird-devops/blob/release-5.3.0-lern/ansible/roles/es-mapping/files/indices/course-batch.json)

To create mappings for the listed indices, follow these steps:

1. Copy the JSON content of the mapping from the provided link for each index.
2. Replace `<indices_name>` with the name of the index for which you want to create the mapping.
3. Replace `<respective_mapping_json_content>` with the JSON content you copied in step 1.

Use the following api to create each mapping:

```
PUT {{es_host}}/<indices_name>/_mapping/_doc 
Body: <respective_mapping_json_content>
```

Here's an example curl command for creating the mapping for the `course-batch` index:

```
curl --location --request PUT 'localhost:9200/course-batch/_mapping/_doc' \
--header 'Content-Type: application/json' \
--data '<course-batch_mapping_json_content>'
```

Make sure to replace `<course-batch_mapping_json_content>` with the name of the mapping JSON file for the corresponding index.

Here's the list of mappings to create and their corresponding links:

- [Course-batch](https://github.com/project-sunbird/sunbird-devops/blob/release-5.3.0-lern/ansible/roles/es-mapping/files/mappings/course-batch-mapping.json)

## Setting up Redis in Docker:

To set up Redis database in Docker, please follow the below steps:

1. Pull the Redis image from Docker Hub using the following command:
```shell
docker pull redis:4.0.0 
```
2. Create a Redis instance by running it in a container with the following command:
```shell
docker run --name sunbird_redis -d -p 6379:6379 redis:4.0.0
```
3. To verify the Redis setup, run the following command to SSH into the Redis Docker container:
```shell
docker exec -it sunbird_redis bash
``` 

To set up Redis database in Docker for Mac M1, please follow the below steps:

1. Pull the Redis image from Docker Hub using the following command:
```shell
docker pull --platform=linux/amd64 redis:5.0.7
```
2. Create a Redis instance by running it in a container with the following command:
```shell
docker run --platform=linux/amd64 --name sunbird_redis -d -p 6379:6379 redis:5.0.7
```
3. To verify the Redis setup, run the following command to SSH into the Redis Docker container:
```shell
docker exec -it sunbird_redis bash
``` 
## Sunbird-course-service Setup

To set up the batch service, follow the steps below:

1. Clone the latest branch of the course service using the following command:
```shell
git clone https://github.com/Sunbird-Lern/sunbird-course-service.git
```

2. Set up the necessary environment variables by running the following script in the path `<project-base-path>/sunbird-course-service`:
```shell
./scripts/lms-config.sh
```

3. Build the application using the following maven command in the path `<project-base-path>/sunbird-course-service`:
```shell
mvn clean install -DskipTests
```
Make sure the build is successful before proceeding to the next step. If the build is not successful,
fix any configuration issues and rebuild the application.

4. Run the netty server using the following maven command in the path `<project-base-path>/sunbird-course-service/service`:
```shell
mvn play2:run
```

5. Verify the database connections by running the following command:
```shell
curl --location --request GET 'http://localhost:9000/health’
```
If all connections are established successfully, the health status will be shown as 'true', otherwise it will be 'false'.

Currently, the lms service is dependent on content read API for batch creation and User org service for getting user and organisation information. 
We are planning to implement a mock service soon for these dependencies.
