package it.sd.lucrezia.ai.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FornitoreOutboundToolDao {

    private final DataSource dataSource;

    public boolean programmaIntervento(
            Long idTelefonataOutbound,
            LocalDateTime dataIntervento,
            String nota) {

        String sqlLock = """
            SELECT
                tel.id,
                tel.id_ticket,
                tel.id_fornitore,
                tel.stato
            FROM telefonata_outbound tel
            WHERE tel.id = ?
            FOR UPDATE
        """;

        String sqlTicket = """
            UPDATE ticket
            SET data_intervento_prevista = ?,
                data_presa_in_carico =
                    COALESCE(data_presa_in_carico, CURRENT_TIMESTAMP),
                data_ultimo_aggiornamento = CURRENT_TIMESTAMP,
                id_stato = (
                    SELECT id
                    FROM stati_ticket
                    WHERE codice = 'IN_LAVORAZIONE'
                )
            WHERE id = ?
              AND id_fornitore = ?
        """;

        String sqlTelefonata = """
            UPDATE telefonata_outbound
            SET esito = 'INTERVENTO_PROGRAMMATO',
                motivo_chiusura = 'APPUNTAMENTO_CONFERMATO',
                data_programmata = ?,
                data_aggiornamento = CURRENT_TIMESTAMP,
                errore = NULL
            WHERE id = ?
        """;

        Connection conn = null;

        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            Long idTicket;
            Long idFornitore;

            try (PreparedStatement ps = conn.prepareStatement(sqlLock)) {

                ps.setLong(1, idTelefonataOutbound);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }

                    idTicket = getNullableLong(rs, "id_ticket");
                    idFornitore = getNullableLong(rs, "id_fornitore");

                    if (idTicket == null || idFornitore == null) {
                        throw new IllegalStateException(
                                "La telefonata outbound non è collegata "
                                + "a un ticket e a un fornitore"
                        );
                    }
                }
            }

            int ticketAggiornati;

            try (PreparedStatement ps =
                         conn.prepareStatement(sqlTicket)) {

                ps.setTimestamp(
                        1,
                        Timestamp.valueOf(dataIntervento)
                );

                ps.setLong(2, idTicket);
                ps.setLong(3, idFornitore);

                ticketAggiornati = ps.executeUpdate();
            }

            if (ticketAggiornati != 1) {
                conn.rollback();

                throw new IllegalStateException(
                        "Il ticket non risulta assegnato "
                        + "al fornitore della telefonata"
                );
            }

            try (PreparedStatement ps =
                         conn.prepareStatement(sqlTelefonata)) {

                ps.setTimestamp(
                        1,
                        Timestamp.valueOf(dataIntervento)
                );

                ps.setLong(2, idTelefonataOutbound);

                int telefonateAggiornate = ps.executeUpdate();

                if (telefonateAggiornate != 1) {
                    conn.rollback();

                    throw new IllegalStateException(
                            "Aggiornamento telefonata outbound non riuscito"
                    );
                }
            }

            /*
             * Per ora la nota viene validata e ricevuta dal tool,
             * ma non viene salvata perché non conosco ancora
             * la struttura esatta della tua tabella ticket_storico
             * o ticket_note.
             */

            conn.commit();
            return true;

        } catch (Exception e) {

            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackException) {
                    rollbackException.printStackTrace();
                }
            }

            throw new RuntimeException(
                    "Errore durante la programmazione "
                    + "dell'intervento dalla telefonata outbound "
                    + idTelefonataOutbound,
                    e
            );

        } finally {

            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (Exception closeException) {
                    closeException.printStackTrace();
                }
            }
        }
    }
    
    public boolean rifiutaAssegnazione(
            Long idTelefonataOutbound,
            String motivo) {

        String sqlLock = """
            SELECT
                tel.id_ticket,
                tel.id_fornitore
            FROM telefonata_outbound tel
            WHERE tel.id = ?
            FOR UPDATE
        """;

        String sqlTicket = """
            UPDATE ticket
            SET id_fornitore = NULL,
                id_stato = (
                    SELECT id
                    FROM stati_ticket
                    WHERE codice = 'APERTO'
                ),
                data_presa_in_carico = NULL,
                data_intervento_prevista = NULL,
                data_ultimo_aggiornamento = CURRENT_TIMESTAMP
            WHERE id = ?
              AND id_fornitore = ?
        """;

        String sqlTelefonata = """
            UPDATE telefonata_outbound
            SET esito = 'TICKET_RIFIUTATO',
                motivo_chiusura = ?,
                data_aggiornamento = CURRENT_TIMESTAMP,
                errore = NULL
            WHERE id = ?
        """;

        Connection conn = null;

        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            Long idTicket;
            Long idFornitore;

            try (PreparedStatement ps = conn.prepareStatement(sqlLock)) {

                ps.setLong(1, idTelefonataOutbound);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }

                    idTicket = getNullableLong(rs, "id_ticket");
                    idFornitore = getNullableLong(rs, "id_fornitore");

                    if (idTicket == null || idFornitore == null) {
                        throw new IllegalStateException(
                                "La telefonata outbound non è collegata "
                                        + "a un ticket e a un fornitore"
                        );
                    }
                }
            }

            int ticketAggiornati;

            try (PreparedStatement ps =
                         conn.prepareStatement(sqlTicket)) {

                ps.setLong(1, idTicket);
                ps.setLong(2, idFornitore);

                ticketAggiornati = ps.executeUpdate();
            }

            if (ticketAggiornati != 1) {
                conn.rollback();

                throw new IllegalStateException(
                        "Il ticket non risulta più assegnato "
                                + "al fornitore della telefonata"
                );
            }

            try (PreparedStatement ps =
                         conn.prepareStatement(sqlTelefonata)) {

                ps.setString(1, motivo);
                ps.setLong(2, idTelefonataOutbound);

                int telefonateAggiornate = ps.executeUpdate();

                if (telefonateAggiornate != 1) {
                    conn.rollback();

                    throw new IllegalStateException(
                            "Aggiornamento telefonata outbound non riuscito"
                    );
                }
            }

            conn.commit();
            return true;

        } catch (Exception e) {

            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackException) {
                    rollbackException.printStackTrace();
                }
            }

            throw new RuntimeException(
                    "Errore durante il rifiuto dell'assegnazione "
                            + "dalla telefonata outbound "
                            + idTelefonataOutbound,
                    e
            );

        } finally {

            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (Exception closeException) {
                    closeException.printStackTrace();
                }
            }
        }
    }

    private Long getNullableLong(
            ResultSet rs,
            String columnName) throws Exception {

        Object value = rs.getObject(columnName);

        return value == null
                ? null
                : rs.getLong(columnName);
    }
}