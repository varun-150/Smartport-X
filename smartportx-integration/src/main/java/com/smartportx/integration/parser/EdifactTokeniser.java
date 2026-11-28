package com.smartportx.integration.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hand-rolled EDIFACT tokeniser (M8.2). Splits a raw interchange into segments on the
 * UNOA/UNOC default segment terminator {@code '}, then each segment into a tag + coded
 * elements on {@code +}, and each composite element into sub-elements on {@code :}.
 *
 * <p>This is a pragmatic syntax layer only — it enforces well-formed framing and exposes
 * the structured elements a semantic mapper (COPRAR/COPARN) then reads. It does NOT
 * implement the full UN/EDIFACT standard (see PHASE-4-DECISIONS D-008). The UNA service
 * string may prefix an interchange and is consumed, not interpreted beyond the defaults.
 */
public final class EdifactTokeniser {

    private static final char SEGMENT_TERMINATOR = '\'';
    private static final char ELEMENT_SEPARATOR = '+';
    private static final char SUB_ELEMENT_SEPARATOR = ':';

    private EdifactTokeniser() {
    }

    public static List<EdifactSegment> tokenise(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        String body = raw.startsWith("UNA") ? stripUna(raw) : raw;
        List<EdifactSegment> segments = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == SEGMENT_TERMINATOR) {
                String segmentText = body.substring(start, i).trim();
                start = i + 1;
                if (!segmentText.isBlank()) {
                    EdifactSegment segment = tokeniseSegment(segmentText);
                    if (segment != null) {
                        segments.add(segment);
                    }
                }
            }
        }
        return segments;
    }

    private static String stripUna(String raw) {
        // UNA:+.? ' : first 3 chars set the delimiters; we keep the fixed defaults and
        // skip the service string (3 chars) + first terminator + trailing carriage return.
        int idx = raw.indexOf(SEGMENT_TERMINATOR);
        return idx < 0 ? raw : raw.substring(idx + 1);
    }

    private static EdifactSegment tokeniseSegment(String segmentText) {
        int firstSeparator = segmentText.indexOf(ELEMENT_SEPARATOR);
        String tag;
        String remainder = "";
        if (firstSeparator < 0) {
            tag = segmentText;
        } else {
            tag = segmentText.substring(0, firstSeparator);
            remainder = segmentText.substring(firstSeparator + 1);
        }
        if (tag.isBlank()) {
            return null;
        }
        List<List<String>> elements = new ArrayList<>();
        for (String element : split(remainder, ELEMENT_SEPARATOR)) {
            List<String> members = new ArrayList<>();
            for (String member : split(element, SUB_ELEMENT_SEPARATOR)) {
                members.add(member.trim());
            }
            elements.add(Collections.unmodifiableList(members));
        }
        return new EdifactSegment(tag.trim(), Collections.unmodifiableList(elements));
    }

    private static List<String> split(String value, char separator) {
        List<String> parts = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            parts.add("");
            return parts;
        }
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == separator) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }
}
