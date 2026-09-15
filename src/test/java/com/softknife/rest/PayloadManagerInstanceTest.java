package com.softknife.rest;

import com.softknife.rest.payload.FreeMarkerPayloadManager;
import com.softknife.util.common.RBFileUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;

public class PayloadManagerInstanceTest {

    @Test(description = "Different payload definitions produce a manager for those definitions, not the first one")
    public void differentDefinitionsRebuildTheManager() throws Exception {
        String payloads = RBFileUtils.getFileOnClassPathAsString("payload/payloads.json");
        assertNotNull(payloads, "fixture missing");

        FreeMarkerPayloadManager first = FreeMarkerPayloadManager.getInstance(payloads);
        FreeMarkerPayloadManager same = FreeMarkerPayloadManager.getInstance(payloads);
        FreeMarkerPayloadManager other = FreeMarkerPayloadManager.getInstance("[]");

        assertSame(same, first, "the same definitions should reuse the manager");
        assertNotSame(other, first, "new definitions were silently ignored");
    }
}
