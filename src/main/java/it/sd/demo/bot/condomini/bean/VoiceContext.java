package it.sd.demo.bot.condomini.bean;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class VoiceContext {

    private String phone;
    private String nome;
    private String condominio;
    private Long idUtente;

    private List<TicketStatusInfo> ticketAperti = new ArrayList<>();

    public boolean hasTicketAperti() {
        return ticketAperti != null && !ticketAperti.isEmpty();
    }

    public int getNumeroTicketAperti() {
        return ticketAperti == null ? 0 : ticketAperti.size();
    }
}