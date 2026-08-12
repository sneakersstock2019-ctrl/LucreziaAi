package it.sd.lucrezia.ai.service.voice;

import org.springframework.stereotype.Service;

import it.sd.lucrezia.ai.bean.ManageUserApprovalRequest;
import it.sd.lucrezia.ai.bean.ManageUserApprovalResponse;
import it.sd.lucrezia.ai.bean.RichiestaAssociazioneUtente;
import it.sd.lucrezia.ai.bean.Utente;
import it.sd.lucrezia.ai.dao.RichiestaAssociazioneUtenteDao;
import it.sd.lucrezia.ai.dao.TelefonataOutboundDao;
import it.sd.lucrezia.ai.dao.UtenteDao;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserApprovalService {

	private final UtenteDao utenteDao;
    private final RichiestaAssociazioneUtenteDao richiestaDao;
    private final TelefonataOutboundDao telefonataOutboundDao;

    public ManageUserApprovalResponse manageApproval(
            ManageUserApprovalRequest request) {

        ManageUserApprovalResponse response =
                new ManageUserApprovalResponse();

        if (request == null
                || request.getIdRichiestaAssociazione() == null) {

            response.setSuccess(false);
            response.setMessage(
                    "Identificativo richiesta non presente."
            );
            response.setNextAction("ERROR");

            return response;
        }

        String esito =
                request.getEsito() != null
                        ? request.getEsito().trim().toUpperCase()
                        : "";

        if (!"APPROVA".equals(esito)
                && !"RIFIUTA".equals(esito)) {

            response.setSuccess(false);
            response.setIdRichiestaAssociazione(
                    request.getIdRichiestaAssociazione()
            );
            response.setMessage(
                    "Esito non valido. Sono ammessi APPROVA oppure RIFIUTA."
            );
            response.setNextAction("ERROR");

            return response;
        }

        RichiestaAssociazioneUtente richiesta =
                richiestaDao.findById(
                        request.getIdRichiestaAssociazione()
                );

        if (richiesta == null) {

            response.setSuccess(false);
            response.setIdRichiestaAssociazione(
                    request.getIdRichiestaAssociazione()
            );
            response.setMessage(
                    "Richiesta di autorizzazione non trovata."
            );
            response.setNextAction("ERROR");

            return response;
        }

        /*
         * Idempotenza:
         * se è già stata gestita, non facciamo nulla.
         */
        if (!"IN_ATTESA".equals(
                richiesta.getStato())) {

            response.setSuccess(true);
            response.setIdRichiestaAssociazione(
                    richiesta.getId()
            );
            response.setStato(
                    richiesta.getStato()
            );
            response.setMessage(
                    "La richiesta risulta già gestita."
            );
            response.setNextAction(
                    "ALREADY_PROCESSED"
            );

            return response;
        }

        if ("APPROVA".equals(esito)) {

        	Utente utenteRegistrato =
        	        utenteDao.findById(
        	                richiesta.getIdUtenteRegistrato()
        	        );

        	if (utenteRegistrato == null) {

        	    response.setSuccess(false);
        	    response.setIdRichiestaAssociazione(
        	            richiesta.getId()
        	    );
        	    response.setMessage(
        	            "Utente registrato non trovato."
        	    );
        	    response.setNextAction(
        	            "ERROR"
        	    );

        	    return response;
        	}

        	Long idNuovoUtente;

        	try {

        	    idNuovoUtente =
        	            richiestaDao
        	                    .approvaERegistraNuovoUtente(
        	                            richiesta,
        	                            utenteRegistrato,
        	                            utenteDao
        	                    );

        	} catch (Exception e) {

        	    e.printStackTrace();

        	    response.setSuccess(false);
        	    response.setIdRichiestaAssociazione(
        	            richiesta.getId()
        	    );
        	    response.setMessage(
        	            "Non è stato possibile completare "
        	                    + "l'autorizzazione."
        	    );
        	    response.setNextAction(
        	            "ERROR"
        	    );

        	    return response;
        	}

        	if (idNuovoUtente == null) {

        	    response.setSuccess(false);
        	    response.setIdRichiestaAssociazione(
        	            richiesta.getId()
        	    );
        	    response.setMessage(
        	            "Non è stato possibile creare "
        	                    + "il nuovo utente."
        	    );
        	    response.setNextAction(
        	            "ERROR"
        	    );

        	    return response;
        	}

        	/*
        	 * Se la decisione è arrivata da WhatsApp
        	 * e l'outbound non è ancora partita,
        	 * annulliamo la chiamata.
        	 *
        	 * Se è già IN_CORSO non viene toccata.
        	 */
        	telefonataOutboundDao
        	        .annullaApprovazioneNonAvviata(
        	                richiesta.getId()
        	        );

        	response.setSuccess(true);

        	response.setIdRichiestaAssociazione(
        	        richiesta.getId()
        	);

        	response.setStato(
        	        "APPROVATA"
        	);

        	response.setMessage(
        	        "La richiesta di autorizzazione "
        	                + "è stata approvata."
        	);

        	response.setNextAction(
        	        "APPROVAL_CONFIRMED"
        	);

        	return response;
        }

        boolean updated =
                richiestaDao.rifiuta(
                        richiesta.getId()
                );

        if (!updated) {

            response.setSuccess(false);
            response.setIdRichiestaAssociazione(
                    richiesta.getId()
            );
            response.setMessage(
                    "Non è stato possibile rifiutare la richiesta."
            );
            response.setNextAction("ERROR");

            return response;
        }

        response.setSuccess(true);
        response.setIdRichiestaAssociazione(
                richiesta.getId()
        );
        response.setStato("RIFIUTATA");
        response.setMessage(
                "La richiesta di autorizzazione non è stata approvata."
        );
        response.setNextAction(
                "APPROVAL_REJECTED"
        );

        /*
         * Se la decisione è arrivata via WhatsApp
         * prima che partisse l'outbound,
         * questa la annulla.
         *
         * Se invece siamo già dentro la chiamata,
         * non succede nulla alla chiamata IN_CORSO.
         */
        telefonataOutboundDao
                .annullaApprovazioneNonAvviata(
                        richiesta.getId()
                );
        telefonataOutboundDao.completaApprovazioneUtente(
                richiesta.getId(),
                esito
        );
        
        return response;
    }
}