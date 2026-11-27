package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculePhotoRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.web.dto.VehiculeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehiculeService {

    private final VehiculeRepository vehiculeRepository;
    private final AgenceRepository agenceRepository;
    private final ReservationRepository reservationRepository;
    private final VehiculePhotoRepository photoRepository;

    public List<VehiculeDto.Response> findAll() {
        Map<Long, LocalDateTime> photos = photoRepository.findAllInfos().stream()
                .collect(Collectors.toMap(VehiculePhotoRepository.PhotoInfo::getIdVehicule,
                        VehiculePhotoRepository.PhotoInfo::getMiseAJour));
        return vehiculeRepository.findAllByOrderByMarqueAscModeleAsc().stream()
                .map(v -> VehiculeDto.Response.from(v, photoUrl(v.getIdVehicule(), photos.get(v.getIdVehicule()))))
                .toList();
    }

    public VehiculeDto.Response findById(Long id) {
        return toResponse(getVehicule(id));
    }

    @Transactional
    public VehiculeDto.Response create(VehiculeDto.Request request) {
        String immatriculation = normaliser(request.immatriculation());
        if (vehiculeRepository.existsByImmatriculation(immatriculation)) {
            throw new BusinessRuleException("L'immatriculation " + immatriculation + " existe déjà.");
        }
        Vehicule vehicule = new Vehicule();
        appliquer(vehicule, request, immatriculation);
        return toResponse(vehiculeRepository.save(vehicule));
    }

    @Transactional
    public VehiculeDto.Response update(Long id, VehiculeDto.Request request) {
        Vehicule vehicule = getVehicule(id);
        String immatriculation = normaliser(request.immatriculation());
        if (vehiculeRepository.existsByImmatriculationAndIdVehiculeNot(immatriculation, id)) {
            throw new BusinessRuleException("L'immatriculation " + immatriculation + " existe déjà.");
        }
        appliquer(vehicule, request, immatriculation);
        return toResponse(vehicule);
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
        photoRepository.findById(id).ifPresent(photoRepository::delete);
        vehiculeRepository.delete(vehicule);
    }

    private VehiculeDto.Response toResponse(Vehicule vehicule) {
        LocalDateTime miseAJour = vehicule.getIdVehicule() == null ? null
                : photoRepository.findMiseAJour(vehicule.getIdVehicule()).orElse(null);
        return VehiculeDto.Response.from(vehicule, photoUrl(vehicule.getIdVehicule(), miseAJour));
    }

    /** The "v" parameter changes with each new photo, so browsers never show an old cached image. */
    private static String photoUrl(Long id, LocalDateTime miseAJour) {
        if (miseAJour == null) {
            return null;
        }
        return "/api/vehicules/" + id + "/photo?v=" + miseAJour.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
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
