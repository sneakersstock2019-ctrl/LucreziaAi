package it.sd.lucrezia.ai.bean;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = "trascrizioneChiamata")
public class VoiceContext {

    private String phone;
    private String nome;
    private String condominio;
    private Long idUtente;
    private Long idCondominio;
    private String callSid;
    private Long idTicketCreato;
    private String recordingUrl;
    private String recordingSid;
    private boolean endCallRequested;
    private long lastUserSpeechTime;
    private boolean salutoVip;
    private Long idTelefonata;
    private long startCallMillis;
    private String esitoTelefonata;
    private String motivoChiusura;
    private int numeroInterruzioni;
    private int numeroTool;
    private boolean initialGreetingCompleted;

    private List<TicketStatusInfo> ticketAperti = new ArrayList<>();
    private String trascrizioneChiamata = "";

    public boolean hasTicketAperti() {
        return ticketAperti != null && !ticketAperti.isEmpty();
    }

    public int getNumeroTicketAperti() {
        return ticketAperti == null ? 0 : ticketAperti.size();
    }
    
    public void incrementNumeroInterruzioni() {
        this.numeroInterruzioni++;
    }

    public void incrementNumeroTool() {
        this.numeroTool++;
    }
}