package it.sd.lucrezia.ai.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import it.sd.lucrezia.ai.bean.TicketFornitoreCallData;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OutboundTicketDao {

    private final DataSource dataSource;

    public TicketFornitoreCallData loadOutboundData(
            Long idTicket,
            Long idFornitore) {

        String sql = """
            SELECT
                t.id AS id_ticket,
                t.categoria,
                t.priorita,
                t.descrizione,
                t.data_apertura,

                f.id AS id_fornitore,
                f.nome || ' ' || COALESCE(f.cognome, '') AS nome_fornitore,
                f.telefono AS telefono_fornitore,

                c.id AS id_condominio,
                c.nome AS condominio,
                c.indirizzo AS indirizzo_condominio,

                u.id AS id_condomino,
                u.nome || ' ' || COALESCE(u.cognome, '') AS nome_condomino,
                u.telefono AS telefono_condomino

            FROM ticket t

            JOIN utenti f
                ON f.id = t.id_fornitore

            JOIN condomini c
                ON c.id = t.id_condominio

            JOIN utenti u
                ON u.id = t.id_utente_apertura

            WHERE t.id = ?
              AND f.id = ?
        """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, idTicket);
            ps.setLong(2, idFornitore);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                TicketFornitoreCallData data =
                        new TicketFornitoreCallData();

                data.setIdTicket(rs.getLong("id_ticket"));
                data.setCategoria(rs.getString("categoria"));
                data.setPriorita(rs.getString("priorita"));
                data.setDescrizione(rs.getString("descrizione"));

                Timestamp dataApertura =
                        rs.getTimestamp("data_apertura");

                if (dataApertura != null) {
                    data.setDataApertura(
                            dataApertura.toLocalDateTime()
                    );
                }

                data.setIdFornitore(rs.getLong("id_fornitore"));
                data.setNomeFornitore(
                        rs.getString("nome_fornitore")
                );
                data.setTelefonoFornitore(
                        rs.getString("telefono_fornitore")
                );

                data.setIdCondominio(
                        rs.getLong("id_condominio")
                );
                data.setCondominio(rs.getString("condominio"));
                data.setIndirizzoCondominio(
                        rs.getString("indirizzo_condominio")
                );

                data.setIdCondomino(rs.getLong("id_condomino"));
                data.setNomeCondomino(
                        rs.getString("nome_condomino")
                );
                data.setTelefonoCondomino(
                        rs.getString("telefono_condomino")
                );

                return data;
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Errore caricamento dati outbound del ticket "
                            + idTicket,
                    e
            );
        }
    }
}