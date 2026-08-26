package com.vedant.picvault.dto;

public record ImageResourceDto(byte[] content, String etag, String contentType) {}
