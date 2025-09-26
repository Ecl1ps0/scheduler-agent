package com.prod_mas.rest.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@Controller
public class FileController {
    final private String fileUploadDirectory = "./upload/result_models/";

    @PostMapping(value="/upload/{fileName}", consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Object> handleFileUpload(@PathVariable("fileName") String fileName, @RequestBody byte[] file) {
        try {
            Path uploadPath = Path.of(fileUploadDirectory);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                System.out.println("Created directory for files: " + uploadPath.toString());
            }

            Files.write(uploadPath.resolve(fileName), file);
            System.out.println("File saved into: " + uploadPath.toString());
        }catch (IOException e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok().build();
    }
}
