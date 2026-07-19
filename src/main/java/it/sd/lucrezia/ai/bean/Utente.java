package it.sd.lucrezia.ai.bean;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Utente {

    private Long id;

    private String nome;
    private String cognome;

    private String email;
    private String telefono;

    private String ruolo;

    private Long idCondominio;
    private String nomeCondominio;
    
    private String codiceFiscaleCondominio;
    private String elevenlabsBranchId;

    public String getNomeCompleto() {
        return (nome != null ? nome : "")
                + (cognome != null ? " " + cognome : "");
    }
}