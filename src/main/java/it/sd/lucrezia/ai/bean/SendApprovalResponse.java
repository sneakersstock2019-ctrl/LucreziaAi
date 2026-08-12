package it.sd.lucrezia.ai.bean;

import lombok.Data;

@Data
public class SendApprovalResponse {

    private boolean success;

    private Long idRichiesta;

    private String stato;

    private String message;

    private String nextAction;
}