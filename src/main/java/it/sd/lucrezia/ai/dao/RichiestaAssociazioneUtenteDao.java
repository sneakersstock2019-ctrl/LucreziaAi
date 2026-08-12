package it.sd.lucrezia.ai.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import it.sd.lucrezia.ai.bean.RichiestaAssociazioneUtente;
import it.sd.lucrezia.ai.bean.Utente;
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
    
    public Long approvaERegistraNuovoUtente(
            RichiestaAssociazioneUtente richiesta,
            Utente utenteRegistrato,
            UtenteDao utenteDao) {

        String sqlUpdateRichiesta = """
            UPDATE richieste_associazione_utente
            SET stato = 'APPROVATA',
                data_risposta = CURRENT_TIMESTAMP
            WHERE id = ?
              AND stato = 'IN_ATTESA'
            """;

        String sqlSbloccaTicket = """
            UPDATE ticket
            SET id_utente_apertura = ?,
                id_stato = (
                    SELECT id
                    FROM stati_ticket
                    WHERE codice = 'APERTO'
                ),
                data_ultimo_aggiornamento = CURRENT_TIMESTAMP
            WHERE id_richiesta_associazione = ?
              AND id_stato = (
                    SELECT id
                    FROM stati_ticket
                    WHERE codice = 'IN_ATTESA_APPROVAZIONE'
                )
            """;

        try (
                Connection conn = dataSource.getConnection()
        ) {

            conn.setAutoCommit(false);

            try {

                /*
                 * ========================================================
                 * 1. Verifica numero già registrato
                 * ========================================================
                 */

                Utente esistente =
                        utenteDao.findByTelefono(
                                conn,
                                richiesta.getTelefonoNuovo()
                        );

                if (esistente != null) {

                    /*
                     * Caso interessante:
                     * se il numero esiste già, non creiamo un duplicato.
                     *
                     * Per ora consideriamolo errore perché in un normale
                     * flusso di approvazione non dovrebbe succedere.
                     */
                    throw new IllegalStateException(
                            "Il numero "
                                    + richiesta.getTelefonoNuovo()
                                    + " risulta già registrato."
                    );
                }

                /*
                 * ========================================================
                 * 2. Creazione nuovo condomino
                 * ========================================================
                 *
                 * Il nuovo utente eredita l'interno dell'utente
                 * che lo ha autorizzato.
                 */

                Long idNuovoUtente =
                        utenteDao.insertCondomino(
                                conn,
                                richiesta.getNomeNuovo(),
                                richiesta.getCognomeNuovo(),
                                richiesta.getTelefonoNuovo(),
                                utenteRegistrato.getInterno()
                        );

                if (idNuovoUtente == null) {

                    throw new IllegalStateException(
                            "Impossibile creare il nuovo utente."
                    );
                }

                /*
                 * ========================================================
                 * 3. Associazione allo stesso condominio
                 * ========================================================
                 */

                utenteDao.associaUtenteCondominio(
                        conn,
                        idNuovoUtente,
                        richiesta.getIdCondominio()
                );

                /*
                 * ========================================================
                 * 4. Approvazione richiesta
                 * ========================================================
                 */

                try (
                        PreparedStatement ps =
                                conn.prepareStatement(
                                        sqlUpdateRichiesta
                                )
                ) {

                    ps.setLong(
                            1,
                            richiesta.getId()
                    );

                    int updated =
                            ps.executeUpdate();

                    if (updated != 1) {

                        throw new IllegalStateException(
                                "La richiesta non è più in attesa "
                                        + "oppure non esiste."
                        );
                    }
                }

                /*
                 * ========================================================
                 * 5. Sblocco eventuali ticket pending
                 * ========================================================
                 */

                int ticketSbloccati;

                try (
                        PreparedStatement ps =
                                conn.prepareStatement(
                                        sqlSbloccaTicket
                                )
                ) {

                    ps.setLong(
                            1,
                            idNuovoUtente
                    );

                    ps.setLong(
                            2,
                            richiesta.getId()
                    );

                    ticketSbloccati =
                            ps.executeUpdate();
                }

                System.out.println(
                        "APPROVAZIONE UTENTE"
                                + " - idRichiesta="
                                + richiesta.getId()
                                + " idNuovoUtente="
                                + idNuovoUtente
                                + " ticketSbloccati="
                                + ticketSbloccati
                );

                conn.commit();

                return idNuovoUtente;

            } catch (Exception e) {

                conn.rollback();

                throw e;

            } finally {

                conn.setAutoCommit(true);
            }

        } catch (Exception e) {

            throw new RuntimeException(
                    "Errore approvazione richiesta "
                            + richiesta.getId(),
                    e
            );
        }
    }
    
    public boolean isInAttesa(
            Long idRichiesta) {

        String sql = """
            SELECT 1
            FROM richieste_associazione_utente
            WHERE id = ?
              AND stato = 'IN_ATTESA'
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setLong(
                    1,
                    idRichiesta
            );

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {

            throw new RuntimeException(
                    "Errore verifica richiesta associazione "
                            + idRichiesta,
                    e
            );
        }
    }
    
    public RichiestaAssociazioneUtente findByIdTelefonata(
            Long idTelefonata) {

        if (idTelefonata == null) {
            return null;
        }

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
            WHERE id_telefonata = ?
            ORDER BY data_creazione DESC
            LIMIT 1
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setLong(
                    1,
                    idTelefonata
            );

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

                    richiesta.setIdTelefonata(
                            rs.getObject(
                                    "id_telefonata",
                                    Long.class
                            )
                    );

                    richiesta.setStato(
                            rs.getString("stato")
                    );

                    Timestamp dataCreazione =
                            rs.getTimestamp(
                                    "data_creazione"
                            );

                    if (dataCreazione != null) {
                        richiesta.setDataCreazione(
                                dataCreazione.toLocalDateTime()
                        );
                    }

                    Timestamp dataRisposta =
                            rs.getTimestamp(
                                    "data_risposta"
                            );

                    if (dataRisposta != null) {
                        richiesta.setDataRisposta(
                                dataRisposta.toLocalDateTime()
                        );
                    }

                    return richiesta;
                }
            }

        } catch (Exception e) {

            throw new RuntimeException(
                    "Errore ricerca richiesta associazione "
                            + "per idTelefonata="
                            + idTelefonata,
                    e
            );
        }

        return null;
    }
}