package com.balancetrail.service;

import java.nio.file.Path;

public record StoredFile(String originalFileName, String sha256, Path path) {}
