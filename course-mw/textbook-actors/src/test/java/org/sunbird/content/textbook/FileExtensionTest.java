package org.sunbird.content.textbook;

import org.junit.Test;

import com.microsoft.azure.storage.table.Ignore;

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
    @Ignore
    public void getExtensionTest() {
        String type = FileExtension.getExtension();
        Assert.assertEquals(null, type);
    }
}
