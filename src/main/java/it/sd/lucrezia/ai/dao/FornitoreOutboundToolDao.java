package it.sd.lucrezia.ai.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.lucrezia.ai.bean.RetryOutboundResult;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FornitoreOutboundToolDao {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

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
    
    public Long programmaRichiamata(
            Long idTelefonataOutbound,
            LocalDateTime dataRichiamata,
            String nota) {

        String sqlLock = """
            SELECT
                id,
                id_ticket,
                id_fornitore,
                id_condominio,
                telefono_destinatario,
                nominativo_destinatario,
                agent_id,
                agent_phone_number_id,
                dynamic_variables
            FROM telefonata_outbound
            WHERE id = ?
            FOR UPDATE
        """;

        String sqlUpdateCorrente = """
            UPDATE telefonata_outbound
            SET esito = 'RICHIAMATA_RICHIESTA',
                motivo_chiusura = ?,
                data_aggiornamento = CURRENT_TIMESTAMP,
                errore = NULL
            WHERE id = ?
        """;

        String sqlInsertRichiamata = """
            INSERT INTO telefonata_outbound (
                tipo_chiamata,
                id_ticket,
                id_fornitore,
                id_condominio,
                id_telefonata_precedente,
                telefono_destinatario,
                nominativo_destinatario,
                agent_id,
                agent_phone_number_id,
                stato,
                data_programmata,
                tentativi,
                massimo_tentativi,
                dynamic_variables,
                data_aggiornamento
            )
            VALUES (
                'RICHIAMATA_FORNITORE',
                ?, ?, ?, ?, ?, ?, ?, ?,
                'RICHIAMATA_PROGRAMMATA',
                ?,
                0,
                3,
                ?,
                CURRENT_TIMESTAMP
            )
            RETURNING id
        """;

        Connection conn = null;

        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            Long idTicket;
            Long idFornitore;
            Long idCondominio;
            String telefono;
            String nominativo;
            String agentId;
            String agentPhoneNumberId;
            String dynamicVariablesJson;

            try (PreparedStatement ps = conn.prepareStatement(sqlLock)) {

                ps.setLong(1, idTelefonataOutbound);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        conn.rollback();
                        return null;
                    }

                    idTicket = getNullableLong(rs, "id_ticket");
                    idFornitore = getNullableLong(rs, "id_fornitore");
                    idCondominio = getNullableLong(rs, "id_condominio");

                    telefono = rs.getString("telefono_destinatario");
                    nominativo = rs.getString("nominativo_destinatario");
                    agentId = rs.getString("agent_id");
                    agentPhoneNumberId =
                            rs.getString("agent_phone_number_id");

                    dynamicVariablesJson =
                            rs.getString("dynamic_variables");

                    if (idTicket == null
                            || idFornitore == null
                            || telefono == null
                            || telefono.isBlank()) {

                        throw new IllegalStateException(
                                "La telefonata outbound non contiene "
                                        + "i dati necessari per la richiamata"
                        );
                    }
                }
            }

            String motivo = nota == null || nota.isBlank()
                    ? "Richiamata richiesta per "
                        + dataRichiamata
                    : nota;

            try (PreparedStatement ps =
                         conn.prepareStatement(sqlUpdateCorrente)) {

                ps.setString(1, motivo);
                ps.setLong(2, idTelefonataOutbound);

                if (ps.executeUpdate() != 1) {
                    throw new IllegalStateException(
                            "Aggiornamento telefonata corrente non riuscito"
                    );
                }
            }

            Long idRichiamata;

            try (PreparedStatement ps =
                         conn.prepareStatement(sqlInsertRichiamata)) {

                setNullableLong(ps, 1, idTicket);
                setNullableLong(ps, 2, idFornitore);
                setNullableLong(ps, 3, idCondominio);

                ps.setLong(4, idTelefonataOutbound);
                ps.setString(5, telefono);
                ps.setString(6, nominativo);
                ps.setString(7, agentId);
                ps.setString(8, agentPhoneNumberId);
                ps.setTimestamp(9, Timestamp.valueOf(dataRichiamata));

                ps.setObject(
                        10,
                        dynamicVariablesJson,
                        java.sql.Types.OTHER
                );

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        throw new IllegalStateException(
                                "Inserimento richiamata non riuscito"
                        );
                    }

                    idRichiamata = rs.getLong("id");
                }
            }
            
            Map<String, Object> dynamicVariables =
                    dynamicVariablesJson == null
                            || dynamicVariablesJson.isBlank()
                            ? new LinkedHashMap<>()
                            : objectMapper.readValue(
                                    dynamicVariablesJson,
                                    new TypeReference<
                                            Map<String, Object>
                                    >() {}
                            );

            dynamicVariables.put(
                    "telefonata_outbound_id",
                    idRichiamata
            );

            dynamicVariables.put(
                    "tipo_chiamata",
                    "RICHIAMATA_FORNITORE"
            );

            String sqlUpdateDynamic = """
                UPDATE telefonata_outbound
                SET dynamic_variables = ?,
                    data_aggiornamento = CURRENT_TIMESTAMP
                WHERE id = ?
            """;

            try (PreparedStatement ps =
                         conn.prepareStatement(sqlUpdateDynamic)) {

                ps.setObject(
                        1,
                        objectMapper.writeValueAsString(dynamicVariables),
                        java.sql.Types.OTHER
                );

                ps.setLong(2, idRichiamata);
                ps.executeUpdate();
            }

            conn.commit();
            return idRichiamata;

        } catch (Exception e) {

            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackException) {
                    rollbackException.printStackTrace();
                }
            }

            throw new RuntimeException(
                    "Errore durante la programmazione della richiamata "
                            + "per la telefonata outbound "
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
    
    public boolean selezionaGestioneDigitale(
            Long idTelefonataOutbound,
            String canale) {

        String esito = switch (canale.toUpperCase()) {
            case "WHATSAPP" -> "GESTIONE_WHATSAPP";
            case "DASHBOARD" -> "GESTIONE_DASHBOARD";
            default -> throw new IllegalArgumentException(
                    "Canale digitale non valido: " + canale
            );
        };

        String sql = """
            UPDATE telefonata_outbound
            SET esito = ?,
                motivo_chiusura = ?,
                errore = NULL,
                data_aggiornamento = CURRENT_TIMESTAMP
            WHERE id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, esito);
            ps.setString(2, canale.toUpperCase());
            ps.setLong(3, idTelefonataOutbound);

            return ps.executeUpdate() == 1;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore selezione gestione digitale per telefonata "
                            + idTelefonataOutbound,
                    e
            );
        }
    }
    
    public boolean chiudiTelefonataOutbound(
            Long idTelefonataOutbound,
            String motivoChiusura) {

        String sql = """
            UPDATE telefonata_outbound
            SET stato = 'COMPLETATA',
                esito = COALESCE(esito, 'NESSUNA_DECISIONE'),
                motivo_chiusura = CASE
                    WHEN motivo_chiusura IS NOT NULL
                         AND motivo_chiusura <> ''
                        THEN motivo_chiusura
                    ELSE ?
                END,
                data_fine = COALESCE(
                    data_fine,
                    CURRENT_TIMESTAMP
                ),
                data_aggiornamento = CURRENT_TIMESTAMP
            WHERE id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(
                    1,
                    motivoChiusura == null || motivoChiusura.isBlank()
                            ? "CHIUSURA_LUCREZIA"
                            : motivoChiusura
            );

            ps.setLong(2, idTelefonataOutbound);

            return ps.executeUpdate() == 1;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore chiusura telefonata outbound "
                            + idTelefonataOutbound,
                    e
            );
        }
    }
    
    public RetryOutboundResult programmaRetryMancataRisposta(
            Long idTelefonataOutbound,
            String motivo) {

        String sql = """
            UPDATE telefonata_outbound
            SET stato = CASE
                    WHEN tentativi >= massimo_tentativi
                        THEN 'FALLITA'
                    ELSE 'DA_AVVIARE'
                END,

                esito = CASE
                    WHEN tentativi >= massimo_tentativi
                        THEN 'FORNITORE_NON_RAGGIUNTO'
                    ELSE 'TENTATIVO_NON_RIUSCITO'
                END,

                motivo_chiusura = ?,

                prossimo_tentativo = CASE
                    WHEN tentativi >= massimo_tentativi
                        THEN NULL
                    WHEN tentativi = 1
                        THEN CURRENT_TIMESTAMP + INTERVAL '1 minutes'
                    WHEN tentativi = 2
                        THEN CURRENT_TIMESTAMP + INTERVAL '5 minutes'
                    ELSE CURRENT_TIMESTAMP + INTERVAL '180 minutes'
                END,

                data_presa_in_carico = NULL,

                data_fine = CASE
                    WHEN tentativi >= massimo_tentativi
                        THEN CURRENT_TIMESTAMP
                    ELSE NULL
                END,

                errore = NULL,
                data_aggiornamento = CURRENT_TIMESTAMP

            WHERE id = ?

            RETURNING
                stato,
                tentativi,
                massimo_tentativi,
                prossimo_tentativo
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(
                    1,
                    motivo == null || motivo.isBlank()
                            ? "Fornitore non raggiunto"
                            : motivo
            );

            ps.setLong(2, idTelefonataOutbound);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                RetryOutboundResult result =
                        new RetryOutboundResult();

                result.setStato(rs.getString("stato"));
                result.setTentativi(rs.getInt("tentativi"));
                result.setMassimoTentativi(
                        rs.getInt("massimo_tentativi")
                );

                Timestamp prossimoTentativo =
                        rs.getTimestamp("prossimo_tentativo");

                if (prossimoTentativo != null) {
                    result.setProssimoTentativo(
                            prossimoTentativo.toLocalDateTime()
                    );
                }

                return result;
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore programmazione retry della telefonata "
                            + idTelefonataOutbound,
                    e
            );
        }
    }
    
    private void setNullableLong(
            PreparedStatement ps,
            int index,
            Long value) throws SQLException {

        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, java.sql.Types.BIGINT);
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