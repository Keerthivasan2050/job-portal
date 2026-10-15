package com.jobportal.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        logger.warn("Resource not found: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public String handleUnauthorizedAccess(UnauthorizedAccessException ex, Model model) {
        logger.warn("Unauthorized access attempted: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/403";
    }

    @ExceptionHandler(DuplicateApplicationException.class)
    public String handleDuplicateApplication(DuplicateApplicationException ex, Model model) {
        logger.warn("Duplicate application attempted: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/500";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(MaxUploadSizeExceededException ex, Model model) {
        logger.warn("Max upload size exceeded: {}", ex.getMessage());
        model.addAttribute("errorMessage", "The uploaded file is too large. Maximum allowed size is 10MB.");
        return "error/500";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneralException(Exception ex, Model model) {
        logger.error("Unhandled exception occurred: ", ex);
        model.addAttribute("errorMessage", ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred.");
        return "error/500";
    }
}
