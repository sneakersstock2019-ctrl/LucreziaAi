package it.sd.lucrezia.ai.bean;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    public List<WhatsAppMessage> cronologiaMessaggi = new ArrayList<>();

    public int tentativiComprensione = 0;

    public boolean primoMessaggio = true;
    
    public Long idTicketAperto;
    
    public List<String> registrazioniAudio = new ArrayList<>();
    
    public String ultimaRegistrazioneAudio;
    
    private VoiceSessionStep voiceSessionStep;
    
    public String step;
    
    private Long condominoId;
    
    private List<Long> openTicketIds;
    
    public Long idTicketFornitore;
    
    private List<TicketStatusInfo> ticketAperti = new ArrayList<>();
    
}