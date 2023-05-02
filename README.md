# sunbird-lms-service

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/b963e5ed122f47b5a27b19a87d9fa6de)](https://app.codacy.com/app/sunbird-bot/sunbird-lms-service?utm_source=github.com&utm_medium=referral&utm_content=project-sunbird/sunbird-lms-service&utm_campaign=Badge_Grade_Settings)

This is the repository for Sunbird learning management system (lms) micro-service. It provides the APIs for lms functionality of Sunbird.

The code in this repository is licensed under MIT License unless otherwise noted. Please see the [LICENSE](https://github.com/project-sunbird/sunbird-lms-service/blob/master/LICENSE) file for details.
This readme file describes how to install and start User&Org Service and set up the default organisation & user creation
in local machine.
## sunbird-course-Service local setup
This readme file contains the instruction to set up and run the sunbird-course-service in local machine.

### System Requirements:

### Prerequisites:

* Java 11

### Prepare folders for database data and logs

```shell
mkdir -p ~/sunbird-dbs/cassandra ~/sunbird-dbs/es 
export sunbird_dbs_path=~/sunbird-dbs
```

### cassandra database setup in docker:

1. we need to get the cassandra image and can be done using the below command.

```shell
docker pull cassandra:3.11.6 
```

For network, we can use the existing network or create a new network using the following command and use it.

```shell
docker network create sunbird_db_network
```

2. We need to create the cassandra instance, By using the below command we can create the same and run in a container.

```shell
docker run -p 9042:9042 --name sunbird_cassandra \
 -v $sunbird_dbs_path/cassandra/data:/var/lib/cassandra \
 -v $sunbird_dbs_path/cassandra/logs:/opt/cassandra/logs \
 -v $sunbird_dbs_path/cassandra/backups:/mnt/backups \
 --network sunbird_db_network -d cassandra:3.11.6 
```

3. We can verify the setup by running the below command, which will show the status of cassandra as up and running

```shell
docker ps -a | grep cassandra
```

## To create/load data to Cassandra
[sunbird-utils-cassandra-setup](https://github.com/Sunbird-Lern/sunbird-utils/tree/release-5.3.0#readme)

4. To ssh to cassandra docker container and check whether the tables got created,
   run the below command.
```shell
docker exec -it sunbird_cassandra /bin/bash
```

### The system environment listed below is required for cassandra connectivity with user org service.

#### System Env variables for cassandra

```shell
sunbird_cassandra_host=localhost
sunbird_cassandra_password=<your_cassandra_password>
sunbird_cassandra_port=<your_cassandra_port>
sunbird_cassandra_username=<your_cassandra_username>
```

### elastic search setup in docker:

1. we need to get the elastic search image and can be done using the below command.

```shell
docker pull elasticsearch:6.8.11
```

2. We need to create the elastic search instance, By using the below command we can create the same and run in a
   container.

```shell
docker run -p 9200:9200 --name sunbird_es \
 -v $sunbird_dbs_path/es/data:/usr/share/elasticsearch/data \
 -v $sunbird_dbs_path/es/logs://usr/share/elasticsearch/logs \
 -v $sunbird_dbs_path/es/backups:/opt/elasticsearch/backup \
 -e "discovery.type=single-node" --network sunbird_db_network \
 -d docker.elastic.co/elasticsearch/elasticsearch:6.8.11
```

> --name -  Name your container (avoids generic id)
>
> -p - Specify container ports to expose
>
> Using the -p option with ports 7474 and 7687 allows us to expose and listen for traffic on both the HTTP and Bolt ports. Having the HTTP port means we can connect to our database with Neo4j Browser, and the Bolt port means efficient and type-safe communication requests between other layers and the database.
>
> -d - This detaches the container to run in the background, meaning we can access the container separately and see into all of its processes.
>
> -v - The next several lines start with the -v option. These lines define volumes we want to bind in our local directory structure so we can access certain files locally.

3. We can verify the setup by running the below command, which will show the status of elastic search as up and running

```shell
docker ps -a | grep es
```

4. This step is required only if you use ubuntu system. Make sure you create necessary permissions for the folder by
   executing the below command,

```shell
chmod -R 777 sunbird-dbs/es
```

### elastic search Indices and mappings setup

1. clone the latest branch of sunbird-devops using
   below command,

```shell
git clone https://github.com/project-sunbird/sunbird-devops/<latest-branch>
```

2. then navigate to,
   <project_base_path>/sunbird-devops/blob/master/ansible/roles/es-mapping/files, for getting the index and mappings.
   We have to use postman to create index and mappings.

Create indices for,
1. [course-batch](https://github.com/project-sunbird/sunbird-devops/blob/master/ansible/roles/es-mapping/files/indices/course-batch.json)

#### PUT {{es_host}}/<indices_name> Body : <respective_index_json_content>

For example,

```shell
curl --location --globoff --request PUT 'localhost:9200/location' \
--header 'Content-Type: application/json' \
--data '{
    "settings": {
        "index": {
            "number_of_shards": "5",
            "number_of_replicas": "1",
            "analysis": {
                "filter": {
                    "mynGram": {
                        "token_chars": [
                            "letter",
                            "digit",
                            "whitespace",
                            "punctuation",
                            "symbol"
                        ],
                        "min_gram": "1",
                        "type": "ngram",
                        "max_gram": "20"
                    }
                },
                "analyzer": {
                    "cs_index_analyzer": {
                        "filter": [
                            "lowercase",
                            "mynGram"
                        ],
                        "type": "custom",
                        "tokenizer": "standard"
                    },
                    "keylower": {
                        "filter": "lowercase",
                        "type": "custom",
                        "tokenizer": "keyword"
                    },
                    "cs_search_analyzer": {
                        "filter": [
                            "lowercase",
                            "standard"
                        ],
                        "type": "custom",
                        "tokenizer": "standard"
                    }
                }
            }
        }
    }
}'
```

replace <respective_index_name> with
##### course-batch
one by one along with copying
<respective_index_json_content> provided in previous step in the body.

Create mappings for,
1. [course-batch](https://github.com/project-sunbird/sunbird-devops/blob/master/ansible/roles/es-mapping/files/mappings/course-batch-mapping.json)

#### PUT {{es_host}}/<indices_name>/_mapping/_doc Body : <respective_mapping_json_content>

For example,

```shell
curl --location --request PUT 'localhost:9200/location/_mapping/_doc' \
--header 'Content-Type: application/json' \
--data '{
    "dynamic": false,
    "properties": {
        "all_fields": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower"
                }
            },
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer"
        },
        "batchId": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "courseId": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "createdBy": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "createdDate": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "createdFor": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "description": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "endDate": {
            "type": "date",
            "fields": {
                "raw": {
                    "type": "date"
                }
            }
        },
         "enrollmentEndDate": {
           "type": "date",
           "fields": {
              "raw":  {
                  "type": "date"
               }
           }
        },
        "enrollmentType": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "id": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "identifier": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "mentors": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "name": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "startDate": {
            "type": "date",
            "fields": {
                "raw": {
                    "type": "date"
                }
            }
        },
        "status": {
            "type": "long",
            "fields": {
                "raw": {
                    "type": "long"
                }
            }
        },
        "updatedDate": {
            "type": "text",
            "fields": {
                "raw": {
                    "type": "text",
                    "analyzer": "keylower",
                    "fielddata": true
                }
            },
            "copy_to": [
                "all_fields"
            ],
            "analyzer": "cs_index_analyzer",
            "search_analyzer": "cs_search_analyzer",
            "fielddata": true
        },
        "participantCount": {
            "type": "long",
            "fields": {
                "raw": {
                    "type": "long"
                }
            }
        },
        "completedCount": {
            "type": "long",
            "fields": {
                "raw": {
                    "type": "long"
                }
            }
        },
        "reportUpdatedOn": {
            "type": "date",
            "fields": {
                "raw": {
                    "type": "date"
                }
            }
        },
        "cert_templates": {
            "type": "nested"
        }
    }
}'
```

replace <respective_index_name> with
##### course-batch
one by one along with copying
<respective_mapping_json_content> provided in previous step in the body.

### The system environment listed below is required for elastic search connectivity with user org service.

#### System Env variables for elastic search

```shell
sunbird_es_host=localhost
sunbird_es_port=<your es port>
sunbird_es_cluster=<your cluster name>
```

### Redis database setup in docker:
1. we need to get the redis image from docker hub using the below command.
```shell
docker pull redis:4.0.0 
```
2. We need to create the redis instance, By using the below command we can create the same and run in a container.
```shell
docker run --name sunbird_redis -d -p 6379:6379 redis:4.0.0
```
3. To SSH to redis docker container, run the below command
```shell
docker exec -it sunbird_redis bash
```

## Batch Service Setup

1. Clone the latest branch of the batch service using the below command,

```shell
git clone https://github.com/<YOUR_FORK>/sunbird-course-service.git
```

2. Go to the path: <project-base-path>/sunbird-course-service and run the below maven command to build the application.

```shell
mvn clean install -DskipTests
```

Please ensure the build is success before firing the below command, if the build is not success then the project might
not be imported properly and there is some configuration issues, fix the same and rebuild until it is successful.

3. Go to the path: <project-base-path>/sunbird-course-service/service and run the below maven command to run the netty
   server.

```shell
mvn play2:run
```

4.Using the below command we can verify whether the databases(cassandra,elastic search,redis) connection is established or
not. If all connections are good, health is shown as 'true' otherwise it will be 'false'.

```shell
curl --location --request GET 'http://localhost:9000/health’
```

Currently the lms service is dependent on content read API for batch creation and User org service for getting user information
and organisation information.We are planning to implement a mock service soon for this dependencies.