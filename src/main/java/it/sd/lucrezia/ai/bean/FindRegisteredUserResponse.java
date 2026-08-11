package it.sd.lucrezia.ai.bean;

import lombok.Data;

@Data
public class FindRegisteredUserResponse {

    private boolean success;
    private boolean found;

    private int attemptsUsed;
    private boolean maxAttemptsReached;

    private Long idUtente;
    private Long idCondominio;

    private String nomeUtente;
    private String cognomeUtente;
    private String telefonoUtente;

    private String nomeCondominio;
    private String indirizzoCondominio;
    private String interno;

    private String message;
    private String nextAction;
}