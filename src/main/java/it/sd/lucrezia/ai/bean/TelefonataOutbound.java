package it.sd.lucrezia.ai.bean;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

@Data
public class TelefonataOutbound {

    private Long id;

    private String tipoChiamata;

    private Long idTicket;
    private Long idFornitore;
    private Long idCondominio;
    private Long idTelefonataPrecedente;

    private String telefonoDestinatario;
    private String nominativoDestinatario;

    private String agentId;
    private String agentPhoneNumberId;

    private String conversationId;
    private String sipCallId;

    private String stato;
    private String esito;
    private String motivoChiusura;

    private LocalDateTime dataProgrammata;
    private LocalDateTime dataRichiesta;
    private LocalDateTime dataAvvio;
    private LocalDateTime dataRisposta;
    private LocalDateTime dataFine;

    private Integer durataSecondi;

    private String trascrizione;
    private String urlAudio;
    private String aiSummary;

    private Integer numeroTool;
    private Integer numeroInterruzioni;

    private String errore;

    private Map<String, Object> dynamicVariables;
}