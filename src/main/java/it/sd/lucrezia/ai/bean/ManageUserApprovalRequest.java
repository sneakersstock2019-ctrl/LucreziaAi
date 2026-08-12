package it.sd.lucrezia.ai.bean;

import lombok.Data;

@Data
public class ManageUserApprovalRequest {

    private Long idRichiestaAssociazione;

    /*
     * APPROVA
     * RIFIUTA
     */
    private String esito;
}