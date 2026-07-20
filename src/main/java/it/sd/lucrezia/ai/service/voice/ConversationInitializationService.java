package it.sd.lucrezia.ai.service.voice;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import it.sd.lucrezia.ai.bean.Utente;
import it.sd.lucrezia.ai.bean.VoiceConversationContext;
import it.sd.lucrezia.ai.dao.TelefonataDao;
import it.sd.lucrezia.ai.dao.TicketDao;
import it.sd.lucrezia.ai.dao.UtenteDao;
import it.sd.lucrezia.ai.util.CallLogger;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationInitializationService {

    private final UtenteDao utenteDao;
    private final TicketDao ticketDao;
    private final TelefonataDao telefonataDao;

    public VoiceConversationContext initialize(
            String fromNumber,
            String toNumber,
            String callSid,
            String conversationId,
            String canale
    ) {

        String telefono = normalizePhoneNumber(fromNumber);
        String numeroLucrezia = normalizePhoneNumber(toNumber);
        String normalizedCallSid = safe(callSid);

        if (telefono.isBlank()) {
            throw new IllegalArgumentException(
                    "Numero chiamante non presente"
            );
        }

        Utente utente = utenteDao.findCondominoByTelefono(telefono);
        if (utente == null) {
            throw new IllegalStateException(
                    "Nessun condomino associato al numero "
                            + telefono
            );
        }

        int ticketAperti =
                ticketDao.countTicketApertiByUtente(
                        utente.getId()
                );

        Long idTelefonata =
                telefonataDao.insertTelefonata(
                        normalizedCallSid,
                        telefono,
                        utente.getId(),
                        utente.getIdCondominio()
                );

        if (idTelefonata == null) {
            throw new IllegalStateException(
                    "Impossibile registrare la telefonata "
                            + normalizedCallSid
            );
        }

        if (conversationId != null
                && !conversationId.isBlank()) {

            telefonataDao.updateElevenLabsConversationId(
                    idTelefonata,
                    conversationId,
                    normalizedCallSid
            );
        }

        String firstMessage =
                buildFirstMessage(
                        utente,
                        ticketAperti
                );

        Map<String, Object> dynamicVariables =
                new LinkedHashMap<>();

        dynamicVariables.put(
                "call_sid",
                normalizedCallSid
        );

        dynamicVariables.put(
                "conversation_id",
                safe(conversationId)
        );

        dynamicVariables.put(
                "id_telefonata",
                String.valueOf(idTelefonata)
        );

        dynamicVariables.put(
                "id_utente",
                String.valueOf(utente.getId())
        );

        dynamicVariables.put(
                "id_condominio",
                String.valueOf(utente.getIdCondominio())
        );

        dynamicVariables.put(
                "nome",
                safe(utente.getNome())
        );

        dynamicVariables.put(
                "telefono",
                telefono
        );

        dynamicVariables.put(
                "numero_lucrezia",
                numeroLucrezia
        );

        dynamicVariables.put(
                "condominio",
                safe(utente.getNomeCondominio())
        );

        dynamicVariables.put(
                "codice_fiscale_condominio",
                safe(utente.getCodiceFiscaleCondominio())
        );

        dynamicVariables.put(
                "ticket_aperti",
                ticketAperti
        );

        dynamicVariables.put(
                "first_message",
                firstMessage
        );

        dynamicVariables.put(
                "canale",
                safe(canale)
        );

        dynamicVariables.put(
                "branch_condominio",
                buildBranchName(utente)
        );

        CallLogger.info(
                normalizedCallSid,
                "CONVERSATION INIT - canale=" + canale
                        + " idTelefonata=" + idTelefonata
                        + " idUtente=" + utente.getId()
                        + " idCondominio="
                        + utente.getIdCondominio()
                        + " branchId="
                        + utente.getElevenlabsBranchId()
        );

        return new VoiceConversationContext(
                idTelefonata,
                utente,
                ticketAperti,
                utente.getElevenlabsBranchId(),
                firstMessage,
                dynamicVariables
        );
    }

    private String buildFirstMessage(
            Utente utente,
            int ticketAperti
    ) {

        String nome = safe(utente.getNome());

        if (ticketAperti == 1) {
            return "Ciao " + nome
                    + ", sono Lucrezia. "
                    + "Vedo che hai una segnalazione aperta. "
                    + "Vuoi conoscerne lo stato oppure "
                    + "aprire una nuova segnalazione?";
        }

        if (ticketAperti > 1) {
            return "Ciao " + nome
                    + ", sono Lucrezia. "
                    + "Vedo che hai " + ticketAperti
                    + " segnalazioni aperte. "
                    + "Vuoi conoscere lo stato di una segnalazione "
                    + "oppure aprirne una nuova?";
        }

        return "Ciao " + nome
                + ", sono Lucrezia, l'assistente del condominio "
                + safe(utente.getNomeCondominio())
                + ". Come posso aiutarti?";
    }

    private String buildBranchName(Utente utente) {

        String codiceFiscale =
                safe(utente.getCodiceFiscaleCondominio());

        String nomeCondominio =
                safe(utente.getNomeCondominio());

        return codiceFiscale
                + " - "
                + nomeCondominio;
    }

    private String normalizePhoneNumber(String phone) {

        if (phone == null) {
            return "";
        }

        phone = phone.replaceAll("[^0-9]", "");

        if (phone.length() > 10) {
            phone = phone.substring(phone.length() - 10);
        }

        return phone;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}