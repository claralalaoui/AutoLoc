package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.domain.VehiculePhoto;
import fr.ece.autoloc.repository.VehiculePhotoRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehiculePhotoService {

    public static final int TAILLE_MAX = 5 * 1024 * 1024;

    private final VehiculePhotoRepository photoRepository;
    private final VehiculeRepository vehiculeRepository;

    public VehiculePhoto get(Long idVehicule) {
        return photoRepository.findById(idVehicule)
                .orElseThrow(() -> new ResourceNotFoundException("Ce véhicule n'a pas de photo."));
    }

    @Transactional
    public void enregistrer(Long idVehicule, byte[] donnees) {
        if (donnees.length == 0) {
            throw new FichierInvalideException("Le fichier est vide.");
        }
        if (donnees.length > TAILLE_MAX) {
            throw new FichierInvalideException("La photo dépasse 5 Mo.");
        }
        String type = detecterType(donnees);
        if (type == null) {
            throw new FichierInvalideException("Format non accepté : envoyez une image JPEG, PNG ou WebP.");
        }
        Vehicule vehicule = vehiculeRepository.findById(idVehicule)
                .orElseThrow(() -> new ResourceNotFoundException("Véhicule", idVehicule));

        VehiculePhoto photo = photoRepository.findById(idVehicule).orElseGet(() -> {
            VehiculePhoto nouvelle = new VehiculePhoto();
            nouvelle.setVehicule(vehicule);
            return nouvelle;
        });
        photo.setContentType(type);
        photo.setDonnees(donnees);
        photo.setMiseAJour(LocalDateTime.now());
        photoRepository.save(photo);
    }

    @Transactional
    public void supprimer(Long idVehicule) {
        if (!photoRepository.existsById(idVehicule)) {
            throw new ResourceNotFoundException("Ce véhicule n'a pas de photo.");
        }
        photoRepository.deleteById(idVehicule);
    }

    /**
     * Recognises the format from the first bytes of the file ("magic numbers").
     * The file name and the Content-Type sent by the browser can be faked, the content cannot.
     * SVG is refused on purpose: it can contain scripts.
     */
    static String detecterType(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
