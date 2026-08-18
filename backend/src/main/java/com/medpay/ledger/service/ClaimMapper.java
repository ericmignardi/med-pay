package com.medpay.ledger.service;

import com.medpay.ledger.dto.ClaimLineResponse;
import com.medpay.ledger.dto.ClaimResponse;
import com.medpay.ledger.dto.ClaimSummaryResponse;
import com.medpay.ledger.dto.JournalGroupResponse;
import com.medpay.ledger.dto.JournalLineResponse;
import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimLine;
import com.medpay.ledger.model.LedgerJournal;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ClaimMapper {

    public ClaimResponse toResponse(Claim claim, List<LedgerJournal> journals) {
        return new ClaimResponse(
                claim.getClaimUuid(),
                claim.getProvider().getProviderNpi(),
                claim.getProvider().getProviderName(),
                claim.getMemberReference(),
                claim.getServiceDate(),
                claim.getBilledAmount(),
                claim.getAllowedAmount(),
                claim.getPatientResponsibility(),
                claim.getStatus(),
                claim.getSubmittedAt(),
                claim.getAdjudicatedAt(),
                claim.getReviewedAt(),
                claim.getReviewNote(),
                claim.getDenialReason(),
                claim.getLines().stream().map(this::toLineResponse).toList(),
                toJournalGroups(journals));
    }

    public ClaimSummaryResponse toSummary(Claim claim) {
        return new ClaimSummaryResponse(
                claim.getClaimUuid(),
                claim.getProvider().getProviderNpi(),
                claim.getServiceDate(),
                claim.getBilledAmount(),
                claim.getAllowedAmount(),
                claim.getStatus(),
                claim.getSubmittedAt(),
                claim.getLines().size());
    }

    public ClaimLineResponse toLineResponse(ClaimLine line) {
        return new ClaimLineResponse(
                line.getLineNumber(),
                line.getServiceCode(),
                line.getDiagnosisCode(),
                line.getBilledAmount(),
                line.getAllowedAmount(),
                line.getPatientResponsibility());
    }

    public JournalLineResponse toJournalLine(LedgerJournal journal) {
        return new JournalLineResponse(
                journal.getJournalGroupId(),
                journal.getClaim().getClaimUuid(),
                journal.getClaim().getProvider().getProviderNpi(),
                journal.getAccountType(),
                journal.getDirection(),
                journal.getAmount(),
                journal.getMemo(),
                journal.getReversesJournalGroupId(),
                journal.getPostedAt());
    }

    public List<JournalGroupResponse> toJournalGroups(List<LedgerJournal> journals) {
        Map<UUID, List<LedgerJournal>> grouped = new LinkedHashMap<>();
        journals.stream()
                .sorted(Comparator.comparing(LedgerJournal::getPostedAt)
                        .thenComparing(LedgerJournal::getId))
                .forEach(journal -> grouped
                        .computeIfAbsent(journal.getJournalGroupId(), key -> new java.util.ArrayList<>())
                        .add(journal));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<LedgerJournal> group = entry.getValue();
                    LedgerJournal first = group.getFirst();
                    Instant postedAt = first.getPostedAt();
                    return new JournalGroupResponse(
                            entry.getKey(),
                            first.getReversesJournalGroupId(),
                            postedAt,
                            group.stream().map(this::toJournalLine).toList());
                })
                .toList();
    }
}
