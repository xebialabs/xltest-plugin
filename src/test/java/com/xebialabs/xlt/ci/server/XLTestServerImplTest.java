package com.xebialabs.xlt.ci.server;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import com.xebialabs.xlt.ci.TestSpecificationDescribable;
import com.xebialabs.xlt.ci.server.authentication.UsernamePassword;
import com.xebialabs.xlt.ci.server.domain.TestSpecification;
import hudson.FilePath;
import hudson.util.ListBoxModel;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.mail.BodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class XLTestServerImplTest {
    // TODO: this is from the demo data and not 'the whole truth'
    private static final String TEST_SPEC_RESPONSE = "{\"regressionTests\":{\"id\":\"regressionTests\",\"project\":{\"id\":\"DemoProject\",\"title\":\"Demo Project\",\"type\":\"xlt.Project\"},\"qualification\":{\"description\":\"Description unavailable\",\"type\":\"xlt.DefaultFunctionalTestsQualifier\"},\"title\":\"regressionTests\",\"type\":\"xlt.ShowCaseTestSpecification\"},\"demoGatling\":{\"id\":\"demoGatling\",\"project\":{\"id\":\"DemoProject\",\"title\":\"Demo Project\",\"type\":\"xlt.Project\"},\"qualification\":{\"description\":\"Description unavailable\",\"type\":\"xlt.DefaultPerformanceTestsQualifier\"},\"title\":\"demoGatling\",\"type\":\"xlt.ShowCaseTestSpecification\"},\"functionalTestsComponentA\":{\"id\":\"functionalTestsComponentA\",\"project\":{\"id\":\"DemoProject\",\"title\":\"Demo Project\",\"type\":\"xlt.Project\"},\"qualification\":{\"description\":\"Description unavailable\",\"type\":\"xlt.DefaultFunctionalTestsQualifier\"},\"title\":\"functionalTestsComponentA\",\"type\":\"xlt.ShowCaseTestSpecification\"},\"f3850327-69df-4f01-b063-e5d367a960f8\":{\"id\":\"f3850327-69df-4f01-b063-e5d367a960f8\",\"project\":{\"id\":\"DemoProject\",\"title\":\"Demo Project\",\"type\":\"xlt.Project\"},\"title\":\"allMyTests\",\"type\":\"xlt.TestSpecificationSet\"},\"calculatorTestsComponentB\":{\"id\":\"calculatorTestsComponentB\",\"project\":{\"id\":\"DemoProject\",\"title\":\"Demo Project\",\"type\":\"xlt.Project\"},\"qualification\":{\"description\":\"Description unavailable\",\"type\":\"xlt.DefaultFunctionalTestsQualifier\"},\"title\":\"calculatorTestsComponentB\",\"type\":\"xlt.ShowCaseTestSpecification\"},\"performance tests (old) for Demo\":{\"id\":\"performance tests (old) for Demo\",\"project\":{\"id\":\"DemoProject\",\"title\":\"Demo Project\",\"type\":\"xlt.Project\"},\"title\":\"performance tests (old) for Demo\",\"type\":\"xlt.ShowCaseTestSpecification\"}}";

    MockWebServer xltStub;
    XLTestServerImpl xlTestServer;

    @BeforeMethod
    public void setup() throws IOException {
        xltStub = new MockWebServer();
        xltStub.start();

        UsernamePassword cred = Mockito.mock(UsernamePassword.class);
        when(cred.getUsername()).thenReturn("admin");
        when(cred.getPassword()).thenReturn("admin");

        xlTestServer = new XLTestServerImpl(String.format("http://127.0.0.1:%d", xltStub.getPort()), null, cred);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown() throws IOException {
        xltStub.shutdown();
    }

    @Test
    public void shouldCheckConnection() throws InterruptedException {
        xltStub.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("{}"));

        xlTestServer.checkConnection();

        RecordedRequest request = xltStub.takeRequest();
        assertEquals(request.getRequestLine(), "GET /api/internal/data HTTP/1.1");
        assertEquals(request.getHeader("accept"), "application/json; charset=utf-8");
        assertEquals(request.getHeader("authorization"), "Basic YWRtaW46YWRtaW4=");
        assertEquals(request.getBody().readUtf8(), "");
    }

    @Test
    public void shouldLoadTestSpecifications() throws IOException, InterruptedException {
        xltStub.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(TEST_SPEC_RESPONSE));

        Map<String, TestSpecification> testSpecs = xlTestServer.getTestSpecifications();
        assertEquals(testSpecs.size(), 6L);

        RecordedRequest request = xltStub.takeRequest();
        assertEquals(request.getRequestLine(), "GET /api/internal/testspecifications/extended HTTP/1.1");
        assertEquals(request.getHeader("accept"), "application/json; charset=utf-8");
        assertEquals(request.getHeader("authorization"), "Basic YWRtaW46YWRtaW4=");
        assertEquals(request.getBody().readUtf8(), "");
    }

    @Test
    public void fillsTestSpecificationIdItems() {
        xltStub.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(TEST_SPEC_RESPONSE));

        Map<String, TestSpecification> testSpecifications = xlTestServer.getTestSpecifications();
        assertEquals(testSpecifications.size(), 6);

        ListBoxModel result = TestSpecificationDescribable.TestSpecificationDescriptor.getSpecificationOptions(testSpecifications);

        assertEquals(result.size(), 5); // because we don't count the one superset in the list
    }

    @Test
    public void shouldImport() throws Exception {
        xltStub.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("{ \"testRunId\": \"testrunid\" }"));
        FilePath fp = new FilePath(new File("."));

        PrintStream logger = new PrintStream(System.out);

        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("source", "jenkins");
        metadata.put("serverUrl", "http://my-jenkins");
        metadata.put("buildResult", "success");
        metadata.put("buildNumber", "10");
        metadata.put("jobUrl", "http://my-jenkins/job/sub-dir/my-job");
        metadata.put("jobName", "sub-dir/my-job");
        metadata.put("buildUrl", "http://my-jenkins/job/test/14");
        metadata.put("executedOn", "slave1");
        metadata.put("buildParameters", new HashMap());

        xlTestServer.uploadTestRun("testspecid", fp, "**/*.xml", null, metadata, logger);

        RecordedRequest request = xltStub.takeRequest();
        assertEquals(request.getRequestLine(), "POST /api/internal/import/testspecid HTTP/1.1");
        assertEquals(request.getHeader("accept"), "application/json; charset=utf-8");
        assertEquals(request.getHeader("authorization"), "Basic YWRtaW46YWRtaW4=");
        assertTrue(request.getBodySize() > 0);

        ByteArrayDataSource bads = new ByteArrayDataSource(request.getBody().inputStream(), "multipart/mixed");
        MimeMultipart mp = new MimeMultipart(bads);
        assertTrue(request.getBodySize() > 0);

        assertEquals(mp.getCount(), 2);
        assertEquals(mp.getContentType(), "multipart/mixed");

        // TODO could do additional checks on metadata content
        BodyPart bodyPart1 = mp.getBodyPart(0);
        assertEquals(bodyPart1.getContentType(), "application/json; charset=utf-8");

        BodyPart bodyPart2 = mp.getBodyPart(1);
        assertEquals(bodyPart2.getContentType(), "application/zip");
    }
}