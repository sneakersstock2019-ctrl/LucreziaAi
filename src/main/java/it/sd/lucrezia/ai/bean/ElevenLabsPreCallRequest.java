package it.sd.lucrezia.ai.bean;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElevenLabsPreCallRequest {

    @JsonProperty("caller_id")
    @JsonAlias("callerId")
    private String callerId;

    @JsonProperty("called_number")
    @JsonAlias("calledNumber")
    private String calledNumber;

    @JsonProperty("call_sid")
    @JsonAlias({
            "callSid",
            "call_id"
    })
    private String callSid;

    @JsonProperty("conversation_id")
    @JsonAlias("conversationId")
    private String conversationId;

    @JsonProperty("agent_id")
    @JsonAlias("agentId")
    private String agentId;
}