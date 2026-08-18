package com.medpay.ledger.util;

import com.medpay.ledger.exception.IllegalStateTransitionException;
import com.medpay.ledger.model.ClaimEvent;
import com.medpay.ledger.model.ClaimStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimStateMachineTest {

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource({
            "RECEIVED,       VALIDATE_OK,                      VALIDATED",
            "VALIDATED,      ADJUDICATE_BELOW_THRESHOLD,       ADJUDICATED",
            "VALIDATED,      ADJUDICATE_AT_OR_ABOVE_THRESHOLD, FLAGGED_REVIEW",
            "ADJUDICATED,    POST_LEDGER,                      PAID",
            "FLAGGED_REVIEW, REVIEWER_APPROVE,                 ADJUDICATED",
            "FLAGGED_REVIEW, REVIEWER_DENY,                    DENIED",
            "PAID,           REVERSE,                          REVERSED"
    })
    @DisplayName("FR-012: every legal transition in §3.3")
    void legalTransitions(ClaimStatus from, ClaimEvent event, ClaimStatus expected) {
        assertThat(ClaimStateMachine.transition(from, event)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} + {1} is rejected")
    @CsvSource({
            "PAID,           REVIEWER_APPROVE",
            "DENIED,         REVIEWER_APPROVE",
            "PAID,           REVIEWER_DENY",
            "DENIED,         REVIEWER_DENY",
            "FLAGGED_REVIEW, REVERSE",
            "DENIED,         REVERSE",
            "REVERSED,       REVERSE",
            "VALIDATED,      REVIEWER_APPROVE",
            "RECEIVED,       POST_LEDGER",
            "ADJUDICATED,    REVERSE"
    })
    @DisplayName("FR-012: every consequential illegal transition in §3.3")
    void illegalTransitions(ClaimStatus from, ClaimEvent event) {
        assertThatThrownBy(() -> ClaimStateMachine.transition(from, event))
                .isInstanceOf(IllegalStateTransitionException.class)
                .satisfies(thrown -> {
                    var ex = (IllegalStateTransitionException) thrown;
                    assertThat(ex.getCurrentStatus()).isEqualTo(from);
                    assertThat(ex.getAttemptedEvent()).isEqualTo(event);
                    assertThat(ex.getAllowedEvents()).doesNotContain(event);
                });
    }

    @ParameterizedTest
    @EnumSource(ClaimStatus.class)
    @DisplayName("FR-012: exhaustively, only the tabulated pairs are legal")
    void everyUntabulatedPairIsIllegal(ClaimStatus from) {
        Set<ClaimEvent> allowed = ClaimStateMachine.allowedEvents(from);

        for (ClaimEvent event : ClaimEvent.values()) {
            if (allowed.contains(event)) {
                assertThat(ClaimStateMachine.transition(from, event)).isNotNull();
            } else {
                assertThatThrownBy(() -> ClaimStateMachine.transition(from, event))
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("terminalStates")
    @DisplayName("FR-012: PAID is not terminal until reversed; DENIED and REVERSED are")
    void terminalStatesAdmitNothing(ClaimStatus terminal) {
        assertThat(ClaimStateMachine.allowedEvents(terminal)).isEmpty();
        assertThat(ClaimStateMachine.isTerminal(terminal)).isTrue();
    }

    static Stream<ClaimStatus> terminalStates() {
        return Stream.of(ClaimStatus.DENIED, ClaimStatus.REVERSED);
    }

    @Test
    @DisplayName("SUBMIT and VALIDATE_FAIL have no tabulated source state")
    void unmappedEventsAreNeverLegal() {
        for (ClaimStatus from : ClaimStatus.values()) {
            assertThatThrownBy(() -> ClaimStateMachine.transition(from, ClaimEvent.SUBMIT))
                    .isInstanceOf(IllegalStateTransitionException.class);
            assertThatThrownBy(() -> ClaimStateMachine.transition(from, ClaimEvent.VALIDATE_FAIL))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Test
    @DisplayName("the exception carries the events that would have been legal")
    void allowedEventsAreReported() {
        assertThatThrownBy(() ->
                ClaimStateMachine.transition(ClaimStatus.FLAGGED_REVIEW, ClaimEvent.REVERSE))
                .isInstanceOf(IllegalStateTransitionException.class)
                .satisfies(thrown -> assertThat(
                        ((IllegalStateTransitionException) thrown).getAllowedEvents())
                        .containsExactlyInAnyOrder(
                                ClaimEvent.REVIEWER_APPROVE, ClaimEvent.REVIEWER_DENY));
    }

    @Test
    @DisplayName("every status appears in the transition table")
    void everyStatusIsTabulated() {
        List<ClaimStatus> statuses = Arrays.asList(ClaimStatus.values());

        assertThat(statuses).allSatisfy(status ->
                assertThat(ClaimStateMachine.allowedEvents(status)).isNotNull());
    }
}
