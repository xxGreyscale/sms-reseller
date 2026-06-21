package com.opendesk.messaging.sms;

/**
 * SMS character encoding type.
 *
 * <ul>
 *   <li>GSM7: 7-bit encoding. Single SMS: 160 chars. Multipart: 153 chars/part.</li>
 *   <li>UCS2: 16-bit encoding. Single SMS: 70 chars. Multipart: 67 chars/part.</li>
 * </ul>
 */
public enum SmsEncoding {
    GSM7,
    UCS2
}
