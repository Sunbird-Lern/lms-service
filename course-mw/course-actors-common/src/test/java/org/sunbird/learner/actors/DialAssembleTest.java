package org.sunbird.learner.actors;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DialAssembleTest {
    
    private static final ActorSystem system = ActorSystem.create("system");
    private static final Props props = Props.create(PageManagementActor.class);
    private static final TestActorRef<PageManagementActor> actorRef = TestActorRef.create(system, props, "testDialPage");
    
    private static final String sectionsStr = "[{\"collectionsCount\":4,\"collections\":[{\"identifier\":\"do_1128565809467146241914\",\"subject\":\"Math\",\"childNodes\":[\"do_1128565812897710081915\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"medium\":\"English\",\"objectType\":\"Content\",\"appIcon\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1128565809467146241914/artifact/download_1551875986627.thumb.png\",\"gradeLevel\":[\"Grade 1\"],\"size\":42980,\"name\":\"25th Sept Book new\",\"contentType\":\"TextBook\",\"board\":\"NCERT\",\"resourceType\":\"Book\"},{\"identifier\":\"do_11275809293017907217\",\"subject\":\"English\",\"childNodes\":[\"do_11275809309185638418\"],\"originData\":\"{\\\"name\\\":\\\"Shallow Copy Origin\\\",\\\"copyType\\\":\\\"shallow\\\",\\\"license\\\":\\\"CC BY 4.0\\\",\\\"organisation\\\":[\\\"Sunbird\\\"],\\\"pkgVersion\\\":4.0}\",\"mimeType\":\"application/vnd.ekstep.content-collection\",\"medium\":\"Marathi\",\"objectType\":\"Content\",\"appIcon\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_112732670888542208125/artifact/1_9zymhi_1554281114487.png\",\"gradeLevel\":[\"Grade 1\"],\"size\":6327,\"name\":\"Book 100\",\"contentType\":\"TextBook\",\"board\":\"KA\",\"resourceType\":\"Book\"},{\"identifier\":\"do_11299090913177600017\",\"subject\":\"English\",\"childNodes\":[\"do_11299090931854540818\"],\"originData\":\"{\\\"name\\\":\\\"Shallow Copy Origin\\\",\\\"copyType\\\":\\\"shallow\\\",\\\"license\\\":\\\"CC BY 4.0\\\",\\\"organisation\\\":[\\\"Sunbird\\\"],\\\"pkgVersion\\\":4.0}\",\"mimeType\":\"application/vnd.ekstep.content-collection\",\"medium\":\"English\",\"objectType\":\"Content\",\"gradeLevel\":[\"Grade 5\",\"Grade 11\"],\"appIcon\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_112583979797594112134/artifact/cone1_1536130346961.png\",\"size\":102982,\"name\":\"BOOK_APRIL\",\"contentType\":\"TextBook\",\"board\":\"MH\",\"resourceType\":\"Book\"},{\"identifier\":\"do_112990985068732416125\",\"appIcon\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_112583979797594112134/artifact/cone1_1536130346961.png\",\"size\":103087,\"childNodes\":[\"do_11299090931854540818\"],\"origin\":\"do_11299090913177600017\",\"name\":\"copy1 book\",\"originData\":\"{\\\"name\\\":\\\"BOOK_APRIL\\\",\\\"copyType\\\":\\\"shallow\\\",\\\"license\\\":\\\"CC BY 4.0\\\",\\\"organisation\\\":[\\\"Sunbird\\\"],\\\"author\\\":\\\"KIRUBAAA\\\",\\\"pkgVersion\\\":1.0}\",\"mimeType\":\"application/vnd.ekstep.content-collection\",\"contentType\":\"TextBook\",\"objectType\":\"Content\",\"board\":\"AP\",\"resourceType\":\"Book\"}],\"searchQuery\":\"{\\\"request\\\":{\\\"facets\\\":[\\\"language\\\",\\\"grade\\\",\\\"domain\\\",\\\"contentType\\\",\\\"subject\\\",\\\"medium\\\"],\\\"filters\\\":{\\\"contentType\\\":[\\\"TextBook\\\",\\\"TextBookUnit\\\"],\\\"objectType\\\":[\\\"Content\\\"],\\\"status\\\":[\\\"Live\\\"],\\\"compatibilityLevel\\\":{\\\"max\\\":4,\\\"min\\\":1}},\\\"mode\\\":\\\"collection\\\"},\\\"limit\\\":10,\\\"sort_by\\\":{\\\"lastUpdatedOn\\\":\\\"desc\\\"}}\"}]";
    private static ObjectMapper mapper = new ObjectMapper();
    
    @Test
    public void testUserProfileData() throws Exception {
        List<Map<String, Object>> sectionList = mapper.readValue(sectionsStr, new TypeReference<List<Map<String, Object>>>(){});
        Map<String, Object> userProfile = new HashMap<String, Object>(){{
            put("board", "KA");
        }};
        Method method = PageManagementActor.class.getDeclaredMethod("getUserProfileData", List.class, Map.class);
        method.setAccessible(true);
        List<Map<String, Object>> response = (List<Map<String, Object>>) method.invoke(actorRef.underlyingActor(), sectionList, userProfile);
        Assert.assertEquals(1, response.get(0).get("collectionsCount"));
        Assert.assertEquals("KA", ((List<Map<String, Object>>)response.get(0).get("collections")).get(0).get("board"));
    }

    @Test
    public void testUserProfileDataMultiBoard() throws Exception {
        List<Map<String, Object>> sectionList = mapper.readValue(sectionsStr, new TypeReference<List<Map<String, Object>>>(){});
        Map<String, Object> userProfile = new HashMap<String, Object>(){{
            put("board", Arrays.asList("KA", "MH"));
        }};
        Method method = PageManagementActor.class.getDeclaredMethod("getUserProfileData", List.class, Map.class);
        method.setAccessible(true);
        List<Map<String, Object>> response = (List<Map<String, Object>>) method.invoke(actorRef.underlyingActor(), sectionList, userProfile);
        Assert.assertEquals(2, response.get(0).get("collectionsCount"));
        Assert.assertEquals("KA", ((List<Map<String, Object>>)response.get(0).get("collections")).get(0).get("board"));
        Assert.assertEquals("MH", ((List<Map<String, Object>>)response.get(0).get("collections")).get(1).get("board"));
    }

    @Test
    public void testUserProfileDataInvalidBoard() throws Exception {
        List<Map<String, Object>> sectionList = mapper.readValue(sectionsStr, new TypeReference<List<Map<String, Object>>>(){});
        Map<String, Object> userProfile = new HashMap<String, Object>(){{
            put("board", "JK");
        }};
        Method method = PageManagementActor.class.getDeclaredMethod("getUserProfileData", List.class, Map.class);
        method.setAccessible(true);
        List<Map<String, Object>> response = (List<Map<String, Object>>) method.invoke(actorRef.underlyingActor(), sectionList, userProfile);
        Assert.assertNotEquals(0, response.get(0).get("collectionsCount"));
        ((List<Map<String, Object>>)response.get(0).get("collections")).forEach(content -> {
            Assert.assertNotEquals("JK", content.get("board"));
        });
    }
}
