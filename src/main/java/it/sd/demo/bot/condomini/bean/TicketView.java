package it.sd.demo.bot.condomini.bean;

public class TicketView {

    private String nome;
    private String condominio;
    private String descrizione;
    private String servizio;
    private String priorita;
    private String stato;
    private String data;

    public TicketView(String nome, String condominio, String descrizione,
                      String servizio, String priorita, String stato, String data) {
        this.nome = nome;
        this.condominio = condominio;
        this.descrizione = descrizione;
        this.servizio = servizio;
        this.priorita = priorita;
        this.stato = stato;
        this.data = data;
    }

    public String getNome() { return nome; }
    public String getCondominio() { return condominio; }
    public String getDescrizione() { return descrizione; }
    public String getServizio() { return servizio; }
    public String getPriorita() { return priorita; }
    public String getStato() { return stato; }
    public String getData() { return data; }
}