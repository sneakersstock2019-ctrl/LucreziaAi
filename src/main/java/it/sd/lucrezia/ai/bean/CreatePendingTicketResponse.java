package it.sd.lucrezia.ai.bean;

import lombok.Data;

@Data
public class CreatePendingTicketResponse {

    private boolean success;

    private Long idTicket;

    private String stato;

    private String message;

    private String nextAction;
}