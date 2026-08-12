package it.sd.lucrezia.ai.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.postgresql.util.PGobject;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
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
                id_richiesta_associazione,
                id_utente_destinatario,
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
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
            )
            RETURNING id
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(
                    1,
                    telefonata.getTipoChiamata()
            );

            setNullableLong(
                    ps,
                    2,
                    telefonata.getIdTicket()
            );

            setNullableLong(
                    ps,
                    3,
                    telefonata.getIdFornitore()
            );

            setNullableLong(
                    ps,
                    4,
                    telefonata.getIdCondominio()
            );

            setNullableLong(
                    ps,
                    5,
                    telefonata.getIdTelefonataPrecedente()
            );

            setNullableLong(
                    ps,
                    6,
                    telefonata.getIdRichiestaAssociazione()
            );

            setNullableLong(
                    ps,
                    7,
                    telefonata.getIdUtenteDestinatario()
            );

            ps.setString(
                    8,
                    telefonata.getTelefonoDestinatario()
            );

            ps.setString(
                    9,
                    telefonata.getNominativoDestinatario()
            );

            ps.setString(
                    10,
                    telefonata.getAgentId()
            );

            ps.setString(
                    11,
                    telefonata.getAgentPhoneNumberId()
            );

            ps.setString(
                    12,
                    telefonata.getStato()
            );

            if (telefonata.getDataProgrammata() != null) {

                ps.setTimestamp(
                        13,
                        Timestamp.valueOf(
                                telefonata.getDataProgrammata()
                        )
                );

            } else {

                ps.setNull(
                        13,
                        java.sql.Types.TIMESTAMP
                );
            }

            ps.setObject(
                    14,
                    toJsonb(
                            telefonata.getDynamicVariables()
                    )
            );

            if (telefonata.getDataAvvio() != null) {

                ps.setTimestamp(
                        15,
                        Timestamp.valueOf(
                                telefonata.getDataAvvio()
                        )
                );

            } else {

                ps.setNull(
                        15,
                        java.sql.Types.TIMESTAMP
                );
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
    
    public List<TelefonataOutbound> claimChiamateDaAvviare(
            int limite) {

        List<TelefonataOutbound> lista = new ArrayList<>();

        String sql = """
            WITH chiamate_da_prendere AS (
                SELECT id
                FROM telefonata_outbound
                WHERE stato IN (
                    'DA_AVVIARE',
                    'RICHIAMATA_PROGRAMMATA'
                )
                  AND COALESCE(
                        data_programmata,
                        CURRENT_TIMESTAMP
                      ) <= CURRENT_TIMESTAMP
                  AND COALESCE(
                        prossimo_tentativo,
                        CURRENT_TIMESTAMP
                      ) <= CURRENT_TIMESTAMP
                  AND tentativi < massimo_tentativi
                ORDER BY
                    COALESCE(
                        data_programmata,
                        data_richiesta
                    ),
                    id
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE telefonata_outbound t
            SET stato = 'IN_AVVIO',
                data_presa_in_carico = CURRENT_TIMESTAMP,
                tentativi = tentativi + 1,
                data_aggiornamento = CURRENT_TIMESTAMP
            FROM chiamate_da_prendere c
            WHERE t.id = c.id
            RETURNING
			    t.id,
			    t.tipo_chiamata,
			    t.id_ticket,
			    t.id_fornitore,
			    t.id_condominio,
			    t.id_telefonata_precedente,
			    t.id_richiesta_associazione,
			    t.id_utente_destinatario,
			    t.telefono_destinatario,
			    t.nominativo_destinatario,
			    t.agent_id,
			    t.agent_phone_number_id,
			    t.stato,
			    t.data_programmata,
			    t.tentativi,
			    t.massimo_tentativi,
			    t.dynamic_variables
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setInt(1, limite);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    TelefonataOutbound t =
                            new TelefonataOutbound();

                    t.setId(rs.getLong("id"));
                    t.setTipoChiamata(
                            rs.getString("tipo_chiamata")
                    );

                    t.setIdTicket(
                            getNullableLong(rs, "id_ticket")
                    );

                    t.setIdFornitore(
                            getNullableLong(rs, "id_fornitore")
                    );

                    t.setIdCondominio(
                            getNullableLong(rs, "id_condominio")
                    );

                    t.setIdTelefonataPrecedente(
                            getNullableLong(
                                    rs,
                                    "id_telefonata_precedente"
                            )
                    );

                    t.setIdRichiestaAssociazione(
                            getNullableLong(
                                    rs,
                                    "id_richiesta_associazione"
                            )
                    );

                    t.setIdUtenteDestinatario(
                            getNullableLong(
                                    rs,
                                    "id_utente_destinatario"
                            )
                    );
                    
                    t.setTelefonoDestinatario(
                            rs.getString("telefono_destinatario")
                    );

                    t.setNominativoDestinatario(
                            rs.getString("nominativo_destinatario")
                    );

                    t.setAgentId(rs.getString("agent_id"));

                    t.setAgentPhoneNumberId(
                            rs.getString(
                                    "agent_phone_number_id"
                            )
                    );

                    t.setStato(rs.getString("stato"));
                    t.setTentativi(rs.getInt("tentativi"));

                    t.setMassimoTentativi(
                            rs.getInt("massimo_tentativi")
                    );

                    Timestamp programmata =
                            rs.getTimestamp("data_programmata");

                    if (programmata != null) {
                        t.setDataProgrammata(
                                programmata.toLocalDateTime()
                        );
                    }

                    String json =
                            rs.getString("dynamic_variables");

                    if (json != null && !json.isBlank()) {
                        t.setDynamicVariables(
                                objectMapper.readValue(
                                        json,
                                        new TypeReference<
                                                Map<String, Object>
                                        >() {}
                                )
                        );
                    }

                    lista.add(t);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore claim chiamate outbound",
                    e
            );
        }

        return lista;
    }
    
    public void programmaRetry(
            Long id,
            String errore,
            int minutiAttesa) {

        String sql = """
            UPDATE telefonata_outbound
            SET stato = CASE
                    WHEN tentativi >= massimo_tentativi
                        THEN 'FALLITA'
                    ELSE 'DA_AVVIARE'
                END,
                errore = ?,
                prossimo_tentativo = CASE
                    WHEN tentativi >= massimo_tentativi
                        THEN NULL
                    ELSE CURRENT_TIMESTAMP
                         + (? * INTERVAL '1 minute')
                END,
                data_fine = CASE
                    WHEN tentativi >= massimo_tentativi
                        THEN CURRENT_TIMESTAMP
                    ELSE NULL
                END,
                data_aggiornamento = CURRENT_TIMESTAMP
            WHERE id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, truncate(errore, 10000));
            ps.setInt(2, minutiAttesa);
            ps.setLong(3, id);

            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore programmazione retry outbound " + id,
                    e
            );
        }
    }
    
    public Long findIdByConversationId(String conversationId) {

        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }

        String sql = """
            SELECT id
            FROM telefonata_outbound
            WHERE conversation_id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, conversationId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getLong("id");
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore ricerca telefonata outbound per conversationId="
                            + conversationId,
                    e
            );
        }

        return null;
    }
    
    public void completaPostCall(
            Long idTelefonataOutbound,
            String conversationId,
            String trascrizione,
            long durataSecondi,
            int numeroTool) {

        if (idTelefonataOutbound == null) {
            return;
        }

        String sql = """
            UPDATE telefonata_outbound
            SET conversation_id = COALESCE(conversation_id, ?),

                trascrizione = ?,

                durata_secondi = ?,

                numero_tool = ?,

                /*
                 * Il post-call chiude tecnicamente solo una chiamata
                 * che non è già stata gestita da tool/retry.
                 */
                stato = CASE

                    WHEN stato IN (
                        'FALLITA',
                        'ANNULLATA',
                        'DA_AVVIARE',
                        'RICHIAMATA_PROGRAMMATA'
                    )
                        THEN stato

                    ELSE 'COMPLETATA'

                END,

                /*
                 * data_fine rappresenta la conclusione definitiva
                 * dell'intero processo outbound.
                 *
                 * Se è previsto un retry, resta NULL.
                 */
                data_fine = CASE

                    WHEN stato IN (
                        'DA_AVVIARE',
                        'RICHIAMATA_PROGRAMMATA'
                    )
                        THEN NULL

                    ELSE COALESCE(
                        data_fine,
                        CURRENT_TIMESTAMP
                    )

                END,

                /*
                 * Non sovrascriviamo mai un esito deciso da un tool.
                 */
                esito = COALESCE(
                    esito,
                    'NESSUNA_DECISIONE'
                ),

                data_aggiornamento = CURRENT_TIMESTAMP

            WHERE id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, conversationId);
            ps.setString(2, trascrizione);
            ps.setLong(3, durataSecondi);
            ps.setInt(4, numeroTool);
            ps.setLong(5, idTelefonataOutbound);

            int updated = ps.executeUpdate();

            System.out.println(
                    "OUTBOUND POST-CALL UPDATE"
                            + " id=" + idTelefonataOutbound
                            + " conversationId=" + conversationId
                            + " updated=" + updated
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore completamento post-call outbound "
                            + idTelefonataOutbound,
                    e
            );
        }
    }
    
    public void updateAudioByConversationId(
            String conversationId,
            String audioBase64,
            String audioUrl) {

        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        String sql = """
            UPDATE telefonata_outbound
            SET audio_base64 = ?,
                url_audio = ?,
                data_aggiornamento = CURRENT_TIMESTAMP
            WHERE conversation_id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, audioBase64);
            ps.setString(2, audioUrl);
            ps.setString(3, conversationId);

            int updated = ps.executeUpdate();

            System.out.println(
                    "OUTBOUND AUDIO UPDATE"
                            + " conversationId=" + conversationId
                            + " updated=" + updated
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore salvataggio audio outbound conversationId="
                            + conversationId,
                    e
            );
        }
    }
    
    public String findAudioBase64ByConversationId(
            String conversationId) {

        String sql = """
            SELECT audio_base64
            FROM telefonata_outbound
            WHERE conversation_id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, conversationId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getString("audio_base64");
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore lettura audio outbound conversationId="
                            + conversationId,
                    e
            );
        }

        return null;
    }
    
    private Long getNullableLong(
            ResultSet rs,
            String columnName) throws SQLException {

        Object value = rs.getObject(columnName);

        return value != null
                ? rs.getLong(columnName)
                : null;
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