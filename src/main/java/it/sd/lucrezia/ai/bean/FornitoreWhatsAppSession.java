package it.sd.lucrezia.ai.bean;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class FornitoreWhatsAppSession {

    private Long idTicket;

    /*
     * Informazioni parziali raccolte durante la conversazione.
     */
    private String dataParziale;
    private String oraParziale;

    /*
     * ACCEPT / REJECT / ecc.
     */
    private String intent;

    private List<WhatsAppMessage> cronologiaMessaggi =
            new ArrayList<>();
}