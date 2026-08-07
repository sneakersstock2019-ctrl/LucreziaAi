package it.sd.lucrezia.ai.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TicketConversazioneDao {

    private final DataSource dataSource;

    public void insertConversazione(
            Long idTicket,
            Long idUtente,
            String tipo,
            String contenuto) {

        String sql = """
            INSERT INTO whatsapp (
                id_ticket,
                id_utente,
                tipo,
                contenuto
            )
            VALUES (?, ?, ?, ?)
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setLong(1, idTicket);

            if (idUtente != null) {
                ps.setLong(2, idUtente);
            } else {
                ps.setNull(2, java.sql.Types.BIGINT);
            }

            ps.setString(3, tipo);
            ps.setString(4, contenuto);

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateAudioUrlByTicket(
            Long idTicket,
            String audioUrl) {

        String sql = """
            UPDATE ticket_conversazioni
            SET url_audio = ?
            WHERE id_ticket = ?
              AND canale = 'TELEFONO'
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, audioUrl);
            ps.setLong(2, idTicket);

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}