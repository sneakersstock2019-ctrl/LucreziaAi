package it.sd.lucrezia.ai.bean;

import lombok.Data;

@Data
public class SendApprovalRequest {

    private Long idUtenteRegistrato;
    private Long idCondominio;

    private String nomeNuovo;
    private String cognomeNuovo;

    private String telefonoNuovo;

    private Long idTelefonata;
}