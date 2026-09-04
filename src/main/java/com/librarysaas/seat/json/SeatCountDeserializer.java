package com.librarysaas.seat.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;

/**
 * Reads a seat count that must have been written as a whole number.
 *
 * Jackson accepts a JSON float for an int field by default and quietly truncates
 * it, so a client sending 1.5 seats would be told it created 1 - Bean Validation
 * never sees the .5, because the value has already lost it by the time the
 * constraints run. A decimal seat count has to be refused, so it is refused
 * here, the only place that can still tell it was one.
 *
 * Applied per field rather than by switching ACCEPT_FLOAT_AS_INT off globally:
 * that flag is a whole-application change affecting every numeric payload in the
 * API, and only the seat count needs to be this strict.
 *
 * The message is the one the user should read. GlobalExceptionHandler relays it
 * as a field error against the field that carried it, so a decimal is reported
 * the same way an out-of-range value is rather than as a generic malformed body.
 */
public class SeatCountDeserializer extends JsonDeserializer<Integer> {

    /** Wording fixed by the requirement, and surfaced verbatim to the client. */
    public static final String WHOLE_NUMBER_REQUIRED = "Number of seats must be a whole number.";

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();

        if (token == JsonToken.VALUE_NUMBER_FLOAT) {
            throw new InvalidFormatException(parser,
                    WHOLE_NUMBER_REQUIRED, parser.getText(), Integer.class);
        }

        // A quoted number is still a number to most clients, but it must be a
        // whole one; "1.5" is refused for the same reason 1.5 is.
        if (token == JsonToken.VALUE_STRING) {
            String text = parser.getText().trim();
            if (!text.matches("-?\\d+")) {
                throw new InvalidFormatException(parser, WHOLE_NUMBER_REQUIRED, text, Integer.class);
            }
            return Integer.valueOf(text);
        }

        // Anything left that is not an integer - a boolean, an object - is
        // reported by Jackson's own handling rather than guessed at here.
        return parser.getIntValue();
    }
}
