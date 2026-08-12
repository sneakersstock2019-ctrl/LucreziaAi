package it.sd.lucrezia.ai.job;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.sd.lucrezia.ai.bean.ElevenLabsSipCallResult;
import it.sd.lucrezia.ai.bean.TelefonataOutbound;
import it.sd.lucrezia.ai.dao.RichiestaAssociazioneUtenteDao;
import it.sd.lucrezia.ai.dao.TelefonataOutboundDao;
import it.sd.lucrezia.ai.service.elevenlabs.ElevenLabsService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TelefonataOutboundJob {

    private final TelefonataOutboundDao telefonataOutboundDao;
    private final RichiestaAssociazioneUtenteDao richiestaAssociazioneUtenteDao;
    private final ElevenLabsService elevenLabsService;

    @Scheduled(
        fixedDelayString = "${voice.elevenlabs.job-call-outbound-delay-ms}"
    )
    public void processaChiamateOutbound() {

        System.out.println(
                "Avvio Job Chiamate Outbound"
        );

        List<TelefonataOutbound> chiamate;

        try {

            chiamate =
                    telefonataOutboundDao
                            .claimChiamateDaAvviare(5);

        } catch (Exception e) {

            System.err.println(
                    "Errore claim chiamate outbound: "
                            + e.getMessage()
            );

            e.printStackTrace();
            return;
        }

        for (TelefonataOutbound chiamata : chiamate) {
            processaSingolaChiamata(chiamata);
        }

        System.out.println(
                "Fine Job Chiamate Outbound ("
                        + chiamate.size()
                        + " chiamate elaborate)"
        );
    }

    private void processaSingolaChiamata(
            TelefonataOutbound chiamata) {

        try {

            System.out.println(
                    "Avvio chiamata outbound"
                            + " id=" + chiamata.getId()
                            + ", tipo="
                            + chiamata.getTipoChiamata()
                            + ", ticket="
                            + chiamata.getIdTicket()
                            + ", richiestaAssociazione="
                            + chiamata.getIdRichiestaAssociazione()
                            + ", destinatario="
                            + chiamata.getNominativoDestinatario()
                            + ", agentId="
                            + chiamata.getAgentId()
                            + ", tentativo="
                            + chiamata.getTentativi()
            );

            if (chiamata.getAgentId() == null
                    || chiamata.getAgentId().isBlank()) {

                throw new IllegalStateException(
                        "Agent ElevenLabs non configurato"
                );
            }

            if (chiamata.getAgentPhoneNumberId() == null
                    || chiamata.getAgentPhoneNumberId().isBlank()) {

                throw new IllegalStateException(
                        "Agent phone number ElevenLabs "
                        + "non configurato"
                );
            }

            String userId =
                    buildUserId(
                            chiamata
                    );

            if ("APPROVAZIONE_UTENTE".equals(
                    chiamata.getTipoChiamata())) {

                boolean ancoraInAttesa =
                        richiestaAssociazioneUtenteDao
                                .isInAttesa(
                                        chiamata.getIdRichiestaAssociazione()
                                );

                if (!ancoraInAttesa) {

                    telefonataOutboundDao
                            .annullaApprovazioneNonAvviata(
                                    chiamata.getIdRichiestaAssociazione()
                            );

                    System.out.println(
                            "Chiamata approvazione non eseguita: "
                                    + "richiesta già gestita. idRichiesta="
                                    + chiamata.getIdRichiestaAssociazione()
                    );

                    return;
                }
            }
            
            ElevenLabsSipCallResult result =
                    elevenLabsService.avviaChiamata(
                            chiamata.getTelefonoDestinatario(),
                            userId,
                            chiamata.getAgentId(),
                            chiamata.getAgentPhoneNumberId(),
                            chiamata.getDynamicVariables()
                    );

            telefonataOutboundDao.updateAvviata(
                    chiamata.getId(),
                    result.getConversationId(),
                    result.getSipCallId()
            );

            System.out.println(
                    "Chiamata outbound avviata"
                            + " id=" + chiamata.getId()
                            + ", tipo="
                            + chiamata.getTipoChiamata()
                            + ", conversationId="
                            + result.getConversationId()
            );

        } catch (Exception e) {

            System.err.println(
                    "Errore chiamata outbound id="
                            + chiamata.getId()
                            + ": "
                            + e.getMessage()
            );

            telefonataOutboundDao.programmaRetry(
                    chiamata.getId(),
                    e.getMessage(),
                    calcolaMinutiRetry(
                            chiamata.getTentativi()
                    )
            );
        }
    }

    private String buildUserId(
            TelefonataOutbound chiamata) {

        if ("FORNITORE".equals(
                chiamata.getTipoChiamata())) {

            return "fornitore-"
                    + chiamata.getIdFornitore();
        }

        if ("APPROVAZIONE_UTENTE".equals(
                chiamata.getTipoChiamata())) {

            return "approvazione-"
                    + chiamata.getIdRichiestaAssociazione();
        }

        /*
         * Fallback generico.
         */
        return "outbound-"
                + chiamata.getId();
    }

    private int calcolaMinutiRetry(
            int tentativo) {

        return switch (tentativo) {
            case 1 -> 1;
            case 2 -> 5;
            default -> 15;
        };
    }
}