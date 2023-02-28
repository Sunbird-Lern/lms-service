CREATE KEYSPACE IF NOT EXISTS sunbird_courses WITH replication = {'class':'SimpleStrategy','replication_factor':1};

CREATE TABLE IF NOT EXISTS sunbird_courses.course_batch (courseid text, batchid text, createdby text, createddate text, createdfor list<text>, description text, enddate text, enrollmentenddate text, enrollmenttype text, mentors list<text>, name text, startdate text, status int, updateddate text, PRIMARY KEY (courseid, batchid));
CREATE INDEX inx_cou_bat_status ON sunbird_courses.course_batch (status);

CREATE TABLE IF NOT EXISTS sunbird_courses.user_courses (batchid text, userid text, active boolean, addedby text, completedon timestamp, completionpercentage int, contentstatus map<text, int>, courseid text, datetime timestamp, delta text, enrolleddate text, grade text, lastreadcontentid text, lastreadcontentstatus int, progress int, status int, PRIMARY KEY (batchid, userid));
CREATE INDEX inx_ucs_status ON sunbird_courses.user_courses (status);

CREATE TABLE IF NOT EXISTS sunbird_courses.content_consumption (userid text, contentid text, batchid text, courseid text, completedcount int, contentversion text, datetime timestamp, grade text, lastaccesstime text, lastcompletedtime text, lastupdatedtime text, progress int, result text, score text, status int, viewcount int, PRIMARY KEY (userid, contentid, batchid, courseid));
CREATE INDEX inx_cc_status ON sunbird_courses.content_consumption (status);

CREATE TABLE IF NOT EXISTS sunbird_courses.bulk_upload_process(id text, status int, data text, successResult text, failureResult text, uploadedBy text, uploadedDate text, processStartTime text, processEndTime text, ObjectType text, PRIMARY KEY (id));
CREATE INDEX inx_bup_status ON sunbird_courses.bulk_upload_process(status);

ALTER TABLE sunbird_courses.user_courses ADD certificates list<frozen <map<text,text>>>;

CREATE TYPE IF NOT EXISTS sunbird_courses.question (id text,max_score int,score int,type text,title text,resvalues list<frozen<map<text, text>>>,params list<frozen<map<text, text>>>,description text,duration decimal);
CREATE TABLE IF NOT EXISTS sunbird_courses.assessment_aggregator (course_id text,batch_id text,content_id text,attempt_id text,user_id text,created_on timestamp,last_attempted_on timestamp,total_max_score int,question list<frozen<question>>,total_score int,updated_on timestamp,PRIMARY KEY (course_id, batch_id, content_id, attempt_id, user_id));
CREATE INDEX assessment_aggregator_last_attempted_on_idx ON sunbird_courses.assessment_aggregator (last_attempted_on);

DROP TABLE IF EXISTS sunbird_courses.assessment_aggregator;
DROP TYPE IF EXISTS sunbird_courses.question;
CREATE TYPE IF NOT EXISTS sunbird_courses.question (id text,assess_ts timestamp,max_score double,score double,type text,title text,resvalues list<frozen<map<text, text>>>,params list<frozen<map<text, text>>>,description text,duration decimal);
CREATE TABLE IF NOT EXISTS sunbird_courses.assessment_aggregator (course_id text,batch_id text,content_id text,attempt_id text,user_id text,created_on timestamp,last_attempted_on timestamp,total_max_score double,grand_total text,question list<frozen<question>>,total_score double,updated_on timestamp,PRIMARY KEY (course_id, batch_id, content_id, attempt_id, user_id));
CREATE INDEX assessment_aggregator_last_attempted_on_idx ON sunbird_courses.assessment_aggregator (last_attempted_on);

DROP TABLE IF EXISTS sunbird_courses.assessment_aggregator;
DROP TYPE IF EXISTS sunbird_courses.question;
CREATE TYPE IF NOT EXISTS sunbird_courses.question (id text,assess_ts timestamp,max_score double,score double,type text,title text,resvalues list<frozen<map<text, text>>>,params list<frozen<map<text, text>>>,description text,duration decimal);
CREATE TABLE IF NOT EXISTS sunbird_courses.assessment_aggregator (course_id text,batch_id text,content_id text,attempt_id text,user_id text,created_on timestamp,last_attempted_on timestamp,total_max_score double,grand_total text,question list<frozen<question>>,total_score double,updated_on timestamp,PRIMARY KEY (course_id, batch_id, user_id, content_id, attempt_id));
CREATE INDEX assessment_aggregator_last_attempted_on_idx ON sunbird_courses.assessment_aggregator (last_attempted_on);

ALTER TABLE sunbird_courses.course_batch ADD cert_templates map<text,frozen<map<text,text>>>;

CREATE TABLE IF NOT EXISTS sunbird_courses.user_enrolments (
    userid text,
    courseid text,
    batchid text,
    active boolean,
    addedby text,
    certificates list<frozen<map<text, text>>>,
    completedon timestamp,
    completionpercentage int,
    contentstatus map<text, int>,
    datetime timestamp,
    enrolleddate text,
    lastreadcontentid text,
    lastreadcontentstatus int,
    progress int,
    status int,
    PRIMARY KEY (userid, courseid, batchid)
) WITH CLUSTERING ORDER BY (courseid ASC, batchid ASC);
CREATE INDEX IF NOT EXISTS inx_ues_status ON sunbird_courses.user_enrolments (status);
CREATE INDEX IF NOT EXISTS inx_ues_certs ON sunbird_courses.user_enrolments (values(certificates));
CREATE TABLE IF NOT EXISTS sunbird_courses.user_content_consumption (
    userid text,
    courseid text,
    batchid text,
    contentid text,
    completedcount int,
    datetime timestamp,
    lastaccesstime text,
    lastcompletedtime text,
    lastupdatedtime text,
    progress int,
    status int,
    viewcount int,
    PRIMARY KEY (userid, courseid, batchid, contentid)
) WITH CLUSTERING ORDER BY (courseid ASC, batchid ASC, contentid ASC);
CREATE INDEX IF NOT EXISTS inx_ucc_status ON sunbird_courses.user_content_consumption (status);

CREATE TABLE IF NOT EXISTS sunbird_courses.user_activity_agg (
    activity_type text,
    activity_id text,
    user_id text,
    context_id text,
    agg map<text, int>,
    agg_last_updated map<text, timestamp>,
    PRIMARY KEY ((activity_type, activity_id, user_id), context_id)
) WITH CLUSTERING ORDER BY (context_id ASC);

ALTER TABLE sunbird_courses.user_enrolments ADD issued_certificates list<frozen <map<text,text>>>;

ALTER TABLE sunbird_courses.course_batch ADD tandc boolean;

ALTER TABLE sunbird_courses.user_enrolments ADD certstatus int;

DROP INDEX IF EXISTS sunbird_courses.assessment_aggregator_last_attempted_on_idx;
DROP INDEX IF EXISTS sunbird_courses.inx_ucc_status;
DROP INDEX IF EXISTS sunbird_courses.inx_ues_status;
DROP INDEX IF EXISTS sunbird_courses.inx_ues_certs;

DROP INDEX IF EXISTS sunbird_courses.inx_cou_bat_status;

ALTER TABLE sunbird_courses.course_batch ADD (created_date timestamp, start_date timestamp, end_date timestamp, enrollment_enddate timestamp, updated_date timestamp);
ALTER TABLE sunbird_courses.user_enrolments ADD (enrolled_date timestamp);
ALTER TABLE sunbird_courses.user_content_consumption ADD (last_access_time timestamp, last_completed_time timestamp, last_updated_time timestamp);

CREATE TABLE sunbird_courses.report_user_enrolments ( userid text, courseid text, batchid text, active boolean, addedby text, certificates list<frozen<map<text, text>>>, certstatus int, completedon timestamp, completionpercentage int, contentstatus map<text, int>, datetime timestamp, enrolled_date timestamp, enrolleddate text, issued_certificates list<frozen<map<text, text>>>, lastreadcontentid text, lastreadcontentstatus int, progress int, status int, PRIMARY KEY (userid, courseid, batchid));

ALTER TABLE sunbird_courses.user_activity_agg ADD aggregates map<text, double>;

ALTER TABLE sunbird_courses.user_activity_agg ADD agg_details list<text>; 

ALTER TABLE sunbird_courses.user_enrolments ADD lastcontentaccesstime timestamp;

ALTER TABLE sunbird_courses.user_content_consumption ADD completionpercentage float;
ALTER TABLE sunbird_courses.user_content_consumption ADD progressdetails text;
