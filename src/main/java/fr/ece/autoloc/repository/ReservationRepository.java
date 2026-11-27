package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @EntityGraph(attributePaths = {"client", "vehicule", "contrat"})
    List<Reservation> findAllByOrderByDateDebutDesc();

    @EntityGraph(attributePaths = {"client", "vehicule", "contrat"})
    Optional<Reservation> findWithDetailsByIdReservation(Long idReservation);

    @EntityGraph(attributePaths = {"client", "vehicule", "contrat"})
    List<Reservation> findByClient_UsernameOrderByDateDebutDesc(String username);

    boolean existsByVehicule_IdVehicule(Long idVehicule);

    long countByStatutIn(Collection<StatutReservation> statuts);

    /**
     * True when the vehicle already has an active reservation that overlaps [debut, fin].
     */
    @Query("""
            select case when count(r) > 0 then true else false end
            from Reservation r
            where r.vehicule.idVehicule = :vehiculeId
              and r.statut in :statuts
              and r.dateDebut <= :fin
              and r.dateFin >= :debut
            """)
    boolean existsOverlap(@Param("vehiculeId") Long vehiculeId,
                          @Param("statuts") Collection<StatutReservation> statuts,
                          @Param("debut") LocalDate debut,
                          @Param("fin") LocalDate fin);
}
