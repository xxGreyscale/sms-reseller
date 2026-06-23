package com.smsreseller.contact.csv;

/**
 * Import summary DTO returned by POST /api/v1/contacts/import (CONT-09).
 *
 * @param imported   number of rows successfully inserted as new contacts
 * @param duplicates number of rows skipped because the phone already exists for this user
 * @param invalid    number of rows skipped because the phone number was unparseable or invalid
 */
public record ImportSummaryResponse(int imported, int duplicates, int invalid) {}
