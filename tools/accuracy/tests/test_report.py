import csv
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from report import FIELDS, Session, aggregate, compare, journal_coverage, load_trials, measure_trial, percentile, read_sessions


def frame(seconds, distance=.51, tracking=True, fresh=True, completed=False):
    return {"observed_elapsed_ns": int((seconds + 1) * 1e9), "frame_timestamp_ns": int((seconds + 1) * 1e9),
            "camera_frame_is_new": fresh, "camera_tracking": "TRACKING" if tracking else "PAUSED",
            "measurements": [{"id": 1, "distance_m": distance, "tracking": tracking}],
            "command": {"outcome": "COMPLETED", "completed_measurement_id": 1} if completed else None}


def session(frames=None):
    return Session({"model": "Pixel", "algorithm": "baseline_hit_test_v1", "depth_mode": "AUTOMATIC"},
                   frames or [frame(0, completed=True), frame(10, .52), frame(30, .505)], [])


def trial(**changes):
    row = dict.fromkeys(FIELDS, "")
    row.update(trial_id="t1", cohort="pilot", algorithm="baseline_hit_test_v1", scenario="book", support="candidate",
               model="Pixel", depth_mode="AUTOMATIC", session_id="s1", measurement_id="1", reference_cm="50",
               reference_uncertainty_cm="0.1", camera_distance_cm="80", lighting="diffuse", surface="matte",
               status="success", point1_time_s="1", point2_time_s="2", attempt_time_s="3")
    return row | changes


class ReportTest(unittest.TestCase):
    def load(self, rows, sessions=None):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "trials.csv"
            with path.open("w", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=FIELDS)
                writer.writeheader()
                writer.writerows(rows)
            return load_trials(path, sessions or {"s1": session()})

    def test_absolute_relative_bias_and_drift_come_from_confirmed_result(self):
        row = self.load([trial()])[0]
        self.assertAlmostEqual(1, row["absolute_error_cm"])
        self.assertAlmostEqual(2, row["relative_error_percent"])
        self.assertAlmostEqual(1, row["signed_error_cm"])
        self.assertAlmostEqual(1, row["drift_10s_cm"])
        self.assertAlmostEqual(-.5, row["drift_30s_cm"])

    def test_failures_stay_in_denominator_and_missing_times_stay_missing(self):
        rows = self.load([trial(), trial(trial_id="t2", status="refused", measurement_id="", failure_reason="no surface", point1_time_s=""),
                          trial(trial_id="t3", status="aborted", measurement_id="", failure_reason="timeout")])
        group = aggregate(rows)[0]
        self.assertEqual(3, group["attempts"])
        self.assertEqual(1 / 3, group["success_rate"])
        self.assertEqual(2, group["point1_time_s_count"])
        self.assertEqual(1, group["refused"])
        self.assertEqual(1, group["aborted"])

    def test_all_refused_still_generates_group_with_no_accuracy_values(self):
        group = aggregate(self.load([trial(status="refused", measurement_id="", failure_reason="no AR")]))[0]
        self.assertEqual(0, group["success_rate"])
        self.assertIsNone(group["median_absolute_error_cm"])

    def test_stalled_or_late_frames_do_not_make_drift_look_zero(self):
        data = session([frame(0, completed=True), frame(10, .51, fresh=False), frame(11.01, .51), frame(30, .51, tracking=False)])
        result = measure_trial(trial(reference_cm=50), data)
        self.assertIsNone(result["drift_10s_cm"])
        self.assertIsNone(result["drift_30s_cm"])

    def test_checkpoint_tracking_loss_is_not_hidden_by_later_recovery(self):
        data = session([frame(0, completed=True), frame(10, tracking=False), frame(10.2, .52)])
        self.assertIsNone(measure_trial(trial(reference_cm=50), data)["drift_10s_cm"])

    def test_duplicate_trial_or_measurement_is_rejected(self):
        for rows in ([trial(), trial()], [trial(), trial(trial_id="t2", measurement_id="01")]):
            with self.assertRaises(ValueError):
                self.load(rows)

    def test_no_physical_trials_is_not_a_report(self):
        with self.assertRaisesRegex(ValueError, "No physical trials"):
            self.load([])

    def test_missing_logs_invalid_reference_unreported_failure_and_algorithm_mismatch_rejected(self):
        for row in (trial(session_id="missing"), trial(reference_cm="0"), trial(reference_cm="NaN"),
                    trial(status="refused", measurement_id=""), trial(algorithm="different"), trial(status="pending")):
            with self.subTest(row=row), self.assertRaises(ValueError):
                self.load([row])

    def test_missing_completion_is_not_replaced_by_a_later_reading(self):
        with self.assertRaisesRegex(ValueError, "exactly one completion"):
            self.load([trial()], {"s1": session([frame(5)])})

    def test_group_conditions_include_distance_device_and_unsupported_cases(self):
        rows = self.load([trial(), trial(trial_id="t2", session_id="s2", camera_distance_cm="120", support="unsupported")],
                         {"s1": session(), "s2": session()})
        self.assertEqual(2, len(aggregate(rows)))
        self.assertEqual([], compare(aggregate(rows)))

    def test_comparison_reports_error_ratio_with_success_tradeoff(self):
        changed_session = session()
        changed_session.metadata["algorithm"] = "stability"
        changed_session.frames[0]["measurements"][0]["distance_m"] = .505
        rows = self.load([trial(), trial(trial_id="t2", session_id="s2", algorithm="stability"),
                          trial(trial_id="t3", session_id="s2", algorithm="stability", status="refused", measurement_id="", failure_reason="unstable")],
                         {"s1": session(), "s2": changed_session})
        result = compare(aggregate(rows))[0]
        self.assertAlmostEqual(.5, result["median_error_after_divided_by_before"])
        self.assertEqual(-.5, result["success_rate_change"])
        self.assertIn("UNVERIFIED", result["gate"])

    def test_percentile_definition(self):
        self.assertAlmostEqual(2.9, percentile([1, 2, 3]))
        self.assertIsNone(percentile([]))

    def test_unassigned_completed_measurements_are_exposed(self):
        self.assertEqual([1], journal_coverage({"s1": session()}, [])[0]["unassigned_measurement_ids"])
        self.assertEqual([], journal_coverage({"s1": session()}, self.load([trial()]))[0]["unassigned_measurement_ids"])

    def test_log_integrity_and_drop_flags(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "accuracy-test.jsonl"
            header = {"event": "session", "schema_version": 1, "session_id": "s", "sequence": 0}
            end = {"event": "journal_end", "schema_version": 1, "session_id": "s", "sequence": 3,
                   "reason": "size_limit", "dropped_events": 2}
            path.write_text(json.dumps(header) + "\n" + json.dumps(end) + "\n")
            self.assertEqual(3, len(read_sessions(Path(directory))["s"].warnings))
            path.write_text(json.dumps(header) + "\n{" )
            with self.assertRaisesRegex(ValueError, "interrupted journal"):
                read_sessions(Path(directory))


if __name__ == "__main__":
    unittest.main()
