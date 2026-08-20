package it.sd.lucrezia.ai.service.voice;

import org.springframework.stereotype.Service;

import it.sd.lucrezia.ai.bean.FindRegisteredUserRequest;
import it.sd.lucrezia.ai.bean.FindRegisteredUserResponse;
import it.sd.lucrezia.ai.bean.SendApprovalRequest;
import it.sd.lucrezia.ai.bean.SendApprovalResponse;
import it.sd.lucrezia.ai.bean.Utente;
import it.sd.lucrezia.ai.dao.RichiestaAssociazioneUtenteDao;
import it.sd.lucrezia.ai.dao.UtenteDao;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnknownUserService {

    private static final int MAX_TENTATIVI = 2;

    private final UtenteDao utenteDao;
    private final UnknownUserSearchAttemptService attemptService;
    private final UnknownUserApprovalService unknownUserApprovalService;
    private final RichiestaAssociazioneUtenteDao richiestaAssociazioneUtenteDao;

    public FindRegisteredUserResponse findRegisteredUser(
            FindRegisteredUserRequest request) {

        FindRegisteredUserResponse response =
                new FindRegisteredUserResponse();

        /*
         * Se abbiamo già raggiunto il massimo,
         * non effettuiamo altre query di ricerca.
         *
         * In questo caso assicuriamoci però che esista
         * una richiesta di associazione da verificare
         * manualmente da parte dell'ADMIN.
         */
        if (attemptService.maxReached(
                request.getIdTelefonata())) {

            Long idRichiesta =
                    creaRichiestaDaVerificareAdminSeNecessaria(
                            request
                    );

            /*
             * Il tool ha completato correttamente il proprio flusso.
             * Non è un errore tecnico: semplicemente l'utente
             * registrato non è stato individuato entro i tentativi previsti.
             */
            response.setSuccess(true);
            response.setFound(false);

            response.setAttemptsUsed(
                    attemptService.get(
                            request.getIdTelefonata()
                    )
            );

            response.setMaxAttemptsReached(true);

            /*
             * Se hai aggiunto idRichiesta al bean response.
             */
            response.setIdRichiesta(
                    idRichiesta
            );

            response.setMessage(
                    "Sono già stati effettuati "
                            + MAX_TENTATIVI
                            + " tentativi di identificazione "
                            + "senza successo. "
                            + "La richiesta sarà verificata "
                            + "da un amministratore."
            );
            
            response.setNextAction(
                    "MAX_ATTEMPTS_REACHED"
            );

            return response;
        }

        /*
         * Prima validiamo la modalità di ricerca.
         *
         * Questa validazione NON conta come tentativo,
         * perché non abbiamo ancora interrogato il DB.
         */
        boolean ricercaTelefono =
                !isBlank(
                        request.getTelefonoRegistrato()
                );

        boolean ricercaDati =
                !isBlank(request.getNomeRegistrato())
                && !isBlank(request.getCognomeRegistrato())
                && !isBlank(request.getIndirizzoCondominio())
                && !isBlank(request.getInterno());

        if (!ricercaTelefono && !ricercaDati) {

            response.setSuccess(false);
            response.setFound(false);

            response.setAttemptsUsed(
                    attemptService.get(
                            request.getIdTelefonata()
                    )
            );

            response.setMaxAttemptsReached(false);

            response.setMessage(
                    "Mancano informazioni per effettuare la ricerca. "
                    + "È necessario il numero di telefono dell'utente "
                    + "registrato oppure nome, cognome, indirizzo "
                    + "del condominio e interno."
            );

            response.setNextAction(
                    "MISSING_INFORMATION"
            );

            return response;
        }

        /*
         * Solo ora consumiamo un tentativo.
         */
        int tentativo =
                attemptService.increment(
                        request.getIdTelefonata()
                );

        response.setAttemptsUsed(
                tentativo
        );

        Utente utente;

        if (ricercaTelefono) {

            utente =
                    utenteDao.findUtenteRegistratoByTelefono(
                            request.getTelefonoRegistrato()
                    );

        } else {

            utente =
                    utenteDao.findUtenteRegistratoByDati(
                            request.getNomeRegistrato(),
                            request.getCognomeRegistrato(),
                            request.getIndirizzoCondominio(),
                            request.getInterno()
                    );
        }

        /*
         * TROVATO
         */
        if (utente != null) {

            response.setSuccess(true);
            response.setFound(true);
            response.setMaxAttemptsReached(false);

            response.setIdUtente(
                    utente.getId()
            );

            response.setIdCondominio(
                    utente.getIdCondominio()
            );

            response.setNomeUtente(
                    utente.getNome()
            );

            response.setCognomeUtente(
                    utente.getCognome()
            );

            response.setTelefonoUtente(
                    utente.getTelefono()
            );

            response.setNomeCondominio(
                    utente.getNomeCondominio()
            );

            response.setIndirizzoCondominio(
                    utente.getIndirizzoCondominio()
            );

            response.setInterno(
                    utente.getInterno()
            );

            response.setMessage(
                    "Utente registrato individuato correttamente."
            );

            response.setNextAction(
                    "USER_FOUND"
            );

            SendApprovalRequest approvalRequest =
                    new SendApprovalRequest();

            approvalRequest.setIdUtenteRegistrato(
                    utente.getId()
            );

            approvalRequest.setIdCondominio(
                    utente.getIdCondominio()
            );

            approvalRequest.setNomeNuovo(
                    request.getNomeNuovo()
            );

            approvalRequest.setCognomeNuovo(
                    request.getCognomeNuovo()
            );

            approvalRequest.setTelefonoNuovo(
                    request.getTelefonoNuovo()
            );

            approvalRequest.setIdTelefonata(
                    request.getIdTelefonata()
            );

            SendApprovalResponse approvalResponse =
                    unknownUserApprovalService
                            .sendApprovalRequest(
                                    approvalRequest
                            );
            
            if (approvalResponse.isSuccess()) {

                response.setMessage(
                        "Utente registrato individuato correttamente. "
                        + "La richiesta di autorizzazione è stata inviata."
                );

                response.setNextAction(
                        "USER_FOUND_AND_APPROVAL_SENT"
                );

            } else {

                response.setMessage(
                        "Utente registrato individuato correttamente, "
                        + "ma non è stato possibile completare "
                        + "l'invio della richiesta di autorizzazione."
                );

                response.setNextAction(
                        "USER_FOUND_APPROVAL_ERROR"
                );
            }

            /*
             * Non serviranno più tentativi.
             */
            attemptService.clear(
                    request.getIdTelefonata()
            );
            
            return response;
        }

        /*
         * ============================================================
         * NON TROVATO
         * ============================================================
         */

        boolean maxReached =
                tentativo >= MAX_TENTATIVI;

        response.setSuccess(true);
        response.setFound(false);
        response.setMaxAttemptsReached(maxReached);

        /*
         * ============================================================
         * MASSIMO TENTATIVI RAGGIUNTO
         * ============================================================
         *
         * Questo è il punto fondamentale:
         * l'ultimo tentativo è appena fallito.
         *
         * Creiamo subito una richiesta di associazione
         * DA_VERIFICARE_ADMIN, senza aspettare una nuova
         * invocazione del tool.
         */
        if (maxReached) {

            Long idRichiesta =
                    creaRichiestaDaVerificareAdminSeNecessaria(
                            request
                    );

            response.setIdRichiesta(
                    idRichiesta
            );

            response.setMessage(
                    "Non è stato possibile individuare "
                            + "l'utente registrato dopo "
                            + MAX_TENTATIVI
                            + " tentativi. "
                            + "La richiesta sarà verificata "
                            + "da un amministratore."
            );

            response.setNextAction(
                    "MAX_ATTEMPTS_REACHED"
            );

            return response;
        }

        /*
         * ============================================================
         * CI SONO ANCORA TENTATIVI DISPONIBILI
         * ============================================================
         */

        response.setMessage(
                "Nessun utente registrato trovato "
                        + "con i dati indicati."
        );

        if (ricercaTelefono) {

            response.setNextAction(
                    "RETRY_SEARCH_NUMBER"
            );

        } else {

            response.setNextAction(
                    "RETRY_SEARCH_ANAG"
            );
        }

        return response;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
    
    private Long creaRichiestaDaVerificareAdminSeNecessaria(
            FindRegisteredUserRequest request) {

        Long idRichiesta =
        		richiestaAssociazioneUtenteDao.findIdByTelefonata(
                        request.getIdTelefonata()
                );

        /*
         * Se esiste già una richiesta per questa telefonata,
         * non ne creiamo una seconda.
         */
        if (idRichiesta != null) {
            return idRichiesta;
        }

        return richiestaAssociazioneUtenteDao
                .insertRichiestaDaVerificareAdmin(
                        request.getTelefonoNuovo(),
                        request.getNomeNuovo(),
                        request.getCognomeNuovo(),

                        request.getTelefonoRegistrato(),
                        request.getNomeRegistrato(),
                        request.getCognomeRegistrato(),
                        request.getIndirizzoCondominio(),

                        request.getInterno() != null
                                ? String.valueOf(
                                        request.getInterno()
                                )
                                : null,

                        request.getIdTelefonata()
                );
    }
}