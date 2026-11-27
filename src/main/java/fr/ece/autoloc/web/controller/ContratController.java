package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.ContratService;
import fr.ece.autoloc.web.dto.ContratDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contrats")
@RequiredArgsConstructor
public class ContratController {

    private final ContratService contratService;

    @GetMapping
    public List<ContratDto.Response> findAll() {
        return contratService.findAll();
    }

    @GetMapping("/{id}")
    public ContratDto.Response findById(@PathVariable("id") Long id) {
        return contratService.findById(id);
    }

    @PostMapping("/{id}/paiements")
    @ResponseStatus(HttpStatus.CREATED)
    public ContratDto.Response ajouterPaiement(@PathVariable("id") Long id,
                                               @Valid @RequestBody ContratDto.PaiementRequest request) {
        return contratService.ajouterPaiement(id, request);
    }
}
