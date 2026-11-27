package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.web.dto.ContratDto;
import fr.ece.autoloc.web.dto.ReservationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static fr.ece.autoloc.domain.StatutReservation.ANNULEE;
import static fr.ece.autoloc.domain.StatutReservation.CONFIRMEE;
import static fr.ece.autoloc.domain.StatutReservation.EN_ATTENTE;
import static fr.ece.autoloc.domain.StatutReservation.TERMINEE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    /** Reservations that block the vehicle for their period. */
    public static final Set<StatutReservation> STATUTS_ACTIFS = EnumSet.of(EN_ATTENTE, CONFIRMEE);

    private final ReservationRepository reservationRepository;
    private final ClientRepository clientRepository;
    private final VehiculeRepository vehiculeRepository;
    private final ContratRepository contratRepository;

    public List<ReservationDto.Response> findAll() {
        return reservationRepository.findAllByOrderByDateDebutDesc().stream()
                .map(ReservationDto.Response::from)
                .toList();
    }

    public ReservationDto.Response findById(Long id) {
        return ReservationDto.Response.from(getReservation(id));
    }

    /** Reservations of the customer linked to a login (customer space). */
    public List<ReservationDto.Response> findMine(String username) {
        return reservationRepository.findByClient_UsernameOrderByDateDebutDesc(username).stream()
                .map(ReservationDto.Response::from)
                .toList();
    }

    /** Reservation made by the agency for any client. */
    @Transactional
    public ReservationDto.Response create(ReservationDto.Request request) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client", request.clientId()));
        return creer(client, request.vehiculeId(), request.dateDebut(), request.dateFin());
    }

    /** Reservation request made by a customer from his own space. */
    @Transactional
    public ReservationDto.Response createForClient(String username, ReservationDto.ClientRequest request) {
        Client client = clientRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessRuleException("Complétez votre profil client avant de réserver."));
        if (request.dateDebut().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("La date de début ne peut pas être dans le passé.");
        }
        return creer(client, request.vehiculeId(), request.dateDebut(), request.dateFin());
    }

    /** A customer can only cancel his own pending requests. */
    @Transactional
    public ReservationDto.Response annulerMienne(String username, Long id) {
        Reservation reservation = getReservation(id);
        if (reservation.getClient() == null || !username.equals(reservation.getClient().getUsername())) {
            // Same answer as an unknown id: a customer must not learn that other reservations exist.
            throw new ResourceNotFoundException("Réservation", id);
        }
        if (reservation.getStatut() != EN_ATTENTE) {
            throw new BusinessRuleException("Seule une demande en attente peut être annulée. Pour le reste, contactez l'agence.");
        }
        reservation.setStatut(ANNULEE);
        return ReservationDto.Response.from(reservation);
    }

    @Transactional
    public ReservationDto.Response changerStatut(Long id, StatutReservation nouveau) {
        Reservation reservation = getReservation(id);
        StatutReservation actuel = reservation.getStatut();
        if (!transitionAutorisee(actuel, nouveau)) {
            throw new BusinessRuleException("Impossible de passer une réservation de " + actuel + " à " + nouveau + ".");
        }
        Vehicule vehicule = reservation.getVehicule();
        if (nouveau == CONFIRMEE) {
            if (vehicule.getStatut() == StatutVehicule.MAINTENANCE) {
                throw new BusinessRuleException("Le véhicule est en maintenance : la réservation ne peut pas être confirmée.");
            }
            vehicule.setStatut(StatutVehicule.LOUE);
        } else if (actuel == CONFIRMEE) {
            // TERMINEE or ANNULEE after a confirmation: the vehicle comes back.
            vehicule.setStatut(StatutVehicule.DISPONIBLE);
        }
        reservation.setStatut(nouveau);
        return ReservationDto.Response.from(reservation);
    }

    @Transactional
    public ContratDto.Response creerContrat(Long id) {
        Reservation reservation = getReservation(id);
        if (reservation.getStatut() != CONFIRMEE && reservation.getStatut() != TERMINEE) {
            throw new BusinessRuleException("Seule une réservation confirmée ou terminée peut avoir un contrat.");
        }
        if (reservation.getContrat() != null) {
            throw new BusinessRuleException("Cette réservation a déjà un contrat.");
        }
        long jours = ReservationDto.nombreJours(reservation.getDateDebut(), reservation.getDateFin());
        BigDecimal montant = reservation.getVehicule().getTarifJournalier().multiply(BigDecimal.valueOf(jours));

        Contrat contrat = new Contrat();
        contrat.setDateSignature(LocalDate.now());
        contrat.setMontantTotal(montant);
        contrat.setValide(true);
        contrat.setReservation(reservation);   // owning side: Contrat stores the foreign key
        reservation.setContrat(contrat);       // keep both sides consistent
        return ContratDto.Response.from(contratRepository.save(contrat));
    }

    @Transactional
    public void delete(Long id) {
        Reservation reservation = getReservation(id);
        if (reservation.getContrat() != null) {
            throw new BusinessRuleException("Cette réservation a un contrat : elle ne peut pas être supprimée.");
        }
        if (reservation.getStatut() == CONFIRMEE) {
            throw new BusinessRuleException("Annulez d'abord la réservation confirmée.");
        }
        reservationRepository.delete(reservation);
    }

    private ReservationDto.Response creer(Client client, Long vehiculeId, LocalDate debut, LocalDate fin) {
        if (fin.isBefore(debut)) {
            throw new BusinessRuleException("La date de fin doit être après la date de début.");
        }
        Vehicule vehicule = vehiculeRepository.findById(vehiculeId)
                .orElseThrow(() -> new ResourceNotFoundException("Véhicule", vehiculeId));
        if (vehicule.getStatut() == StatutVehicule.MAINTENANCE) {
            throw new BusinessRuleException("Ce véhicule est en maintenance.");
        }
        if (reservationRepository.existsOverlap(vehicule.getIdVehicule(), STATUTS_ACTIFS, debut, fin)) {
            throw new BusinessRuleException("Ce véhicule est déjà réservé sur cette période.");
        }

        Reservation reservation = new Reservation();
        reservation.setClient(client);
        reservation.setVehicule(vehicule);
        reservation.setDateDebut(debut);
        reservation.setDateFin(fin);
        reservation.setStatut(EN_ATTENTE);
        return ReservationDto.Response.from(reservationRepository.save(reservation));
    }

    private Reservation getReservation(Long id) {
        return reservationRepository.findWithDetailsByIdReservation(id)
                .orElseThrow(() -> new ResourceNotFoundException("Réservation", id));
    }

    private static boolean transitionAutorisee(StatutReservation actuel, StatutReservation nouveau) {
        return switch (actuel) {
            case EN_ATTENTE -> nouveau == CONFIRMEE || nouveau == ANNULEE;
            case CONFIRMEE -> nouveau == TERMINEE || nouveau == ANNULEE;
            case ANNULEE, TERMINEE -> false;
        };
    }
}
