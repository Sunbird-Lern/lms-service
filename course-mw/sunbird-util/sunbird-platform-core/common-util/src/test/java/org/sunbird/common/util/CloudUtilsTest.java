package org.sunbird.common.util;

import org.junit.Assert;
import org.junit.Test;
import org.sunbird.common.models.util.cloud.CloudUtils;

public class CloudUtilsTest {

    @Test
    public void testGetObjectKeyMultiplePath() {
        String objKey = CloudUtils.getObjectKey("/tez/po/","test.txt");
        Assert.assertEquals("po/test.txt",objKey);
    }

    @Test
    public void testGetObjectKeyPath() {
        String objKey = CloudUtils.getObjectKey("/po/","test.txt");
        Assert.assertEquals("po/test.txt",objKey);
    }

    @Test
    public void testGetObjectKeyContainerNamePath() {
        String objKey = CloudUtils.getObjectKey("testcontainer","test.txt");
        Assert.assertEquals("testcontainer/test.txt",objKey);
    }

    @Test
    public void testGetObjectKeyContainerMultipleNamePath() {
        String objKey = CloudUtils.getObjectKey("/tez/po/testcontainer","test.txt");
        Assert.assertEquals("po/testcontainer/test.txt",objKey);
    }
}
