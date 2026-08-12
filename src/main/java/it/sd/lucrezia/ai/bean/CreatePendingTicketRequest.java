package it.sd.lucrezia.ai.bean;

import lombok.Data;

@Data
public class CreatePendingTicketRequest {

    private Long idTelefonata;

    private String categoria;
    private String priorita;
    private String area;
    private String descrizione;
}