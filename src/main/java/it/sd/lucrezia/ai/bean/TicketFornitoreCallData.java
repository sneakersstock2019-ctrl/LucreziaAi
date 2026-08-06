package it.sd.lucrezia.ai.bean;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class TicketFornitoreCallData {

    private Long idTicket;

    private Long idFornitore;
    private String nomeFornitore;
    private String telefonoFornitore;

    private Long idCondominio;
    private String condominio;
    private String indirizzoCondominio;

    private String categoria;
    private String priorita;
    private String descrizione;

    private Long idCondomino;
    private String nomeCondomino;
    private String telefonoCondomino;

    private LocalDateTime dataApertura;

    private String dashboardUrl;
}