package org.sunbird.learner.util;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.sunbird.common.models.util.LoggerUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ContentSearchMock {

    private static final Map<String, MockResponse> RESPONSES = new HashMap<>();
    private static LoggerUtil logger = new LoggerUtil(ContentSearchMock.class);
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String HEADER = "application/json";
    private static final Dispatcher DISPATCHER = new Dispatcher() {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            String path = request.getPath();
            return RESPONSES.getOrDefault(path, new MockResponse().setResponseCode(404));
        }
    };
    private static MockWebServer server;

    private ContentSearchMock() {
    }

    public static void setup() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(DISPATCHER);
        server.start(9080);

        RESPONSES.put("/content/v3/read/" + "do_2137525270494330881314?fields=status,batches,leafNodesCount",
                new MockResponse()
                        .setHeader(CONTENT_TYPE, HEADER)
                        .setResponseCode(200)
                        .setBody("{\"id\":\"api.content.read\",\"ver\":\"1.0\",\"ts\":\"2023-04-28T11:13:36.611Z\",\"params\":{\"resmsgid\":\"b887eb30-e5b5-11ed-9d5b-d30bfb2743aa\",\"msgid\":\"b8852c10-e5b5-11ed-b223-294888a1bb2f\",\"status\":\"successful\",\"err\":null,\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"content\":{\"ownershipType\":[\"createdBy\"],\"copyright\":\"tn\",\"se_gradeLevelIds\":[\"tn_k-12_5_gradelevel_class10\",\"tn_k-12_5_gradelevel_class11\",\"tn_k-12_5_gradelevel_class12\",\"tn_k-12_5_gradelevel_class1\",\"tn_k-12_5_gradelevel_class2\",\"tn_k-12_5_gradelevel_class3\",\"tn_k-12_5_gradelevel_class4\",\"tn_k-12_5_gradelevel_class5\",\"tn_k-12_5_gradelevel_class6\",\"tn_k-12_5_gradelevel_class7\",\"tn_k-12_5_gradelevel_class8\",\"tn_k-12_5_gradelevel_class9\"],\"keywords\":[\"test\"],\"subject\":[\"Basic Civil Engineering - Practical\"],\"targetMediumIds\":[\"tn_k-12_5_medium_english\",\"tn_k-12_5_medium_tamil\"],\"channel\":\"01269878797503692810\",\"downloadUrl\":\"https://sunbirddevbbpublic.blob.core.windows.net/sunbird-content-dev/content/do_213754130660818944126/l1-course_1678971250268_do_213754130660818944126_1_SPINE.ecar\",\"organisation\":[\"Tamil Nadu\"],\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://sunbirddevbbpublic.blob.core.windows.net/sunbird-content-dev/content/do_213754130660818944126/l1-course_1678971250268_do_213754130660818944126_1_SPINE.ecar\",\"size\":\"19243\"},\"online\":{\"ecarUrl\":\"https://sunbirddevbbpublic.blob.core.windows.net/sunbird-content-dev/content/do_213754130660818944126/l1-course_1678971250557_do_213754130660818944126_1_ONLINE.ecar\",\"size\":\"7427\"}},\"leafNodes\":[\"do_2135323699826606081233\",\"do_21337188080177971211\"],\"targetGradeLevelIds\":[\"tn_k-12_5_gradelevel_class10\",\"tn_k-12_5_gradelevel_class11\",\"tn_k-12_5_gradelevel_class12\",\"tn_k-12_5_gradelevel_class1\",\"tn_k-12_5_gradelevel_class2\",\"tn_k-12_5_gradelevel_class3\",\"tn_k-12_5_gradelevel_class4\",\"tn_k-12_5_gradelevel_class5\",\"tn_k-12_5_gradelevel_class6\",\"tn_k-12_5_gradelevel_class7\",\"tn_k-12_5_gradelevel_class8\",\"tn_k-12_5_gradelevel_class9\"],\"objectType\":\"Content\",\"se_mediums\":[\"English\",\"Tamil\"],\"primaryCategory\":\"Course\",\"appId\":\"staging.sunbird.portal\",\"contentEncoding\":\"gzip\",\"lockKey\":\"5f98d8fe-872d-4708-a55a-92e05190c8c8\",\"generateDIALCodes\":\"No\",\"totalCompressedSize\":1498116,\"mimeTypesCount\":\"{\\\"application/pdf\\\":1,\\\"application/vnd.ekstep.ecml-archive\\\":1,\\\"application/vnd.ekstep.content-collection\\\":1}\",\"sYS_INTERNAL_LAST_UPDATED_ON\":\"2023-03-16T12:54:10.266+0000\",\"contentType\":\"Course\",\"se_gradeLevels\":[\"Class 10\",\"Class 11\",\"Class 12\",\"Class 1\",\"Class 2\",\"Class 3\",\"Class 4\",\"Class 5\",\"Class 6\",\"Class 7\",\"Class 8\",\"Class 9\"],\"trackable\":{\"enabled\":\"Yes\",\"autoBatch\":\"No\"},\"identifier\":\"do_213754130660818944126\",\"audience\":[\"Student\"],\"se_boardIds\":[\"tn_k-12_5_board_statetamilnadu\"],\"subjectIds\":[\"tn_k-12_5_subject_basiccivilengineeringpractical\"],\"toc_url\":\"https://sunbirddevbbpublic.blob.core.windows.net/sunbird-content-dev/content/do_213754130660818944126/artifact/do_213754130660818944126_toc.json\",\"visibility\":\"Default\",\"contentTypesCount\":\"{\\\"SelfAssess\\\":1,\\\"Resource\\\":1,\\\"CourseUnit\\\":1}\",\"author\":\"newtncc\",\"consumerId\":\"c24a9706-93e0-47e8-a39e-862e71b9b026\",\"childNodes\":[\"do_2135323699826606081233\",\"do_213754131124731904131\",\"do_21337188080177971211\"],\"discussionForum\":{\"enabled\":\"Yes\"},\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"languageCode\":[\"en\"],\"lastPublishedBy\":\"91a81041-bbbd-4bd7-947f-09f9e469213c\",\"version\":2,\"se_subjects\":[\"Basic Civil Engineering - Practical\"],\"license\":\"CC BY 4.0\",\"prevState\":\"Review\",\"size\":19243,\"lastPublishedOn\":\"2023-03-16T12:54:10.194+0000\",\"name\":\"L1 course\",\"targetBoardIds\":[\"tn_k-12_5_board_statetamilnadu\"],\"status\":\"Live\",\"code\":\"org.sunbird.lFrel6\",\"credentials\":{\"enabled\":\"Yes\"},\"prevStatus\":\"Processing\",\"description\":\"Enter description for Course\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2023-03-16T12:50:27.939+0000\",\"reservedDialcodes\":{\"F5D3D2\":0},\"batches\":[{\"createdFor\":[\"01269878797503692810\"],\"endDate\":null,\"name\":\"123\",\"batchId\":\"0137541355703173128\",\"enrollmentType\":\"open\",\"enrollmentEndDate\":null,\"startDate\":\"2023-03-16\",\"status\":1}],\"se_boards\":[\"State (Tamil Nadu)\"],\"targetSubjectIds\":[\"tn_k-12_5_subject_accountancy\"],\"se_mediumIds\":[\"tn_k-12_5_medium_english\",\"tn_k-12_5_medium_tamil\"],\"copyrightYear\":2023,\"contentDisposition\":\"inline\",\"additionalCategories\":[\"Lesson Plan\",\"Textbook\"],\"lastUpdatedOn\":\"2023-03-16T12:54:10.666+0000\",\"dialcodeRequired\":\"No\",\"lastStatusChangedOn\":\"2023-03-16T12:54:10.666+0000\",\"createdFor\":[\"01269878797503692810\"],\"creator\":\"newtncc\",\"os\":[\"All\"],\"se_subjectIds\":[\"tn_k-12_5_subject_basiccivilengineeringpractical\",\"tn_k-12_5_subject_accountancy\"],\"se_FWIds\":[\"tn_k-12_5\"],\"targetFWIds\":[\"tn_k-12_5\"],\"pkgVersion\":1,\"versionKey\":\"1678971216414\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"tn_k-12_5\",\"dialcodes\":[\"F5D3D2\"],\"depth\":0,\"s3Key\":\"content/do_213754130660818944126/artifact/do_213754130660818944126_toc.json\",\"lastSubmittedOn\":\"2023-03-16T12:53:35.402+0000\",\"createdBy\":\"deec6352-0f62-4306-9818-aba349a0e0f8\",\"compatibilityLevel\":4,\"leafNodesCount\":2,\"userConsent\":\"Yes\",\"resourceType\":\"Course\"}}}"));
        RESPONSES.put("/content/v3/search",
                new MockResponse()
                        .setHeader(CONTENT_TYPE, HEADER)
                        .setResponseCode(200)
                        .setBody("{\"id\":\"api.content.search\",\"ver\":\"1.0\",\"ts\":\"2023-02-16T07:19:30.405Z\",\"params\":{\"resmsgid\":\"4102b950-adca-11ed-90b2-b7bf811d5c69\",\"msgid\":\"40fbdb80-adca-11ed-a723-4b0cc0e91be5\",\"status\":\"successful\",\"err\":null,\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"count\":712392,\"content\":[{\"ownershipType\":[\"createdBy\"],\"publish_type\":\"public\",\"unitIdentifiers\":[\"do_21337599437489766414999\"],\"copyright\":\"Test axis,2126\",\"se_gradeLevelIds\":[\"ekstep_ncert_k-12_gradelevel_class10\"],\"previewUrl\":\"https://obj.stage.sunbirded.org/sunbird-content-staging/content/do_21337600715072307215013/artifact/do_21337600715072307215013_1632814051137_do_21337600715072307215013_1632813407308_pdf_229.pdf\",\"organisationId\":\"c5b2b2b9-fafa-4909-8d5d-f9458d1a3881\",\"keywords\":[\"All_Contents\"],\"subject\":[\"Hindi\"],\"downloadUrl\":\"https://obj.stage.sunbirded.org/sunbird-content-staging/content/do_21337600715072307215013/learning-resource_1671091450835_do_21337600715072307215013_2.ecar\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"language\":[\"English\"],\"variants\":{\"full\":{\"ecarUrl\":\"https://obj.stage.sunbirded.org/sunbird-content-staging/content/do_21337600715072307215013/learning-resource_1671091450835_do_21337600715072307215013_2.ecar\",\"size\":\"262229\"},\"spine\":{\"ecarUrl\":\"https://obj.stage.sunbirded.org/sunbird-content-staging/content/do_21337600715072307215013/learning-resource_1671091451013_do_21337600715072307215013_2_SPINE.ecar\",\"size\":\"7647\"}},\"source\":\"https://dockstaging.sunbirded.org/api/content/v1/read/do_21337600715072307215013\",\"mimeType\":\"application/pdf\",\"objectType\":\"Content\",\"se_mediums\":[\"English\"],\"appIcon\":\"https://stagingdock.blob.core.windows.net/sunbird-content-dock/content/do_21337600715072307215013/artifact/apple-fruit.thumb.jpg\",\"gradeLevel\":[\"Class 10\"],\"primaryCategory\":\"Learning Resource\",\"appId\":\"staging.dock.portal\",\"artifactUrl\":\"https://obj.stage.sunbirded.org/sunbird-content-staging/content/do_21337600715072307215013/artifact/do_21337600715072307215013_1632814051137_do_21337600715072307215013_1632813407308_pdf_229.pdf\",\"contentEncoding\":\"identity\",\"contentType\":\"PreviousBoardExamPapers\",\"se_gradeLevels\":[\"Class 10\"],\"trackable\":{\"enabled\":\"No\",\"autoBatch\":\"No\"},\"identifier\":\"do_213754130660818944126\",\"se_boardIds\":[\"ekstep_ncert_k-12_board_cbse\"],\"subjectIds\":[\"ekstep_ncert_k-12_subject_hindi\"],\"audience\":[\"Student\"],\"visibility\":\"Default\",\"consumerId\":\"cb069f8d-e4e1-46c5-831f-d4a83b323ada\",\"author\":\"paul1\",\"discussionForum\":{\"enabled\":\"No\"},\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"9c9b3259-f137-491a-bae5-fe2ad1763647\",\"languageCode\":[\"en\"],\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"version\":2,\"pragma\":[\"external\"],\"se_subjects\":[\"Hindi\"],\"prevState\":\"Review\",\"license\":\"CC BY 4.0\",\"lastPublishedOn\":\"2019-11-15 05:41:50:382+0000\",\"size\":270173,\"name\":\"24 aug course\",\"topic\":[\"कर चले हम फ़िदा\"],\"mediumIds\":[\"ekstep_ncert_k-12_medium_english\"],\"attributions\":[]}]}}"));
        RESPONSES.put("/system/v3/content/update/",
                new MockResponse()
                        .setHeader(CONTENT_TYPE, HEADER)
                        .setResponseCode(200)
                        .setBody("{\"id\":\"api.content.update\",\"ver\":\"4.0\",\"ts\":\"2020-12-10T20:26:07ZZ\",\"params\":{\"resmsgid\":\"80aa9310-b749-411c-a13b-8d9f25af389f\",\"msgid\":null,\"err\":null,\"status\":\"successful\",\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"identifier\":\"do_213754130660818944126\",\"node_id\":\"do_213754130660818944126\",\"versionKey\":\"1607631967842\"}}"));

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.shutdown();
            } catch (IOException e) {
                logger.info(null,"Error setting up ContentSearchMock:"+e);
            }
        }));
    }

}