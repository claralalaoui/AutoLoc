package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.web.dto.DashboardDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final VehiculeRepository vehiculeRepository;
    private final AgenceRepository agenceRepository;
    private final ClientRepository clientRepository;
    private final ReservationRepository reservationRepository;
    private final ContratRepository contratRepository;
    private final PaiementRepository paiementRepository;

    public DashboardDto resume() {
        return new DashboardDto(
                vehiculeRepository.count(),
                vehiculeRepository.countByStatut(StatutVehicule.DISPONIBLE),
                vehiculeRepository.countByStatut(StatutVehicule.LOUE),
                vehiculeRepository.countByStatut(StatutVehicule.MAINTENANCE),
                agenceRepository.count(),
                clientRepository.count(),
                reservationRepository.countByStatutIn(ReservationService.STATUTS_ACTIFS),
                contratRepository.count(),
                paiementRepository.totalEncaisse());
    }
}
