package com.smartportx.integration.parser;

import java.util.List;

/**
 * A single EDIFACT segment: a tag (e.g. {@code UNB}, {@code EQD}) followed by its
 * coded elements. Elements are tokenised on {@code +}; composite element members are
 * split on {@code :} and retained as ordered sub-elements.
 */
public record EdifactSegment(String tag, List<List<String>> elements) {

    /** Positional accessor: the n-th (0-based) element's 0-th sub-element, or empty. */
    public String element(int index) {
        if (index >= elements.size()) {
            return "";
        }
        List<String> members = elements.get(index);
        return members.isEmpty() ? "" : members.get(0);
    }

    /** Positional member accessor within a composite element. */
    public String member(int elementIndex, int memberIndex) {
        if (elementIndex >= elements.size()) {
            return "";
        }
        List<String> members = elements.get(elementIndex);
        if (memberIndex >= members.size()) {
            return "";
        }
        String value = members.get(memberIndex);
        return value == null ? "" : value;
    }
}
