package tn.bricocomptoir.privacy;
import java.nio.charset.StandardCharsets;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.*;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.bootstrap.SafeLogEncoder;
import static org.assertj.core.api.Assertions.*;
class SafeLogEncoderTest {
    @Test void argumentsSqlAndNestedThrowableNeverAppearInOutput() {
        var logger=(Logger)org.slf4j.LoggerFactory.getLogger("tn.bricocomptoir.privacy.Test");
        var event=new LoggingEvent(getClass().getName(),logger,Level.ERROR,"Email {} token=secret Rue personnelle",new IllegalStateException("SQL person@example.invalid 20123456",new RuntimeException("password=secret")),new Object[]{"person@example.invalid"});
        String output=new String(new SafeLogEncoder().encode(event),StandardCharsets.UTF_8);
        assertThat(output).contains("ERROR","EVENT_DETAILS_REDACTED","EXCEPTION_PRESENT").doesNotContain("person@example.invalid","20123456","Rue personnelle","secret","password=","SQL");
        var safe=new LoggingEvent(getClass().getName(),logger,Level.WARN,"RETENTION_FAILED",null,null);
        assertThat(new String(new SafeLogEncoder().encode(safe),StandardCharsets.UTF_8)).contains("RETENTION_FAILED");
    }
}
