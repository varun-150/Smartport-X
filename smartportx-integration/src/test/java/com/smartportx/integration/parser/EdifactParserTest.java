package com.smartportx.integration.parser;

import com.smartportx.common.error.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EdifactParserTest {

    private final EdifactParser parser = new EdifactParser();

    private String coprar() {
        return "UNB+UNOC:3+ZIM+NYKANG+260101:0000+12345+1'"
                + "UNH+COPRAR01+COPRAR:D:95B:UN:1.0'"
                + "BGM+115+COPRAR01+9'"
                + "NAD+CA+ZIMU+ZIM+NL:311'"
                + "NAD+CO+NL:311+++++ROTTERDAM'"
                + "TDT+20+++MAERSK+HONGKONG+9V5951'"
                + "LOC+11+USNYC'"
                + "LOC+88+TERM-NY'"
                + "DTM+132:20260115:1015+203'"
                + "EQD+CN+45G1+MSKU1234567+5'"
                + "UNT+11+COPRAR01'"
                + "UNZ+1+12345'";
    }

    @Test
    void parsesCoprarEnvelopeAndContainer() {
        ParsedEdifact result = parser.parse("COPRAR", coprar());

        assertThat(result.messageType()).isEqualTo("COPRAR");
        assertThat(result.messageReference()).isEqualTo("COPRAR01");
        assertThat(result.sender()).isEqualTo("ZIM");
        assertThat(result.receiver()).isEqualTo("NYKANG");
        assertThat(result.interchangeRef()).isEqualTo("12345");
        assertThat(result.carrierCode()).isEqualTo("ZIMU");
        assertThat(result.carrierName()).isEqualTo("ZIM");
        assertThat(result.carrierCountry()).isEqualTo("ROTTERDAM");
        assertThat(result.vesselName()).isEqualTo("MAERSK");
        assertThat(result.vesselImo()).isEqualTo("9V5951");
        assertThat(result.portOfCall()).isEqualTo("USNYC");
        assertThat(result.terminal()).isEqualTo("TERM-NY");
        assertThat(result.eta()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 15, 10, 15).toInstant(ZoneOffset.UTC));

        List<EdifactContainer> containers = result.containers();
        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).containerNumber()).isEqualTo("MSKU1234567");
        assertThat(containers.get(0).isoType()).isEqualTo("45G1");
        assertThat(containers.get(0).status()).isEqualTo("5");
    }

    @Test
    void parsesCoparnAsSingleContainerArrival() {
        String raw = "UNB+UNOC:3+CNGP+PORTX+260102:0000+777+1'"
                + "UNH+CA01+COPARN:D:95B:UN:1.0'"
                + "BGM+180+CA01+7'"
                + "NAD+CA+CNGP+CHINA SHIPPING+CN:156'"
                + "LOC+11+CNSHA'"
                + "LOC+88+TERM-NY'"
                + "DTM+132:20260120:1015+203'"
                + "EQD+CN+42G1+CMAU0000425+5'"
                + "RFF+BM:BSO123'"
                + "UNT+9+CA01'"
                + "UNZ+1+777'";
        ParsedEdifact result = parser.parse("COPARN", raw);

        assertThat(result.messageType()).isEqualTo("COPARN");
        assertThat(result.sender()).isEqualTo("CNGP");
        assertThat(result.carrierCode()).isEqualTo("CNGP");
        assertThat(result.portOfCall()).isEqualTo("CNSHA");
        assertThat(result.containers()).extracting(EdifactContainer::containerNumber)
                .containsExactly("CMAU0000425");
    }

    @Test
    void rejectsMappingToWrongMessageType() {
        assertThatThrownBy(() -> parser.parse("CODECO", coprar()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(400);
                    assertThat(api.getMessage()).contains("does not match requested messageType");
                });
    }

    @Test
    void rejectsUnsupportedMessageType() {
        assertThatThrownBy(() -> parser.parse("IFTMIN", "UNB+UNOC:3+A+B+1+260101:0000+1'"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(400);
                    assertThat(api.getCode()).isEqualTo("unsupported_message_type");
                });
    }

    @Test
    void rejectsMissingEnvelopeSegments() {
        String raw = "BGM+115+empty+9'";
        assertThatThrownBy(() -> parser.parse("COPRAR", raw))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(400);
                    assertThat(api.getCode()).isEqualTo("validation_failed");
                });
    }

    @Test
    void rejectsBlankRawMessage() {
        assertThatThrownBy(() -> parser.parse("COPRAR", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("malformed_message"));
    }

    @Test
    void etaUsesUtcInstant() {
        ParsedEdifact result = parser.parse("COPRAR", coprar());
        Instant eta = result.eta();
        assertThat(eta).isNotNull();
        assertThat(OffsetDateTime.ofInstant(eta, ZoneOffset.UTC).getYear()).isEqualTo(2026);
    }
}
