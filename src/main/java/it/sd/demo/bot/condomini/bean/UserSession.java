package it.sd.demo.bot.condomini.bean;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

	public String nome;
	
    public List<ChatMessage> cronologiaMessaggi = new ArrayList<>();

    public int tentativiComprensione = 0;

    public boolean primoMessaggio = true;
    
    public Long idTicketAperto;
    
    public List<String> registrazioniAudio = new ArrayList<>();
    
    public String ultimaRegistrazioneAudio;
    
    private VoiceSessionStep voiceSessionStep;
    
    public String step;
    
    private Long condominoId;
    
    private List<Long> openTicketIds;
    
    private List<TicketStatusInfo> ticketAperti = new ArrayList<>();
    
}