package it.sd.lucrezia.ai.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import javax.sql.DataSource;

import org.postgresql.util.PGobject;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.sd.lucrezia.ai.bean.TelefonataOutbound;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TelefonataOutboundDao {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public Long insert(TelefonataOutbound telefonata) {

        String sql = """
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
                dynamic_variables,
                data_avvio,
                data_aggiornamento
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
            )
            RETURNING id
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, telefonata.getTipoChiamata());
            setNullableLong(ps, 2, telefonata.getIdTicket());
            setNullableLong(ps, 3, telefonata.getIdFornitore());
            setNullableLong(ps, 4, telefonata.getIdCondominio());
            setNullableLong(
                    ps,
                    5,
                    telefonata.getIdTelefonataPrecedente()
            );

            ps.setString(
                    6,
                    telefonata.getTelefonoDestinatario()
            );

            ps.setString(
                    7,
                    telefonata.getNominativoDestinatario()
            );

            ps.setString(8, telefonata.getAgentId());
            ps.setString(9, telefonata.getAgentPhoneNumberId());
            ps.setString(10, telefonata.getStato());

            if (telefonata.getDataProgrammata() != null) {
                ps.setTimestamp(
                        11,
                        Timestamp.valueOf(
                                telefonata.getDataProgrammata()
                        )
                );
            } else {
                ps.setNull(11, java.sql.Types.TIMESTAMP);
            }

            ps.setObject(
                    12,
                    toJsonb(telefonata.getDynamicVariables())
            );

            if (telefonata.getDataAvvio() != null) {
                ps.setTimestamp(
                        13,
                        Timestamp.valueOf(telefonata.getDataAvvio())
                );
            } else {
                ps.setNull(13, java.sql.Types.TIMESTAMP);
            }

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    throw new IllegalStateException(
                            "Inserimento telefonata outbound non riuscito"
                    );
                }

                return rs.getLong("id");
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore inserimento telefonata outbound",
                    e
            );
        }
    }

    public void updateAvviata(
            Long id,
            String conversationId,
            String sipCallId) {

        String sql = """
            UPDATE telefonata_outbound
            SET conversation_id = ?,
                sip_call_id = ?,
                stato = 'IN_CORSO',
                data_avvio = COALESCE(
                    data_avvio,
                    CURRENT_TIMESTAMP
                ),
                errore = NULL,
                data_aggiornamento = CURRENT_TIMESTAMP
            WHERE id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, conversationId);
            ps.setString(2, sipCallId);
            ps.setLong(3, id);

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore aggiornamento telefonata outbound " + id,
                    e
            );
        }
    }

    public void updateErrore(Long id, String errore) {

        String sql = """
            UPDATE telefonata_outbound
            SET stato = 'FALLITA',
                errore = ?,
                data_fine = CURRENT_TIMESTAMP,
                data_aggiornamento = CURRENT_TIMESTAMP
            WHERE id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, truncate(errore, 10000));
            ps.setLong(2, id);

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore salvataggio fallimento outbound " + id,
                    e
            );
        }
    }

    public void updateStato(Long id, String stato) {

        String sql = """
            UPDATE telefonata_outbound
            SET stato = ?,
                data_aggiornamento = CURRENT_TIMESTAMP
            WHERE id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, stato);
            ps.setLong(2, id);

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore aggiornamento stato outbound " + id,
                    e
            );
        }
    }
    
    public void updateDynamicVariables(
            Long id,
            Object dynamicVariables) {

        String sql = """
            UPDATE telefonata_outbound
            SET dynamic_variables = ?,
                data_aggiornamento = CURRENT_TIMESTAMP
            WHERE id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setObject(1, toJsonb(dynamicVariables));
            ps.setLong(2, id);

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore aggiornamento dynamic variables outbound "
                            + id,
                    e
            );
        }
    }

    private PGobject toJsonb(Object value) throws Exception {

        PGobject json = new PGobject();
        json.setType("jsonb");
        json.setValue(
                objectMapper.writeValueAsString(value)
        );

        return json;
    }

    private void setNullableLong(
            PreparedStatement ps,
            int index,
            Long value) throws Exception {

        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, java.sql.Types.BIGINT);
        }
    }

    private String truncate(String value, int maxLength) {

        if (value == null) {
            return null;
        }

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}