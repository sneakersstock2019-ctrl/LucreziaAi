package it.sd.demo.bot.condomini.bean;

import java.util.ArrayList;
import java.util.List;

public class UserSession {

	public String nome;
	
    public String step;

    public List<String> cronologiaMessaggi = new ArrayList<>();

    public int tentativiComprensione = 0;

    public boolean haTicketAperti = false;
}