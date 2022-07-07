package org.sunbird.content.textbook;

import org.junit.Test;
import org.junit.Assert;

public class FileExtensionTest {
    @Test
    public void getDotExtensionTest() {
        String type = FileExtension.getDotExtension();
        Assert.assertEquals(".null", type);
    }

    @Test(expected = NullPointerException.class)
    public void getSeperatorTest() {
       FileExtension.getSeperator( 0);
    }

    @Test
    public void getExtensionTest() {
        String type = FileExtension.getExtension();
        Assert.assertEquals(null, type);
    }
}
