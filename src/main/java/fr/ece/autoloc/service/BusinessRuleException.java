package fr.ece.autoloc.service;

/**
 * Raised when a request is well formed but breaks a business rule
 * (duplicate registration number, overlapping reservation, forbidden status change...).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
