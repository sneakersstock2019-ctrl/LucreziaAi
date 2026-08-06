package it.sd.lucrezia.ai.bean;

import lombok.Data;

@Data
public class ElevenLabsSipCallResult {

    private boolean success;
    private String message;
    private String conversationId;
    private String sipCallId;
}