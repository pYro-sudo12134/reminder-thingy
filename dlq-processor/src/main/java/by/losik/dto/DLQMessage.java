package by.losik.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DLQMessage {
    private String messageId;
    private String source;
    private String detailType;
    private Instant timestamp;
    private Map<String, Object> detail;
    private int receiveCount;
    private String queueName;
    private String errorMessage;
    private String rawBody;

    public DLQMessage() {}

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDetailType() { return detailType; }
    public void setDetailType(String detailType) { this.detailType = detailType; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getDetail() { return detail; }
    public void setDetail(Map<String, Object> detail) { this.detail = detail; }

    public int getReceiveCount() { return receiveCount; }
    public void setReceiveCount(int receiveCount) { this.receiveCount = receiveCount; }

    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getRawBody() { return rawBody; }
    public void setRawBody(String rawBody) { this.rawBody = rawBody; }

    public boolean isParsed() {
        return source != null && !source.equals("parse-error");
    }
}