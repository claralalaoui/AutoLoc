package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.web.dto.AgenceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgenceService {

    private final AgenceRepository agenceRepository;

    public List<AgenceDto.Response> findAll() {
        return agenceRepository.findAllByOrderByNomAsc().stream()
                .map(AgenceDto.Response::from)
                .toList();
    }

    public AgenceDto.Response findById(Long id) {
        return AgenceDto.Response.from(getAgence(id));
    }

    @Transactional
    public AgenceDto.Response create(AgenceDto.Request request) {
        Agence agence = new Agence();
        appliquer(agence, request);
        return AgenceDto.Response.from(agenceRepository.save(agence));
    }

    @Transactional
    public AgenceDto.Response update(Long id, AgenceDto.Request request) {
        Agence agence = getAgence(id);
        appliquer(agence, request);
        return AgenceDto.Response.from(agence);
    }

    @Transactional
    public void delete(Long id) {
        Agence agence = getAgence(id);
        // No cascade on Agence -> Vehicule / Employe: they survive the agency, so we refuse instead.
        if (!agence.getVehicules().isEmpty() || !agence.getEmployes().isEmpty()) {
            throw new BusinessRuleException("Cette agence a encore des véhicules ou des employés : réaffectez-les avant de la supprimer.");
        }
        agenceRepository.delete(agence);
    }

    private Agence getAgence(Long id) {
        return agenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agence", id));
    }

    private static void appliquer(Agence agence, AgenceDto.Request request) {
        agence.setNom(request.nom().trim());
        agence.setVille(request.ville().trim());
        agence.setAdresse(request.adresse().trim());
        agence.setTelephone(request.telephone());
    }
}
