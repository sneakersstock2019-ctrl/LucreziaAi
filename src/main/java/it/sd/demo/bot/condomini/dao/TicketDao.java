package it.sd.demo.bot.condomini.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import it.sd.demo.bot.condomini.bean.TicketStatusInfo;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TicketDao {

    private final DataSource dataSource;

    public boolean hasTicketApertiByUtente(Long idUtente) {

        String sql = """
            SELECT COUNT(*)
            FROM ticket t
            JOIN stati_ticket st ON st.id = t.id_stato
            WHERE t.id_utente_apertura = ?
              AND st.codice NOT IN ('RISOLTO', 'CHIUSO')
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, idUtente);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public List<TicketStatusInfo> findOpenTicketsByUtente(Long idUtente) {

        List<TicketStatusInfo> result = new ArrayList<>();

        String sql = """
			SELECT
			    t.id,
			    t.categoria,
			    t.priorita,
			    t.descrizione,
			    t.data_ultimo_aggiornamento,
			    t.data_intervento_prevista,
			    st.codice AS stato_codice,
			    st.descrizione AS stato_descrizione,
			    f.nome AS nome_fornitore
			FROM ticket t
			JOIN stati_ticket st ON st.id = t.id_stato
			LEFT JOIN utenti f ON f.id = t.id_fornitore
			WHERE t.id_utente_apertura = ?
			  AND st.codice NOT IN ('RISOLTO', 'CHIUSO')
			ORDER BY t.data_ultimo_aggiornamento DESC
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, idUtente);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapTicketStatusInfo(rs));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public TicketStatusInfo findTicketStatusById(Long idTicket) {

        String sql = """
            SELECT
			    t.id,
			    t.categoria,
			    t.priorita,
			    t.descrizione,
			    t.data_ultimo_aggiornamento,
			    t.data_intervento_prevista,
			    st.codice AS stato_codice,
			    st.descrizione AS stato_descrizione,
			    f.nome AS nome_fornitore
            FROM ticket t
            JOIN stati_ticket st ON st.id = t.id_stato
            WHERE t.id = ?
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, idTicket);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapTicketStatusInfo(rs);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private TicketStatusInfo mapTicketStatusInfo(ResultSet rs) throws Exception {

        TicketStatusInfo info = new TicketStatusInfo();

        info.setId(rs.getLong("id"));
        info.setCategoria(rs.getString("categoria"));
        info.setPriorita(rs.getString("priorita"));
        info.setDescrizione(rs.getString("descrizione"));
        info.setStatoCodice(rs.getString("stato_codice"));
        info.setStatoDescrizione(rs.getString("stato_descrizione"));

        Timestamp ts = rs.getTimestamp("data_ultimo_aggiornamento");
        if (ts != null) {
            info.setDataUltimoAggiornamento(ts.toLocalDateTime());
        }
        
        info.setNomeFornitore(rs.getString("nome_fornitore"));

        Timestamp tsIntervento = rs.getTimestamp("data_intervento_prevista");
        if (tsIntervento != null) {
            info.setDataInterventoPrevista(tsIntervento.toLocalDateTime());
        }

        return info;
    }

    public Long insertTicket(Long idCondominio,
                             Long idUtenteApertura,
                             String categoria,
                             String priorita,
                             String canale,
                             String descrizione) {

        String sql = """
            INSERT INTO ticket (
                id_condominio,
                id_utente_apertura,
                id_stato,
                categoria,
                priorita,
                canale,
                descrizione,
                data_ultimo_aggiornamento
            )
            VALUES (
                ?,
                ?,
                (SELECT id FROM stati_ticket WHERE codice = 'APERTO'),
                ?,
                ?,
                ?,
                ?,
                CURRENT_TIMESTAMP
            )
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            ps.setLong(1, idCondominio);
            ps.setLong(2, idUtenteApertura);
            ps.setString(3, categoria);
            ps.setString(4, priorita);
            ps.setString(5, canale);
            ps.setString(6, descrizione);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Long idTicket = rs.getLong(1);
                    insertStorico(conn, idTicket, "APERTO", idUtenteApertura, "Ticket aperto da " + canale);
                    return idTicket;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private void insertStorico(Connection conn,
                               Long idTicket,
                               String codiceStato,
                               Long idUtente,
                               String nota) throws Exception {

        String sql = """
            INSERT INTO ticket_storico (
                id_ticket,
                id_stato,
                id_utente,
                nota
            )
            VALUES (
                ?,
                (SELECT id FROM stati_ticket WHERE codice = ?),
                ?,
                ?
            )
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idTicket);
            ps.setString(2, codiceStato);

            if (idUtente != null) {
                ps.setLong(3, idUtente);
            } else {
                ps.setNull(3, java.sql.Types.BIGINT);
            }

            ps.setString(4, nota);
            ps.executeUpdate();
        }
    }
}