package it.sd.lucrezia.ai.dao;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import it.sd.lucrezia.ai.util.CallLogger;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TelefonataDao {

	private final DataSource dataSource;

	public Long insertTelefonata(String callSid, String telefono, Long idUtente, Long idCondominio) {

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

	public void chiudiTelefonata(Long idTelefonata,
			String esito,
			String trascrizione,
			long durataSecondi,
			int numeroInterruzioni,
			int numeroTool,
			String callSid) {

		if (idTelefonata == null) {
			return;
		}

		String sql = """
					UPDATE telefonata
					SET esito = COALESCE(?, esito),
					    motivo_chiusura = CASE
					        WHEN motivo_chiusura IS NULL
					          OR motivo_chiusura = ''
					          OR motivo_chiusura = 'IN_CORSO'
					        THEN 'CHIUSURA_UTENTE'
					        ELSE motivo_chiusura
					    END,
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
			ps.setString(2, trascrizione);
			ps.setLong(3, durataSecondi);
			ps.setInt(4, numeroInterruzioni);
			ps.setInt(5, numeroTool);
			ps.setLong(6, idTelefonata);

			int updated = ps.executeUpdate();
			CallLogger.info(callSid, "TELEFONATA CHIUDI - idTelefonata="
					+ idTelefonata
					+ " esito=" + esito
					+ " updated=" + updated);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void updateTicketByCallSid(String callSid, Long idTicket) {

		if (callSid == null || callSid.isBlank() || idTicket == null) {
			return;
		}

		String sql = """
				UPDATE telefonata
				SET id_ticket = ?,
				    esito = ?,
				    motivo_chiusura = ?
				WHERE call_sid = ?
				""";

		try (var conn = dataSource.getConnection();
				var ps = conn.prepareStatement(sql)) {

			ps.setLong(1, idTicket);
			ps.setString(2, "TICKET_APERTO");
			ps.setString(3, "TICKET_APERTO");
			ps.setString(4, callSid);

			int updated = ps.executeUpdate();

			CallLogger.info(callSid, "TELEFONATA UPDATE TICKET - callSid="
					+ callSid + " idTicket=" + idTicket + " updated=" + updated);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void updateElevenLabsConversationId(Long idTelefonata,
			String conversationId,
			String callSid) {

		if (idTelefonata == null || conversationId == null || conversationId.isBlank()) {
			return;
		}

		String sql = """
				UPDATE telefonata
				SET elevenlabs_conversation_id = ?
				WHERE id = ?
				""";

		try (var conn = dataSource.getConnection();
				var ps = conn.prepareStatement(sql)) {

			ps.setString(1, conversationId);
			ps.setLong(2, idTelefonata);

			int updated = ps.executeUpdate();

			CallLogger.info(callSid, "TELEFONATA UPDATE ELEVENLABS CONVERSATION - updated=" + updated);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void updateAudioByConversationId(String conversationId,
			String audioBase64,
			String audioUrl) {

		if (conversationId == null || conversationId.isBlank()) {
			return;
		}

		String sql = """
				UPDATE telefonata
				SET audio_base64 = ?,
				url_audio = ?
				WHERE elevenlabs_conversation_id = ?
				""";

		try (var conn = dataSource.getConnection();
				var ps = conn.prepareStatement(sql)) {

			ps.setString(1, audioBase64);
			ps.setString(2, audioUrl);
			ps.setString(3, conversationId);

			ps.executeUpdate();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String findAudioBase64ByConversationId(String conversationId) {

	    String sql = """
	            SELECT audio_base64
	            FROM telefonata
	            WHERE elevenlabs_conversation_id = ?
	            """;

	    try (var conn = dataSource.getConnection();
	         var ps = conn.prepareStatement(sql)) {

	        ps.setString(1, conversationId);

	        try (var rs = ps.executeQuery()) {
	            if (rs.next()) {
	                return rs.getString("audio_base64");
	            }
	        }

	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	    return null;
	}
	
	public void updateEsito(Long idTelefonata, String esito, String motivoChiusura, String callSid) {

		if (idTelefonata == null) {
			return;
		}

		String sql = """
				UPDATE telefonata
				SET esito = ?,
				motivo_chiusura = ?
				WHERE id = ?
				""";

		try (var conn = dataSource.getConnection();
				var ps = conn.prepareStatement(sql)) {

			ps.setString(1, esito);
			ps.setString(2, motivoChiusura);
			ps.setLong(3, idTelefonata);

			int updated = ps.executeUpdate();

			CallLogger.info(callSid, "TELEFONATA UPDATE ESITO - idTelefonata="
					+ idTelefonata + " esito=" + esito + " updated=" + updated);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}