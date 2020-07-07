package org.sunbird.learner.actors.coursemanagement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.MapUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sunbird.common.request.Request;
import org.sunbird.learner.actors.course.HierarchyGenerationHelper;

import java.util.Map;


public class HierarchyGenerationHelperTest {

    private static ObjectMapper mapper = null;
    private static HierarchyGenerationHelper helper = null;

    @BeforeClass
    public static void setUp() {
        mapper = new ObjectMapper();
        helper = new HierarchyGenerationHelper();
    }

    @Test
    public void testHierarchyGenerateSuccess() throws Exception {
        String requestString = "{\n" +
                "        \"course\": {\n" +
                "            \"name\": \"Test_CurriculumCourse With 3 Units\",\n" +
                "            \"code\": \"test-code\",\n" +
                "            \"description\": \"abc\",\n" +
                "            \"mimeType\": \"application/vnd.ekstep.content-collection\",\n" +
                "            \"contentType\": \"Course\"\n" +
                "        },\n" +
                "        \"hierarchy\": [\n" +
                "            {\n" +
                "                \"mimeType\": \"application/vnd.ekstep.content-collection\",\n" +
                "                \"code\": \"tbu\",\n" +
                "                \"contentType\": \"TextBookUnit\",\n" +
                "                \"identifier\": \"do_11304065956451123217\",\n" +
                "                \"name\": \"UNIT-2\",\n" +
                "                \"visibility\": \"Parent\",\n" +
                "                \"children\": [\n" +
                "                    {\n" +
                "                        \"mimeType\": \"application/pdf\",\n" +
                "                        \"contentType\": \"MarkingSchemeRubric\",\n" +
                "                        \"identifier\": \"do_11303997216713113613821\",\n" +
                "                        \"visibility\": \"Default\",\n" +
                "                        \"resourceType\": \"Read\",\n" +
                "                        \"name\": \"Resource 1\"\n" + "                    },\n" +
                "                    {\n" +
                "                        \"mimeType\": \"application/pdf\",\n" +
                "                        \"contentType\": \"LearningOutcomeDefinition\",\n" +
                "                        \"identifier\": \"do_11304337189392384011143\",\n" +
                "                        \"visibility\": \"Default\",\n" +
                "                        \"resourceType\": \"Read\",\n" +
                "                        \"name\": \"Resource 2\"\n" + "                    }\n" +
                "                ]\n" +
                "            }\n" +
                "        ]\n" +
                "}";
        Request request = new Request();
        request.getRequest().putAll(mapper.readValue(requestString, new TypeReference<Map<String, Object>>() {}));
        request.setOperation("createCourse");
        Map<String, Object> hierarchy = helper.generateUpdateHierarchyRequest(request, "do_12345");
        Assert.assertNotNull(hierarchy);
        Map<String, Object> nodesModified = (Map) ((Map<String, Object>) ((Map<String, Object>) hierarchy.get("request")).get("data")).get("nodesModified");
        Map<String, Object> hierarchyMap = (Map) ((Map<String, Object>) ((Map<String, Object>) hierarchy.get("request")).get("data")).get("hierarchy");
        Assert.assertTrue(MapUtils.isNotEmpty(nodesModified));
        Assert.assertTrue(MapUtils.isNotEmpty(hierarchyMap));
        Assert.assertTrue(nodesModified.size() == 1);
        Assert.assertTrue(hierarchyMap.size() == 2);
    }

    @Test
    public void testHierarchyGenerateWithEmpty() throws Exception {
        String requestString = "{\n" +
                "        \"course\": {\n" +
                "            \"name\": \"Test_CurriculumCourse With 3 Units\",\n" +
                "            \"code\": \"test-code\",\n" +
                "            \"description\": \"abc\",\n" +
                "            \"mimeType\": \"application/vnd.ekstep.content-collection\",\n" +
                "            \"contentType\": \"Course\"\n" +
                "        },\n" +
                "        \"hierarchy\": []\n" +
                "}";
        Request request = new Request();
        request.getRequest().putAll(mapper.readValue(requestString, new TypeReference<Map<String, Object>>() {}));
        request.setOperation("createCourse");
        Map<String, Object> hierarchy = helper.generateUpdateHierarchyRequest(request, "do_12345");
        Assert.assertNotNull(hierarchy);
        Assert.assertTrue(MapUtils.isEmpty((Map) ((Map<String, Object>) ((Map<String, Object>) hierarchy.get("request")).get("data")).get("nodesModified")));
        Assert.assertTrue(MapUtils.isEmpty((Map) ((Map<String, Object>) ((Map<String, Object>) hierarchy.get("request")).get("data")).get("hierarchy")));
    }

}
