package com.chalkak.backend.jev.domain;

import com.chalkak.backend.jev.repository.JevMetricsRepository.Call;
import com.chalkak.backend.jev.repository.JevMetricsRepository.Sample;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record JevMetrics(
        int runs,
        long completed,
        long autoApproved,
        long adminReview,
        long skipped,
        long technicalErrors,
        boolean truncated,
        Agreement finalAgreement,
        Map<JevGoldLabel.Sampling, Agreement> bySampling,
        Map<String, Agreement> questions,
        long ratedAutoApprovals,
        long falseAutoApprovals,
        long repeatedGroups,
        long stableGroups,
        Double consistencyPercent,
        Usage usage
) {

    public static JevMetrics calculate(
            List<Sample> samples,
            List<Call> calls,
            boolean truncated
    ) {
        List<Sample> rated = samples.stream().filter(sample -> sample.gold() != null
                && sample.evaluation() != null && !isManual(sample.evaluation().error())).toList();
        Map<JevGoldLabel.Sampling, Agreement> groups = new LinkedHashMap<>();
        for (var sampling : JevGoldLabel.Sampling.values()) {
            groups.put(sampling, agreement(rated.stream()
                    .filter(sample -> sample.gold().sampling() == sampling).toList(), null));
        }
        Map<String, Agreement> questions = new LinkedHashMap<>();
        for (String name : List.of(
                "topic_fit",
                "image_safety",
                "image_expression"
        )) {
            questions.put(name, agreement(rated, name));
        }
        var repeated = samples.stream().filter(sample -> sample.evaluation() != null
                && sample.evaluation().error() == null)
                .collect(java.util.stream.Collectors.groupingBy(Sample::repeatKey)).values()
                .stream().filter(rows -> rows.size() >= 5).toList();
        long stable = repeated.stream().filter(rows -> rows.stream()
                .map(row -> row.evaluation().recommendation()).distinct().count() == 1).count();
        return new JevMetrics(
                samples.size(),
                samples.stream().filter(row -> row.evaluation() != null).count(),
                count(samples, JevDisposition.AUTO_APPROVED),
                count(samples, JevDisposition.ADMIN_REVIEW),
                count(samples, JevDisposition.SKIPPED),
                samples.stream()
                        .filter(row -> row.evaluation() != null && row.evaluation().error() != null
                                && !isManual(row.evaluation().error()))
                        .count(),
                truncated,
                agreement(rated, null),
                groups,
                questions,
                rated.stream().filter(row -> row.disposition() == JevDisposition.AUTO_APPROVED)
                        .count(),
                rated.stream().filter(row -> row.disposition() == JevDisposition.AUTO_APPROVED
                        && row.gold().decision() != JevDecision.APPROVE).count(),
                repeated.size(),
                stable,
                percent(stable, repeated.size()),
                usage(calls)
        );
    }

    private static boolean isManual(String error) {
        return error != null && List.of(
                "AUTOMATION_DISABLED",
                "CONFIGURATION_MISSING",
                "PREEXISTING_REGISTRATION",
                "SOURCE_DELETED",
                "ADMIN_ALREADY_DECIDED",
                "IMAGE_PROCESSING_FAILED"
        ).contains(error);
    }

    private static long count(List<Sample> rows, JevDisposition disposition) {
        return rows.stream().filter(row -> row.disposition() == disposition).count();
    }

    private static Agreement agreement(List<Sample> samples, String question) {
        long rated = 0;
        long matched = 0;
        long missing = 0;
        long falseApprove = 0;
        long falseReject = 0;
        for (Sample sample : samples) {
            JevDecision expected = question == null
                    ? sample.gold().decision()
                    : sample.gold().questions().get(question);
            JevDecision actual = question == null
                    ? sample.evaluation().recommendation()
                    : sample.evaluation().decisions().get(question);
            if (expected == null) {
                continue;
            }
            rated++;
            if (actual == null) {
                missing++;
                continue;
            }
            if (expected == actual) {
                matched++;
            }
            if (actual == JevDecision.APPROVE && expected != JevDecision.APPROVE) {
                falseApprove++;
            }
            if (actual == JevDecision.REJECT && expected != JevDecision.REJECT) {
                falseReject++;
            }
        }
        return new Agreement(
                rated,
                matched,
                falseApprove,
                falseReject,
                missing,
                percent(matched, rated)
        );
    }

    private static Usage usage(List<Call> calls) {
        List<Long> latencies = calls.stream().map(Call::latencyMs).filter(value -> value != null)
                .sorted().toList();
        BigInteger input = BigInteger.ZERO;
        BigInteger output = BigInteger.ZERO;
        for (Call call : calls) {
            if (call.inputTokens() != null) {
                input = input.add(BigInteger.valueOf(call.inputTokens()));
            }
            if (call.outputTokens() != null) {
                output = output.add(BigInteger.valueOf(call.outputTokens()));
            }
        }
        Long p99 = latencies.isEmpty()
                ? null
                : latencies.get((int) Math.ceil(latencies.size() * .99) - 1);
        return new Usage(
                calls.size(),
                calls.stream().filter(row -> row.latencyMs() != null || row.httpStatus() != null)
                        .count(),
                latencies.size(),
                p99,
                input,
                output,
                calls.stream().filter(row -> row.inputTokens() == null).count(),
                calls.stream().filter(row -> row.outputTokens() == null).count()
        );
    }

    private static Double percent(long value, long total) {
        return total == 0 ? null : 100.0 * value / total;
    }

    public record Agreement(
            long rated,
            long matched,
            long falseApprovals,
            long falseRejections,
            long missingPredictions,
            Double matchPercent
    ) {
    }

    public record Usage(
            long reservedAttempts,
            long observedHttpCalls,
            long timedHttpCalls,
            Long httpP99Ms,
            BigInteger knownInputTokens,
            BigInteger knownOutputTokens,
            long unknownInputUsageCalls,
            long unknownOutputUsageCalls
    ) {
    }
}
