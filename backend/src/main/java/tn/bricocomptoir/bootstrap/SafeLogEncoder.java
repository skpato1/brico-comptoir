package tn.bricocomptoir.bootstrap;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import ch.qos.logback.core.encoder.EncoderBase;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Allow only static operation codes. Never encode arguments or exception messages/stacks. */
public class SafeLogEncoder extends EncoderBase<ILoggingEvent> {
    public byte[] headerBytes() { return new byte[0]; }
    public byte[] footerBytes() { return new byte[0]; }
    public byte[] encode(ILoggingEvent event) {
        String message=event.getFormattedMessage();
        if(message==null || !message.matches("[A-Z][A-Z0-9_]{2,80}"))message="EVENT_DETAILS_REDACTED";
        String logger=event.getLoggerName();
        if(logger==null || !logger.matches("[a-zA-Z0-9_.]{1,180}"))logger="unknown";
        String exception=event.getThrowableProxy()==null?"":" EXCEPTION_PRESENT";
        return (Instant.ofEpochMilli(event.getTimeStamp())+" "+event.getLevel()+" "+logger+" "+message+exception+"\n").getBytes(StandardCharsets.UTF_8);
    }
}
