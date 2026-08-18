package com.medpay.ledger.repository;

import com.medpay.ledger.model.LedgerJournal;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * FR-021 filters for {@code GET /audit/journals}. Each factory returns {@code null} when its
 * parameter is absent, and {@link #compose} drops those before combining — {@code
 * Specification.allOf} rejects a null element rather than ignoring it.
 *
 * <p>Paths are navigated implicitly ({@code root.get("claim").get("provider")}) rather than
 * with explicit joins: two explicit {@code root.join("claim")} calls produce two separate
 * inner joins to the same row, whereas Hibernate reuses an implicit path.
 */
public final class JournalSpecifications {

    private JournalSpecifications() {
    }

    public static Specification<LedgerJournal> providerNpiIs(String providerNpi) {
        if (providerNpi == null || providerNpi.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(
                root.get("claim").get("provider").get("providerNpi"), providerNpi);
    }

    public static Specification<LedgerJournal> claimUuidIs(UUID claimUuid) {
        if (claimUuid == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("claim").get("claimUuid"), claimUuid);
    }

    public static Specification<LedgerJournal> journalGroupIdIs(UUID journalGroupId) {
        if (journalGroupId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("journalGroupId"), journalGroupId);
    }

    public static Specification<LedgerJournal> postedFrom(Instant from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("postedAt"), from);
    }

    public static Specification<LedgerJournal> postedTo(Instant to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("postedAt"), to);
    }

    /** Composes with AND. With no filters supplied the result is unrestricted, not empty. */
    public static Specification<LedgerJournal> compose(String providerNpi, UUID claimUuid,
                                                       UUID journalGroupId,
                                                       Instant postedFrom, Instant postedTo) {

        List<Specification<LedgerJournal>> present = new ArrayList<>(Arrays.asList(
                providerNpiIs(providerNpi),
                claimUuidIs(claimUuid),
                journalGroupIdIs(journalGroupId),
                postedFrom(postedFrom),
                postedTo(postedTo)));
        present.removeIf(Objects::isNull);

        return present.isEmpty() ? Specification.unrestricted() : Specification.allOf(present);
    }
}
