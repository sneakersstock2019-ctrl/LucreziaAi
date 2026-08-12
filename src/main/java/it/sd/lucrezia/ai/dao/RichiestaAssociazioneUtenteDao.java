package it.sd.lucrezia.ai.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import it.sd.lucrezia.ai.bean.RichiestaAssociazioneUtente;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RichiestaAssociazioneUtenteDao {

    private final DataSource dataSource;

    public Long insertRichiesta(
            String telefonoNuovo,
            String nomeNuovo,
            String cognomeNuovo,
            Long idUtenteRegistrato,
            Long idCondominio,
            Long idTelefonata) {

        String sql = """
            INSERT INTO richieste_associazione_utente (
                telefono_nuovo,
                nome_nuovo,
                cognome_nuovo,
                id_utente_registrato,
                id_condominio,
                id_telefonata,
                stato
            )
            VALUES (?, ?, ?, ?, ?, ?, 'IN_ATTESA')
            RETURNING id
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, telefonoNuovo);
            ps.setString(2, nomeNuovo);
            ps.setString(3, cognomeNuovo);
            ps.setLong(4, idUtenteRegistrato);
            ps.setLong(5, idCondominio);

            if (idTelefonata != null) {
                ps.setLong(6, idTelefonata);
            } else {
                ps.setNull(
                        6,
                        java.sql.Types.BIGINT
                );
            }

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getLong("id");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public Long findRichiestaPending(
            String telefonoNuovo,
            Long idUtenteRegistrato) {

        String sql = """
            SELECT id
            FROM richieste_associazione_utente
            WHERE telefono_nuovo = ?
              AND id_utente_registrato = ?
              AND stato = 'IN_ATTESA'
            ORDER BY data_creazione DESC
            LIMIT 1
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, telefonoNuovo);
            ps.setLong(2, idUtenteRegistrato);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getLong("id");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    
    public RichiestaAssociazioneUtente findById(
            Long idRichiesta) {

        String sql = """
            SELECT
                id,
                telefono_nuovo,
                nome_nuovo,
                cognome_nuovo,
                id_utente_registrato,
                id_condominio,
                id_telefonata,
                stato,
                data_creazione,
                data_risposta
            FROM richieste_associazione_utente
            WHERE id = ?
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setLong(1, idRichiesta);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    RichiestaAssociazioneUtente richiesta =
                            new RichiestaAssociazioneUtente();

                    richiesta.setId(
                            rs.getLong("id")
                    );

                    richiesta.setTelefonoNuovo(
                            rs.getString("telefono_nuovo")
                    );

                    richiesta.setNomeNuovo(
                            rs.getString("nome_nuovo")
                    );

                    richiesta.setCognomeNuovo(
                            rs.getString("cognome_nuovo")
                    );

                    richiesta.setIdUtenteRegistrato(
                            rs.getLong("id_utente_registrato")
                    );

                    richiesta.setIdCondominio(
                            rs.getLong("id_condominio")
                    );

                    Long idTelefonata =
                            rs.getObject(
                                    "id_telefonata",
                                    Long.class
                            );

                    richiesta.setIdTelefonata(
                            idTelefonata
                    );

                    richiesta.setStato(
                            rs.getString("stato")
                    );

                    if (rs.getTimestamp("data_creazione") != null) {
                        richiesta.setDataCreazione(
                                rs.getTimestamp("data_creazione")
                                        .toLocalDateTime()
                        );
                    }

                    if (rs.getTimestamp("data_risposta") != null) {
                        richiesta.setDataRisposta(
                                rs.getTimestamp("data_risposta")
                                        .toLocalDateTime()
                        );
                    }

                    return richiesta;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    
    public boolean approva(Long idRichiesta) {

        String sql = """
            UPDATE richieste_associazione_utente
            SET stato = 'APPROVATA',
                data_risposta = CURRENT_TIMESTAMP
            WHERE id = ?
              AND stato = 'IN_ATTESA'
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setLong(1, idRichiesta);

            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
    
    public boolean rifiuta(Long idRichiesta) {

        String sql = """
            UPDATE richieste_associazione_utente
            SET stato = 'RIFIUTATA',
                data_risposta = CURRENT_TIMESTAMP
            WHERE id = ?
              AND stato = 'IN_ATTESA'
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setLong(1, idRichiesta);

            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
}