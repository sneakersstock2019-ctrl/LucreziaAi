package it.sd.demo.bot.condomini.dao;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TelefonataDao {

    private final DataSource dataSource;

    public Long insertTelefonata(String callSid,
                                 String telefono,
                                 Long idUtente,
                                 Long idCondominio) {

        String sql = """
            INSERT INTO telefonata (
                call_sid,
                telefono,
                id_utente,
                id_condominio,
                esito,
                motivo_chiusura,
                numero_interruzioni,
                numero_tool
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
        """;

        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {

            ps.setString(1, callSid);
            ps.setString(2, telefono);
            ps.setObject(3, idUtente);
            ps.setObject(4, idCondominio);
            ps.setString(5, "IN_CORSO");
            ps.setString(6, "IN_CORSO");
            ps.setInt(7, 0);
            ps.setInt(8, 0);

            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public void updateTicket(Long idTelefonata, Long idTicket) {

        if (idTelefonata == null || idTicket == null) {
            return;
        }

        String sql = """
            UPDATE telefonata
            SET id_ticket = ?,
                esito = ?,
                motivo_chiusura = ?
            WHERE id = ?
        """;

        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {

            ps.setLong(1, idTicket);
            ps.setString(2, "TICKET_APERTO");
            ps.setString(3, "TICKET_APERTO");
            ps.setLong(4, idTelefonata);

            int updated = ps.executeUpdate();
            System.out.println("TELEFONATA UPDATE TICKET - idTelefonata="
                    + idTelefonata + " idTicket=" + idTicket + " updated=" + updated);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateAudioUrl(Long idTelefonata, String urlAudio) {

        if (idTelefonata == null || urlAudio == null || urlAudio.isBlank()) {
            return;
        }

        String sql = """
            UPDATE telefonata
            SET url_audio = ?
            WHERE id = ?
        """;

        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {

            ps.setString(1, urlAudio);
            ps.setLong(2, idTelefonata);

            int updated = ps.executeUpdate();
            System.out.println("TELEFONATA UPDATE AUDIO - idTelefonata="
                    + idTelefonata + " updated=" + updated);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void chiudiTelefonata(Long idTelefonata,
                                 String esito,
                                 String motivoChiusura,
                                 String trascrizione,
                                 long durataSecondi,
                                 int numeroInterruzioni,
                                 int numeroTool) {

        if (idTelefonata == null) {
            return;
        }

        String sql = """
            UPDATE telefonata
            SET esito = COALESCE(?, esito),
                motivo_chiusura = COALESCE(?, motivo_chiusura),
                trascrizione = ?,
                data_fine = CURRENT_TIMESTAMP,
                durata_secondi = ?,
                numero_interruzioni = ?,
                numero_tool = ?
            WHERE id = ?
        """;

        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {

            ps.setString(1, esito);
            ps.setString(2, motivoChiusura);
            ps.setString(3, trascrizione);
            ps.setLong(4, durataSecondi);
            ps.setInt(5, numeroInterruzioni);
            ps.setInt(6, numeroTool);
            ps.setLong(7, idTelefonata);

            int updated = ps.executeUpdate();
            System.out.println("TELEFONATA CHIUDI - idTelefonata="
                    + idTelefonata
                    + " esito=" + esito
                    + " motivoChiusura=" + motivoChiusura
                    + " updated=" + updated);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}