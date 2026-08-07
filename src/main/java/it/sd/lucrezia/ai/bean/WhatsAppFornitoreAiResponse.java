package it.sd.lucrezia.ai.bean;

import lombok.Data;

@Data
public class WhatsAppFornitoreAiResponse {

    /*
     * ACCEPT
     * REJECT
     * NEED_INFO
     * UNCLEAR
     */
    private String action;

    /*
     * Ticket identificato dall'AI.
     * Utile soprattutto se il fornitore ne ha più di uno.
     */
    private Long ticketId;

    /*
     * ISO:
     * 2026-08-08T10:30:00
     */
    private String dataIntervento;

    /*
     * Risposta naturale da inviare al fornitore.
     */
    private String reply;

    /*
     * Motivo dell'eventuale rifiuto.
     */
    private String motivo;

    /*
     * true quando l'AI ritiene di avere
     * informazioni sufficienti per aggiornare il DB.
     */
    private boolean complete;
}