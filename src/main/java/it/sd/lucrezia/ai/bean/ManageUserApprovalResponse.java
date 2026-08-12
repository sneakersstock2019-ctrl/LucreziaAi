package it.sd.lucrezia.ai.bean;

import lombok.Data;

@Data
public class ManageUserApprovalResponse {

    private boolean success;

    private Long idRichiestaAssociazione;

    private String stato;

    private String message;

    private String nextAction;
}