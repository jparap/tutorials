package com.baeldung.batch;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ContextConfiguration;

@SpringBatchTest
@EnableAutoConfiguration
@ContextConfiguration(classes = { SpringBatchRetryConfig.class })
public class SpringBatchRetryIntegrationTest {

    private static final String TEST_OUTPUT = "xml/retryOutput.xml";
    private static final String EXPECTED_OUTPUT = "src/test/resources/output/batchRetry/retryOutput.xml";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @MockBean
    private CloseableHttpClient closeableHttpClient;

    @Mock
    private CloseableHttpResponse httpResponse;

    @Test
    public void whenEndpointAlwaysFail_thenJobFails() throws Exception {
        when(closeableHttpClient.execute(any()))
          .thenThrow(new ConnectTimeoutException("Endpoint is down"));

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(defaultJobParameters());
        JobInstance actualJobInstance = jobExecution.getJobInstance();
        ExitStatus actualJobExitStatus = jobExecution.getExitStatus();

        assertEquals("retryBatchJob", actualJobInstance.getJobName());
        assertEquals("FAILED", actualJobExitStatus.getExitCode());
        assertThat(actualJobExitStatus.getExitDescription(), containsString("org.apache.http.conn.ConnectTimeoutException"));
    }

    @Test
    public void whenEndpointFailsTwicePasses3rdTime_thenSuccess() throws Exception {
        FileSystemResource expectedResult = new FileSystemResource(EXPECTED_OUTPUT);
        FileSystemResource actualResult = new FileSystemResource(TEST_OUTPUT);

        //fails for first two calls and passes third time onwards
        when(httpResponse.getEntity())
          .thenReturn(new StringEntity("{ \"age\":10, \"postCode\":\"430222\" }"));
        when(closeableHttpClient.execute(any()))
          .thenThrow(new ConnectTimeoutException("Timeout count 1"))
          .thenThrow(new ConnectTimeoutException("Timeout count 2"))
          .thenReturn(httpResponse);

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(defaultJobParameters());
        JobInstance actualJobInstance = jobExecution.getJobInstance();
        ExitStatus actualJobExitStatus = jobExecution.getExitStatus();

        assertEquals("retryBatchJob", actualJobInstance.getJobName());
        assertEquals("COMPLETED", actualJobExitStatus.getExitCode());
        org.assertj.core.api.Assertions.assertThat(actualResult.getFile()).hasSameTextualContentAs(expectedResult.getFile());
    }

    private JobParameters defaultJobParameters() {
        JobParametersBuilder paramsBuilder = new JobParametersBuilder();
        paramsBuilder.addString("jobID", String.valueOf(System.currentTimeMillis()));
        return paramsBuilder.toJobParameters();
    }
}
