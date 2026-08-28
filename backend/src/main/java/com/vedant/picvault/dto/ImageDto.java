package com.vedant.picvault.dto;

import java.util.UUID;

public record ImageDto(UUID id, String filename, String url, long size) {}
// UUID id is required in order to implement delete with id on click of delete button in gallery thumbnail.