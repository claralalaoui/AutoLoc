package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.VehiculePhoto;
import fr.ece.autoloc.service.VehiculePhotoService;
import fr.ece.autoloc.service.VehiculeService;
import fr.ece.autoloc.web.dto.VehiculeDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/vehicules")
@RequiredArgsConstructor
public class VehiculeController {

    private final VehiculeService vehiculeService;
    private final VehiculePhotoService photoService;

    @GetMapping
    public List<VehiculeDto.Response> findAll() {
        return vehiculeService.findAll();
    }

    @GetMapping("/{id}")
    public VehiculeDto.Response findById(@PathVariable("id") Long id) {
        return vehiculeService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehiculeDto.Response create(@Valid @RequestBody VehiculeDto.Request request) {
        return vehiculeService.create(request);
    }

    @PutMapping("/{id}")
    public VehiculeDto.Response update(@PathVariable("id") Long id, @Valid @RequestBody VehiculeDto.Request request) {
        return vehiculeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        vehiculeService.delete(id);
    }

    /* ---------- Photo ---------- */

    /** Public (see SecurityConfig): an <img> tag cannot send the Authorization header. */
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable("id") Long id) {
        VehiculePhoto photo = photoService.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(photo.getDonnees());
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VehiculeDto.Response envoyerPhoto(@PathVariable("id") Long id,
                                             @RequestParam("photo") MultipartFile photo) throws IOException {
        photoService.enregistrer(id, photo.getBytes());
        return vehiculeService.findById(id);
    }

    @DeleteMapping("/{id}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerPhoto(@PathVariable("id") Long id) {
        photoService.supprimer(id);
    }
}
