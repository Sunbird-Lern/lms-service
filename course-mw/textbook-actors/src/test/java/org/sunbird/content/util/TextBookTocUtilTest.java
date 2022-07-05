package org.sunbird.content.util;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.learner.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TextBookTocUtilTest {

   private Map testMap =  new HashMap<String, Object>() {
        {
            put("userId", "test");
        }
    };
    @Test
    public void testSerializeMap() {
       String output =  TextBookTocUtil.serialize(testMap);
       Assert.assertEquals("{\"userId\":\"test\"}",output);
    }

    @Test
    public void testSerializeString() {
        String output =  TextBookTocUtil.serialize(new String("TextBook"));
        Assert.assertEquals("\"TextBook\"",output);
    }

    @Test
    public void testStringifyNull() {
        Object output =  TextBookTocUtil.stringify(null);
        Assert.assertEquals("",output);
    }

    @Test
    public void testStringifyEmptyList() {
        Object output =  TextBookTocUtil.stringify(new ArrayList<>());
        Assert.assertEquals("",output);
    }

    @Test
    public void testStringifyList() {
        Object output =  TextBookTocUtil.stringify(new ArrayList<>(){
            {
                add("Book1");
                add("Book2");
            }
        });
        Assert.assertEquals("Book1,Book2",output);
    }

    @Test
    public void testStringifyEmptyArray() {
        Object output =  TextBookTocUtil.stringify(new String[0]);
        Assert.assertEquals("",output);
    }

    @Test
    public void testStringifyArray() {
        Object output =  TextBookTocUtil.stringify(new String[]{"Book1","Book2"});
        Assert.assertEquals("Book1,Book2",output);
    }

    @Test(expected = ProjectCommonException.class)
    public void testGetObject() {
        TextBookTocUtil.getObjectFrom(Mockito.anyString(),Mockito.any());
    }

    @Test
    public void testGetObjectMap() {
        HashMap outputMap = TextBookTocUtil.getObjectFrom("{\"userId\":\"test\"}",HashMap.class);
        Assert.assertEquals(testMap,outputMap);
    }

    @Test(expected = ProjectCommonException.class)
    public void testGetObjectList() {
        TextBookTocUtil.getObjectFrom("{\"userId\":\"test\"}",List.class);
    }

    @Test(expected = ProjectCommonException.class)
    public void testReadContent() {
        TextBookTocUtil.readContent(Mockito.anyString(), Mockito.anyString());
    }

    @Test(expected = ProjectCommonException.class)
    public void testGetRelatedFrameworkById() {
        TextBookTocUtil.getRelatedFrameworkById(Mockito.anyString());
    }
}
