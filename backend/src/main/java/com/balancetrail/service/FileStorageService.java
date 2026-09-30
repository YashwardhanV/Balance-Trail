package com.balancetrail.service;

import com.balancetrail.config.StorageProperties;
import com.balancetrail.exception.InvalidUploadException;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
  private static final String REQUIRED_HEADER =
      "transaction_id,account_number,amount,transaction_date";

  private final Path uploadDirectory;

  public FileStorageService(StorageProperties properties) {
    this.uploadDirectory = Path.of(properties.uploadDirectory()).toAbsolutePath().normalize();
  }

  @PostConstruct
  void createUploadDirectory() {
    try {
      Files.createDirectories(uploadDirectory);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not create upload directory", exception);
    }
  }

  public StoredFile store(MultipartFile file) {
    validateFile(file);
    String originalName = safeOriginalName(file.getOriginalFilename());
    // The stored name is a random UUID, never the user's file name, so it cannot escape the folder.
    Path target = uploadDirectory.resolve(UUID.randomUUID() + ".csv");

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream input = file.getInputStream();
          DigestInputStream digested = new DigestInputStream(input, digest)) {
        Files.copy(digested, target, StandardCopyOption.REPLACE_EXISTING);
      }
      validateHeader(target);
      return new StoredFile(originalName, HexFormat.of().formatHex(digest.digest()), target);
    } catch (IOException exception) {
      deleteQuietly(target);
      throw new InvalidUploadException("Could not store the uploaded CSV", exception);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    } catch (RuntimeException exception) {
      deleteQuietly(target);
      throw exception;
    }
  }

  public void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // An orphan upload is safer than hiding the original request result behind cleanup failure.
    }
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new InvalidUploadException("A non-empty CSV file is required");
    }
    String name = safeOriginalName(file.getOriginalFilename());
    if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      throw new InvalidUploadException("Only .csv files are accepted");
    }
  }

  private void validateHeader(Path target) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(target)) {
      String firstLine = reader.readLine();
      if (firstLine != null && firstLine.startsWith("\uFEFF")) {
        firstLine = firstLine.substring(1);
      }
      if (firstLine == null || !REQUIRED_HEADER.equals(firstLine.trim().toLowerCase(Locale.ROOT))) {
        throw new InvalidUploadException("CSV header must be: " + REQUIRED_HEADER);
      }
    }
  }

  private String safeOriginalName(String name) {
    String value = name == null ? "upload.csv" : Path.of(name).getFileName().toString();
    return value.length() <= 255 ? value : value.substring(value.length() - 255);
  }
}
