package it.sd.lucrezia.ai.service.voice;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import it.sd.lucrezia.ai.bean.SendApprovalRequest;
import it.sd.lucrezia.ai.bean.SendApprovalResponse;
import it.sd.lucrezia.ai.bean.TelefonataOutbound;
import it.sd.lucrezia.ai.bean.Utente;
import it.sd.lucrezia.ai.dao.RichiestaAssociazioneUtenteDao;
import it.sd.lucrezia.ai.dao.TelefonataOutboundDao;
import it.sd.lucrezia.ai.dao.UtenteDao;
import it.sd.lucrezia.ai.service.elevenlabs.ElevenLabsService;
import it.sd.lucrezia.ai.service.whatsapp.WhatsAppService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnknownUserApprovalService {

    private final RichiestaAssociazioneUtenteDao richiestaDao;
    private final UtenteDao utenteDao;
    private final WhatsAppService whatsAppService;
    private final TelefonataOutboundDao telefonataOutboundDao;
    private final ElevenLabsService elevenLabsService;

    public SendApprovalResponse sendApprovalRequest(
            SendApprovalRequest request) {

        SendApprovalResponse response =
                new SendApprovalResponse();

        Long pendingId =
                richiestaDao.findRichiestaPending(
                        request.getTelefonoNuovo(),
                        request.getIdUtenteRegistrato()
                );

        if (pendingId != null) {

            response.setSuccess(true);
            response.setIdRichiesta(pendingId);
            response.setStato("IN_ATTESA");
            response.setMessage(
                    "Esiste già una richiesta di autorizzazione in attesa."
            );
            response.setNextAction(
                    "APPROVAL_ALREADY_PENDING"
            );

            return response;
        }

        Utente utenteRegistrato =
                utenteDao.findById(
                        request.getIdUtenteRegistrato()
                );

        if (utenteRegistrato == null) {

            response.setSuccess(false);
            response.setMessage(
                    "Utente registrato non trovato."
            );
            response.setNextAction("ERROR");

            return response;
        }

        Long idRichiesta =
                richiestaDao.insertRichiesta(
                        request.getTelefonoNuovo(),
                        request.getNomeNuovo(),
                        request.getCognomeNuovo(),
                        request.getIdUtenteRegistrato(),
                        request.getIdCondominio(),
                        request.getIdTelefonata()
                );

        if (idRichiesta == null) {

            response.setSuccess(false);
            response.setMessage(
                    "Impossibile creare la richiesta di autorizzazione."
            );
            response.setNextAction("ERROR");

            return response;
        }

        /*
         * Qui chiameremo il template Meta:
         * approvazione_utente
         */
        boolean templateInviato =
                whatsAppService.inviaTemplateApprovazioneUtente(
                        utenteRegistrato,
                        request.getNomeNuovo(),
                        request.getCognomeNuovo(),
                        request.getTelefonoNuovo(),
                        idRichiesta
                );
        
        /*Programma chiamata telefonica*/
        Long idTelefonataOutbound = null;

        try {

            idTelefonataOutbound =
                    programmaChiamataApprovazione(
                            idRichiesta,
                            request,
                            utenteRegistrato
                    );

            System.out.println(
                    "Chiamata approvazione utente programmata"
                            + " - idRichiesta="
                            + idRichiesta
                            + " idTelefonataOutbound="
                            + idTelefonataOutbound
            );

        } catch (Exception e) {

            System.err.println(
                    "Errore programmazione chiamata "
                            + "approvazione utente"
                            + " - idRichiesta="
                            + idRichiesta
            );

            e.printStackTrace();
        }
        
        boolean chiamataProgrammata =
                idTelefonataOutbound != null;

        if (!templateInviato
                && !chiamataProgrammata) {

            response.setSuccess(false);
            response.setIdRichiesta(idRichiesta);
            response.setStato("IN_ATTESA");

            response.setMessage(
                    "La richiesta è stata registrata, "
                    + "ma al momento non è stato possibile "
                    + "contattare l'utente registrato."
            );

            response.setNextAction(
                    "APPROVAL_SEND_ERROR"
            );

            return response;
        }

        response.setSuccess(true);
        response.setIdRichiesta(idRichiesta);
        response.setStato("IN_ATTESA");

        response.setMessage(
                "La richiesta di autorizzazione è stata "
                + "inviata all'utente registrato."
        );

        response.setNextAction(
                "APPROVAL_SENT"
        );

        return response;
    }
    
    private Long programmaChiamataApprovazione(
            Long idRichiesta,
            SendApprovalRequest request,
            Utente utenteRegistrato) {

        Map<String, Object> dynamicVariables =
                new LinkedHashMap<>();

        dynamicVariables.put(
                "id_richiesta_associazione",
                String.valueOf(idRichiesta)
        );

        dynamicVariables.put(
                "id_utente_registrato",
                String.valueOf(
                        request.getIdUtenteRegistrato()
                )
        );

        dynamicVariables.put(
                "id_condominio",
                String.valueOf(
                        request.getIdCondominio()
                )
        );

        dynamicVariables.put(
                "nome_utente_registrato",
                safe(utenteRegistrato.getNome())
        );

        dynamicVariables.put(
                "nome_nuovo",
                safe(request.getNomeNuovo())
        );

        dynamicVariables.put(
                "cognome_nuovo",
                safe(request.getCognomeNuovo())
        );

        dynamicVariables.put(
                "telefono_nuovo",
                safe(request.getTelefonoNuovo())
        );

        dynamicVariables.put(
                "condominio",
                safe(utenteRegistrato.getNomeCondominio())
        );

        LocalDateTime ora =
                LocalDateTime.now(
                        ZoneId.of("Europe/Rome")
                );

        dynamicVariables.put(
                "orario_chiamata",
                ora.format(
                        DateTimeFormatter.ofPattern(
                                "dd/MM/yyyy HH:mm"
                        )
                )
        );

        String saluto =
                ora.getHour() >= 5
                        && ora.getHour() < 18
                        ? "Buongiorno"
                        : "Buonasera";

        String nominativoNuovo =
                (
                        safe(request.getNomeNuovo())
                        + " "
                        + safe(request.getCognomeNuovo())
                ).trim();

        String firstMessage =
                saluto
                + " "
                + safe(utenteRegistrato.getNome())
                + ", sono Lucrezia. "
                + "Ti contatto perché "
                + nominativoNuovo
                + ", dal numero "
                + safe(request.getTelefonoNuovo())
                + ", ha chiesto di essere autorizzato "
                + "a utilizzare Lucrezia per il condominio "
                + safe(utenteRegistrato.getNomeCondominio())
                + ". Vuoi approvare la richiesta?";

        dynamicVariables.put(
                "first_message",
                firstMessage
        );

        TelefonataOutbound telefonata =
                new TelefonataOutbound();

        telefonata.setTipoChiamata(
                "APPROVAZIONE_UTENTE"
        );

        telefonata.setIdTicket(null);
        telefonata.setIdFornitore(null);

        telefonata.setIdCondominio(
                request.getIdCondominio()
        );

        telefonata.setIdRichiestaAssociazione(
                idRichiesta
        );

        telefonata.setIdUtenteDestinatario(
                request.getIdUtenteRegistrato()
        );

        telefonata.setTelefonoDestinatario(
                utenteRegistrato.getTelefono()
        );

        telefonata.setNominativoDestinatario(
                (
                        safe(utenteRegistrato.getNome())
                        + " "
                        + safe(utenteRegistrato.getCognome())
                ).trim()
        );

        telefonata.setAgentId(
                elevenLabsService.getApprovazioneUtenteAgentId()
        );

        telefonata.setAgentPhoneNumberId(
                elevenLabsService.getAgentPhoneNumberId()
        );

        telefonata.setStato(
                "DA_AVVIARE"
        );

        telefonata.setDataProgrammata(
                LocalDateTime.now()
        );

        telefonata.setDynamicVariables(
                dynamicVariables
        );

        return telefonataOutboundDao.insert(
                telefonata
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}