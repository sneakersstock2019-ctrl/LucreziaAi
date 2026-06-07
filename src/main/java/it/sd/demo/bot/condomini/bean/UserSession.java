package it.sd.demo.bot.condomini.bean;

import java.util.ArrayList;
import java.util.List;

public class UserSession {

	public String nome;
	
    public String step;

    public List<ChatMessage> cronologiaMessaggi = new ArrayList<>();

    public int tentativiComprensione = 0;

    public boolean haTicketAperti = false;
    
    public boolean primoMessaggio = true;
    
    public Long idTicketAperto;
    
    public List<String> registrazioniAudio = new ArrayList<>();
    
}