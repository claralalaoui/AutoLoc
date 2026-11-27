package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.VehiculeService;
import fr.ece.autoloc.web.dto.VehiculeDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vehicules")
@RequiredArgsConstructor
public class VehiculeController {

    private final VehiculeService vehiculeService;

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
}
