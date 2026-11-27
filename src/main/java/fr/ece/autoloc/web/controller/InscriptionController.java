package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.CompteService;
import fr.ece.autoloc.web.dto.InscriptionDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public sign-up (under /api/public/**, see SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class InscriptionController {

    private final CompteService compteService;

    @PostMapping("/api/public/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> inscrire(@Valid @RequestBody InscriptionDto request) {
        return Map.of("identifiant", compteService.inscrire(request));
    }
}
