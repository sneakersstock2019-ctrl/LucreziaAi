package it.sd.lucrezia.ai.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvviaChiamataFornitoreResponse {

    private boolean success;
    private Long telefonataOutboundId;
    private String conversationId;
    private String sipCallId;
    private String message;
}