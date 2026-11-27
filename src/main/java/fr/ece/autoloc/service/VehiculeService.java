package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.web.dto.VehiculeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehiculeService {

    private final VehiculeRepository vehiculeRepository;
    private final AgenceRepository agenceRepository;
    private final ReservationRepository reservationRepository;

    public List<VehiculeDto.Response> findAll() {
        return vehiculeRepository.findAllByOrderByMarqueAscModeleAsc().stream()
                .map(VehiculeDto.Response::from)
                .toList();
    }

    public VehiculeDto.Response findById(Long id) {
        return VehiculeDto.Response.from(getVehicule(id));
    }

    @Transactional
    public VehiculeDto.Response create(VehiculeDto.Request request) {
        String immatriculation = normaliser(request.immatriculation());
        if (vehiculeRepository.existsByImmatriculation(immatriculation)) {
            throw new BusinessRuleException("L'immatriculation " + immatriculation + " existe déjà.");
        }
        Vehicule vehicule = new Vehicule();
        appliquer(vehicule, request, immatriculation);
        return VehiculeDto.Response.from(vehiculeRepository.save(vehicule));
    }

    @Transactional
    public VehiculeDto.Response update(Long id, VehiculeDto.Request request) {
        Vehicule vehicule = getVehicule(id);
        String immatriculation = normaliser(request.immatriculation());
        if (vehiculeRepository.existsByImmatriculationAndIdVehiculeNot(immatriculation, id)) {
            throw new BusinessRuleException("L'immatriculation " + immatriculation + " existe déjà.");
        }
        appliquer(vehicule, request, immatriculation);
        return VehiculeDto.Response.from(vehicule);
    }

    @Transactional
    public void delete(Long id) {
        Vehicule vehicule = getVehicule(id);
        if (reservationRepository.existsByVehicule_IdVehicule(id)) {
            throw new BusinessRuleException("Ce véhicule a des réservations : il ne peut pas être supprimé.");
        }
        if (!vehicule.getMaintenances().isEmpty()) {
            throw new BusinessRuleException("Ce véhicule a un historique de maintenance : il ne peut pas être supprimé.");
        }
        vehiculeRepository.delete(vehicule);
    }

    private Vehicule getVehicule(Long id) {
        return vehiculeRepository.findWithAgenceByIdVehicule(id)
                .orElseThrow(() -> new ResourceNotFoundException("Véhicule", id));
    }

    private void appliquer(Vehicule vehicule, VehiculeDto.Request request, String immatriculation) {
        vehicule.setImmatriculation(immatriculation);
        vehicule.setMarque(request.marque().trim());
        vehicule.setModele(request.modele().trim());
        vehicule.setCategorie(request.categorie());
        vehicule.setTarifJournalier(request.tarifJournalier());
        vehicule.setStatut(request.statut());
        vehicule.setAgence(request.agenceId() == null ? null
                : agenceRepository.findById(request.agenceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Agence", request.agenceId())));
    }

    private static String normaliser(String immatriculation) {
        return immatriculation.trim().toUpperCase(Locale.ROOT);
    }
}
