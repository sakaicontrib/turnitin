package org.sakaiproject.turnitin.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TurnitinLTIUtilTest {

    private TurnitinLTIUtil util;

    @Before
    public void setUp() {
        this.util = new TurnitinLTIUtil();
    }

    @Test
    public void testCleanupPropertiesEmpty() {
        Map<String, String> empty = Collections.emptyMap();
        Map<String, String> cleaned = util.cleanUpProperties(empty);
        Assert.assertEquals(empty, cleaned);
    }

    @Test
    public void testCleanupPropertiesNoRemove() {
        Map<String, String> good = new HashMap<>();
        good.put("key1", "value1");
        good.put("key2", "value2");
        Map<String, String> cleaned = util.cleanUpProperties(good);
        Assert.assertEquals(good, cleaned);
    }

    @Test
    public void testCleanupPropertiesRemove() {
        Map<String, String> bad = new HashMap<>();
        bad.put("key1", "");
        bad.put("key2", null);
        Map<String, String> cleaned = util.cleanUpProperties(bad);
        Assert.assertEquals(Collections.emptyMap(), cleaned);
    }


}
