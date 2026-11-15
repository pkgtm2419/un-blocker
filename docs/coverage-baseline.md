# DNS coverage and accuracy baseline — verification required

This page restores an absent README link. **No measured block percentage, global false-positive rate, battery usage, p95 latency or device coverage number is asserted.** The source contains labeled test corpora and `app/src/test/java/com/unblocker/app/quality/{CoverageReportTest,BlockingRegressionCorpusTest}.kt`, but a checked-in test suite is not a population-wide accuracy study.

## Define a future baseline

1. Pin source SHA, Gradle version, dataset hashes and list enablement/tier.
2. Run labeled benign/tracker/adult fixtures, malformed DNS, protected services and real network transitions.
3. Report true positive, false positive, true negative and false negative counts with denominators and scope; failures cannot be hidden by lowering corpus expectations.
4. Run on representative physical devices/APIs with pre/post battery/CPU and overhead measurements under controlled conditions.
5. Record raw redacted outputs, CI/device evidence and date; maintain regression thresholds only when supported.

See [test matrix](TEST_MATRIX.md) and [network coverage](features/NETWORK_COVERAGE.md).
