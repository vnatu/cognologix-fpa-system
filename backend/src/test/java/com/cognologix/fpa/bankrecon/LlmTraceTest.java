package com.cognologix.fpa.bankrecon;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmTraceTest {

    private static final String API_KEY = "sk-test-mlx-key-9f3a";

    @Test
    void apiKeyNeverAppearsInLogOutput() {
        Logger trace = logger(LlmTrace.TRACE.getName());
        Logger summary = logger(SpringAiStructuredLlmClient.class.getName());
        Logger service = logger(BankReconService.class.getName());
        ListAppender<ILoggingEvent> traceEvents = attach(trace);
        ListAppender<ILoggingEvent> summaryEvents = attach(summary);
        ListAppender<ILoggingEvent> serviceEvents = attach(service);
        try {
            String before = LlmTrace.beforeLine(
                    "R-005#1",
                    "chat",
                    "OMLX",
                    "http://" + API_KEY + "@localhost:9000/v1?api_key=" + API_KEY,
                    "model-" + API_KEY,
                    2,
                    1,
                    "abc123abc123",
                    100,
                    "900",
                    "false",
                    API_KEY);
            LlmTrace.info(summary, API_KEY, before);
            String request = "Authorization: Bearer " + API_KEY + "\napiKey=" + API_KEY + "\n" + before;
            LlmTrace.trace(true, API_KEY, LlmTrace.prefixLines("R-005#1", request));
            LlmTrace.trace(true, API_KEY, LlmTrace.prefixLines(
                    "R-005#1", LlmTrace.responseText("model reply " + API_KEY)));
            LlmTrace.warn(service, API_KEY, "callId=R-005#1 nothing parsed rawReply=see " + API_KEY);
            LlmTrace.error(summary, API_KEY, "R-005#1",
                    "httpStatus=401 errorBody=unauthorized " + API_KEY,
                    new IllegalStateException("mlx_api_key " + API_KEY));
        } finally {
            detach(trace, traceEvents);
            detach(summary, summaryEvents);
            detach(service, serviceEvents);
        }
        assertNoSecret(traceEvents);
        assertNoSecret(summaryEvents);
        assertNoSecret(serviceEvents);
    }

    @Test
    void catalogLoggedInFullOncePerRunThenAsHash() {
        String prompt = MappingPrompt.build(
                List.of(),
                List.of(new MappingPrompt.LedgerLine(
                        "Direct Expenses", "Office Rent Unique", null, null, null, null)),
                List.of(),
                List.of(new MappingPrompt.TransactionLine("1", "rent", "10.00", "PAYMENT", List.of())));
        String catalog = MappingPrompt.catalogSection(prompt);
        String hash = LlmTrace.shortSha256(catalog);
        int ledgers = LlmTrace.catalogLedgerCount(catalog);
        LlmTrace.CatalogMemory memory = new LlmTrace.CatalogMemory();
        String first = LlmTrace.requestText(
                prompt, "OMLX", "http://localhost:9000", "model", 540, false, memory, API_KEY);
        String second = LlmTrace.requestText(
                prompt, "OMLX", "http://localhost:9000", "model", 540, false, memory, API_KEY);

        Logger trace = logger(LlmTrace.TRACE.getName());
        ListAppender<ILoggingEvent> events = attach(trace);
        try {
            LlmTrace.trace(true, API_KEY, LlmTrace.prefixLines("R-005#1", first));
            LlmTrace.trace(true, API_KEY, LlmTrace.prefixLines("R-005#2", second));
        } finally {
            detach(trace, events);
        }

        assertThat(events.list).hasSize(2);
        String firstLog = events.list.get(0).getFormattedMessage();
        String secondLog = events.list.get(1).getFormattedMessage();
        assertThat(firstLog).contains("Office Rent Unique");
        assertThat(firstLog).contains("callId=R-005#1");
        assertThat(secondLog).contains("catalog: " + hash + " (" + ledgers + " ledgers, unchanged)");
        assertThat(secondLog).doesNotContain("Office Rent Unique");
        assertThat(secondLog).contains("callId=R-005#2");
    }

    @Test
    void oversizedResponseIsTruncated() {
        String raw = "A".repeat(5_000) + "M".repeat(15_000) + "Z".repeat(5_000);
        assertThat(raw).hasSize(25_000);
        String formatted = LlmTrace.responseText(raw);
        assertThat(formatted).contains(raw.substring(0, 5_000));
        assertThat(formatted).contains(raw.substring(raw.length() - 5_000));
        assertThat(formatted).contains("totalLength=25000");
        assertThat(formatted).doesNotContain("M");
        assertThat(LlmTrace.responseText("B".repeat(20_000))).isEqualTo("B".repeat(20_000));

        Logger trace = logger(LlmTrace.TRACE.getName());
        ListAppender<ILoggingEvent> events = attach(trace);
        try {
            LlmTrace.trace(true, API_KEY, LlmTrace.prefixLines("R-005#3", formatted));
        } finally {
            detach(trace, events);
        }
        assertThat(events.list).hasSize(1);
        String logged = events.list.getFirst().getFormattedMessage();
        assertThat(logged).contains("callId=R-005#3");
        assertThat(logged).contains("totalLength=25000");
        assertThat(logged).contains("A".repeat(100));
        assertThat(logged).contains("Z".repeat(100));
        assertThat(logged).doesNotContain("M");
        assertThat(logged.length()).isLessThan(raw.length());
    }

    private static void assertNoSecret(ListAppender<ILoggingEvent> events) {
        assertThat(events.list).isNotEmpty();
        for (ILoggingEvent event : events.list) {
            String message = event.getFormattedMessage();
            assertThat(message).doesNotContain(API_KEY);
            assertThat(message.toLowerCase()).doesNotContain("authorization");
            if (event.getThrowableProxy() != null) {
                assertThat(event.getThrowableProxy().getMessage()).doesNotContain(API_KEY);
            }
        }
    }

    private static Logger logger(String name) {
        return (Logger) LoggerFactory.getLogger(name);
    }

    private static ListAppender<ILoggingEvent> attach(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        return appender;
    }

    private static void detach(Logger logger, ListAppender<ILoggingEvent> appender) {
        logger.detachAppender(appender);
        appender.stop();
    }
}
