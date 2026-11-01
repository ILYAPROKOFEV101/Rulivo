#!/usr/bin/env python3
"""Join physical trial records to Rulivo JSONL diagnostics. Standard library only."""
from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
import json
import math
from pathlib import Path
import statistics
from collections import defaultdict

FIELDS = ["trial_id", "cohort", "algorithm", "scenario", "support", "model", "depth_mode",
          "session_id", "measurement_id", "reference_cm", "reference_uncertainty_cm",
          "camera_distance_cm", "lighting", "surface", "status", "failure_reason",
          "point1_time_s", "point2_time_s", "attempt_time_s", "notes"]


def number(value, field, *, optional=False, positive=False):
    if optional and (value is None or value == ""):
        return None
    try:
        result = float(value)
    except (ValueError, TypeError) as error:
        raise ValueError(f"{field}: expected a number, got {value!r}") from error
    if not math.isfinite(result) or result < 0 or (positive and result == 0):
        raise ValueError(f"{field}: expected a finite {'positive' if positive else 'nonnegative'} number")
    return result


def percentile(values, fraction=.95):
    """Linear interpolation at (n-1)*p, reported with sample count, never a population guarantee."""
    if not values:
        return None
    ordered = sorted(values)
    index = (len(ordered) - 1) * fraction
    lo, hi = math.floor(index), math.ceil(index)
    return ordered[lo] + (ordered[hi] - ordered[lo]) * (index - lo)


def median(values):
    return statistics.median(values) if values else None


@dataclass
class Session:
    metadata: dict
    frames: list[dict]
    warnings: list[str]


def read_sessions(directory: Path) -> dict[str, Session]:
    sessions = {}
    for path in sorted(directory.glob("accuracy-*.jsonl")):
        def reject_constant(value):
            raise ValueError(f"{path}: invalid JSON number {value}")
        events = []
        for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            try:
                events.append(json.loads(line, parse_constant=reject_constant))
            except (ValueError, json.JSONDecodeError) as error:
                raise ValueError(f"{path}:{line_no}: malformed or interrupted journal; preserve original and re-export") from error
        if not events or events[0].get("event") != "session":
            raise ValueError(f"{path}: missing session header")
        header = events[0]
        sid = header["session_id"]
        if sid in sessions:
            raise ValueError(f"Duplicate session {sid}")
        warnings = []
        if header.get("synthetic_test_only"):
            warnings.append("SYNTHETIC TEST DATA: not a physical measurement or accuracy result")
        previous = -1
        for event in events:
            if event.get("schema_version") != 1 or event.get("session_id") != sid:
                raise ValueError(f"{path}: mixed session or unsupported schema")
            sequence = event["sequence"]
            if sequence <= previous:
                raise ValueError(f"{path}: unordered/duplicated events")
            if sequence != previous + 1:
                warnings.append("Sequence gap: some diagnostic events are missing")
            previous = sequence
        if any(event.get("dropped_events", 0) for event in events):
            warnings.append("Diagnostic queue overflow: some events were dropped")
        if events[-1].get("event") != "journal_end":
            warnings.append("Open/interrupted session: no journal_end marker; export after pausing the app")
        elif events[-1].get("reason") == "size_limit":
            warnings.append("Journal reached size limit; later attempts may have no diagnostics")
        sessions[sid] = Session(header, [e for e in events if e["event"] == "frame"], sorted(set(warnings)))
    return sessions


def reading(frame, measurement_id):
    return next((m for m in frame.get("measurements", []) if m["id"] == measurement_id), None)


def measured_distance(frame, measurement_id):
    item = reading(frame, measurement_id)
    if not item or not item.get("tracking") or frame.get("camera_tracking") != "TRACKING":
        return None
    return number(item.get("distance_m"), "distance_m", optional=True)


def measure_trial(row, session):
    try:
        measurement_id = int(row["measurement_id"])
    except ValueError as error:
        raise ValueError(f"{row['trial_id']}: success requires measurement_id") from error
    completions = [frame for frame in session.frames if
                   (frame.get("command") or {}).get("outcome") == "COMPLETED" and
                   frame["command"].get("completed_measurement_id") == measurement_id]
    if len(completions) != 1:
        raise ValueError(f"{row['trial_id']}: expected exactly one completion for measurement {measurement_id}")
    completed = completions[0]
    distance = measured_distance(completed, measurement_id)
    if distance is None:
        raise ValueError(f"{row['trial_id']}: completion has no tracked result")
    confirmed_cm = distance * 100
    result = {"measured_cm": confirmed_cm, "signed_error_cm": confirmed_cm - row["reference_cm"]}
    result["absolute_error_cm"] = abs(result["signed_error_cm"])
    result["relative_error_percent"] = result["absolute_error_cm"] / row["reference_cm"] * 100
    # Require the first NEW camera frame at/after the observation time, within a documented 1s window.
    # A lost tracking frame at the checkpoint remains missing; never select a later recovered result.
    for seconds in (10, 30):
        checkpoint = completed["observed_elapsed_ns"] + seconds * 1_000_000_000
        candidate = next((frame for frame in session.frames if
                          checkpoint <= frame["observed_elapsed_ns"] <= checkpoint + 1_000_000_000
                          and frame.get("camera_frame_is_new") is True
                          and frame["frame_timestamp_ns"] > completed["frame_timestamp_ns"]), None)
        value = measured_distance(candidate, measurement_id) if candidate else None
        result[f"drift_{seconds}s_cm"] = value * 100 - confirmed_cm if value is not None else None
        result[f"observed_{seconds}s_at_s"] = ((candidate["observed_elapsed_ns"] - completed["observed_elapsed_ns"]) / 1e9
                                               if candidate and value is not None else None)
    return result


def load_trials(path: Path, sessions):
    with path.open(newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        missing = set(FIELDS) - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f"Missing CSV columns: {', '.join(sorted(missing))}")
        rows = [{key: value.strip() if isinstance(value, str) else value for key, value in row.items()} for row in reader]
    if not rows:
        raise ValueError("No physical trials recorded; an empty template is not an accuracy report")
    seen_trials, seen_measurements = set(), set()
    for row in rows:
        for field in ("trial_id", "cohort", "algorithm", "scenario", "support", "model", "depth_mode", "lighting", "surface"):
            if not row[field]:
                raise ValueError(f"Missing {field} in trial {row.get('trial_id')}")
        if row["trial_id"] in seen_trials:
            raise ValueError(f"Duplicate trial {row['trial_id']}")
        seen_trials.add(row["trial_id"])
        if row["cohort"] not in ("pilot", "validation") or row["support"] not in ("candidate", "unsupported"):
            raise ValueError(f"{row['trial_id']}: invalid cohort/support")
        if row["status"] not in ("success", "refused", "aborted"):
            raise ValueError(f"{row['trial_id']}: status must be success, refused or aborted; pending attempts must be resolved")
        for field in ("reference_cm", "camera_distance_cm"):
            row[field] = number(row[field], field, positive=True)
        row["reference_uncertainty_cm"] = number(row["reference_uncertainty_cm"], "reference_uncertainty_cm")
        for field in ("point1_time_s", "point2_time_s", "attempt_time_s"):
            row[field] = number(row[field], field, optional=True)
        if row["status"] != "success" and not row["failure_reason"].strip():
            raise ValueError(f"{row['trial_id']}: unsuccessful attempt requires failure_reason")
        session = sessions.get(row["session_id"])
        if row["session_id"] and not session:
            raise ValueError(f"{row['trial_id']}: unknown session_id")
        if session:
            for key in ("model", "algorithm", "depth_mode"):
                if row[key] != session.metadata.get(key):
                    raise ValueError(f"{row['trial_id']}: CSV {key} differs from journal")
        row["journal_warnings"] = session.warnings if session else ["No AR session: failure recorded manually"]
        if row["status"] == "success":
            if not session:
                raise ValueError(f"{row['trial_id']}: successful trial requires session diagnostics")
            try:
                row["measurement_id"] = str(int(row["measurement_id"]))
            except ValueError as error:
                raise ValueError(f"{row['trial_id']}: success requires measurement_id") from error
            identity = (row["session_id"], row["measurement_id"])
            if identity in seen_measurements:
                raise ValueError(f"{row['trial_id']}: one completed measurement cannot count as multiple trials")
            seen_measurements.add(identity)
            row.update(measure_trial(row, session))
        else:
            if row["measurement_id"]:
                raise ValueError(f"{row['trial_id']}: unsuccessful trial must not name a completed measurement")
            row.update({field: None for field in ("measured_cm", "signed_error_cm", "absolute_error_cm", "relative_error_percent",
                                                 "drift_10s_cm", "drift_30s_cm", "observed_10s_at_s", "observed_30s_at_s")})
    return rows


GROUP_FIELDS = ("cohort", "scenario", "support", "model", "depth_mode", "reference_cm", "reference_uncertainty_cm",
                "camera_distance_cm", "lighting", "surface")


def aggregate(rows):
    groups = defaultdict(list)
    for row in rows:
        groups[tuple(row[field] for field in GROUP_FIELDS) + (row["algorithm"],)].append(row)
    output = []
    for key, trials in sorted(groups.items()):
        successful = [row for row in trials if row["status"] == "success"]
        def values(field):
            return [row[field] for row in trials if row.get(field) is not None]
        result = dict(zip(GROUP_FIELDS + ("algorithm",), key))
        result.update(attempts=len(trials), successes=len(successful),
                      refused=sum(row["status"] == "refused" for row in trials),
                      aborted=sum(row["status"] == "aborted" for row in trials),
                      sessions=len({row["session_id"] for row in trials if row["session_id"]}),
                      success_rate=len(successful) / len(trials),
                      median_absolute_error_cm=median(values("absolute_error_cm")),
                      p95_absolute_error_cm=percentile(values("absolute_error_cm")),
                      mean_signed_error_cm=statistics.mean(values("signed_error_cm")) if successful else None,
                      median_relative_error_percent=median(values("relative_error_percent")),
                      p95_relative_error_percent=percentile(values("relative_error_percent")))
        for field in ("point1_time_s", "point2_time_s", "attempt_time_s", "drift_10s_cm", "drift_30s_cm"):
            result[f"{field}_count"] = len(values(field))
            result[f"{field}_median"] = median(values(field))
            result[f"{field}_p95_absolute"] = percentile([abs(value) for value in values(field)])
        warnings = {warning for row in trials for warning in row["journal_warnings"]}
        if len(successful) < (30 if result["cohort"] == "validation" else 10):
            warnings.add("Too few successful repeats for the planned cohort")
        if result["sessions"] < 2:
            warnings.add("Only one/no AR session: repeat in new sessions")
        for field in ("point1_time_s", "point2_time_s", "attempt_time_s"):
            if len(values(field)) < len(trials):
                warnings.add(f"Missing timing data: {field}")
        for seconds in (10, 30):
            if len(values(f"drift_{seconds}s_cm")) < len(successful):
                warnings.add(f"Missing tracked observations at {seconds}s")
        result["warnings"] = sorted(warnings)
        output.append(result)
    return output


def compare(groups):
    comparisons = []
    by_conditions = defaultdict(dict)
    for group in groups:
        by_conditions[tuple(group[field] for field in GROUP_FIELDS)][group["algorithm"]] = group
    for variants in by_conditions.values():
        baseline = variants.get("baseline_hit_test_v1")
        if not baseline:
            continue
        for algorithm, after in variants.items():
            if algorithm == "baseline_hit_test_v1":
                continue
            before_error, after_error = baseline["median_absolute_error_cm"], after["median_absolute_error_cm"]
            ratio = after_error / before_error if before_error and after_error is not None else None
            comparisons.append({**{key: after[key] for key in GROUP_FIELDS}, "algorithm": algorithm,
                                "median_error_after_divided_by_before": ratio,
                                "success_rate_change": after["success_rate"] - baseline["success_rate"],
                                "before": baseline, "after": after,
                                "gate": "UNVERIFIED: review absolute tolerance, large errors, failure rate and latency on physical validation data"})
    return comparisons


def journal_coverage(sessions, trials):
    """Expose unassigned completions and rejected taps; neither proves all physical attempts were logged."""
    assigned = {(row["session_id"], int(row["measurement_id"])) for row in trials if row["status"] == "success"}
    coverage = []
    for sid, session in sessions.items():
        outcomes = defaultdict(int)
        completed = set()
        for frame in session.frames:
            command = frame.get("command") or {}
            if command.get("type") == "place":
                outcomes[command["outcome"]] += 1
            if command.get("outcome") == "COMPLETED":
                completed.add(command["completed_measurement_id"])
        coverage.append({"session_id": sid, "frames_logged": len(session.frames),
                         "placement_outcomes": dict(outcomes),
                         "unassigned_measurement_ids": sorted(mid for mid in completed if (sid, mid) not in assigned),
                         "warnings": session.warnings})
    return coverage


def display(value):
    if value is None:
        return "—"
    if isinstance(value, float):
        return f"{value:.3f}"
    return str(value).replace("|", "\\|").replace("\n", " ").replace("\r", " ")


def markdown(groups, comparisons, coverage=None):
    lines = ["# Rulivo: отчёт по замерам", "",
             "Отказы и прерванные попытки входят в знаменатель. Ошибки рассчитаны по успешным замерам. "
             "p95 — выборочный процентиль с линейной интерполяцией; он не подтверждает универсальный допуск.", "",
             "| Сценарий / условия | Алгоритм | Успех / попытки | Отказы / прерваны | Сессии | Медиана / p95 ошибки, см | Смещение, см | Медиана / p95 относит. ошибки, % |",
             "|---|---|---:|---:|---:|---:|---:|---:|"]
    for g in groups:
        conditions = "; ".join(f"{field}={display(g[field])}" for field in GROUP_FIELDS)
        lines.append(f"| {conditions} | {display(g['algorithm'])} | {g['successes']}/{g['attempts']} | "
                     f"{g['refused']}/{g['aborted']} | {g['sessions']} | "
                     f"{display(g['median_absolute_error_cm'])} / {display(g['p95_absolute_error_cm'])} | "
                     f"{display(g['mean_signed_error_cm'])} | {display(g['median_relative_error_percent'])} / "
                     f"{display(g['p95_relative_error_percent'])} |")
    lines += ["", "## Задержка и дрейф", "",
              "Дрейф = последующее значение минус значение подтверждения. Окна: [10, 11] и [30, 31] секунд. "
              "Нет свежего tracking в окне — нет значения; пропуск не заменяется нулём.", "",
              "| Сценарий / алгоритм / аппарат | Показатель | Наблюдений | Медиана | p95 модуля |",
              "|---|---|---:|---:|---:|"]
    for g in groups:
        for field in ("point1_time_s", "point2_time_s", "attempt_time_s", "drift_10s_cm", "drift_30s_cm"):
            lines.append(f"| {display(g['scenario'])} / {display(g['algorithm'])} / {display(g['model'])} | {field} | "
                         f"{g[field + '_count']} | {display(g[field + '_median'])} | {display(g[field + '_p95_absolute'])} |")
    lines += ["", "## До / после", ""]
    if not comparisons:
        lines += ["Сопоставимых пар «исходный / новый алгоритм» пока нет. Улучшение не установлено."]
    else:
        lines += ["| Сценарий / аппарат | Новый алгоритм | Медиана ошибки после / до | Изменение доли успеха |",
                  "|---|---|---:|---:|"]
        for c in comparisons:
            lines.append(f"| {display(c['scenario'])} / {display(c['model'])} | {display(c['algorithm'])} | "
                         f"{display(c['median_error_after_divided_by_before'])} | {display(c['success_rate_change'])} |")
        lines += ["", "Условия, обе сводки, задержки и p95 каждой пары сохранены в report.json. "
                  "Приемлемую долю успеха, задержку и абсолютный допуск назначают до итоговой проверки."]
    lines += ["", "## Ограничения данных", ""]
    for item in coverage or []:
        if item["unassigned_measurement_ids"]:
            lines.append(f"- Сессия {display(item['session_id'])}: в журнале есть завершённые замеры без строки CSV: "
                         f"{display(item['unassigned_measurement_ids'])}. Полнота испытаний не подтверждена.")
    for g in groups:
        for warning in g["warnings"]:
            lines.append(f"- {display(g['scenario'])} / {display(g['algorithm'])}: {warning}")
    lines += ["", "`support=unsupported` — отдельные неподдерживаемые случаи. Даже отсутствие предупреждений "
              "не означает подтверждённой точности: нужны новая выборка, второй аппарат и проверка условий.", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--logs", type=Path, required=True)
    parser.add_argument("--trials", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        sessions = read_sessions(args.logs)
        trials = load_trials(args.trials, sessions)
        groups = aggregate(trials)
        comparisons = compare(groups)
        coverage = journal_coverage(sessions, trials)
    except (ValueError, KeyError, OSError) as error:
        parser.error(str(error))
    args.out.mkdir(parents=True, exist_ok=True)
    payload = {"accuracy_claim": "UNVERIFIED", "trials": trials, "groups": groups, "comparisons": comparisons,
               "journal_coverage": coverage,
               "contains_synthetic_test_data": any(s.metadata.get("synthetic_test_only") for s in sessions.values())}
    (args.out / "report.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    (args.out / "report.md").write_text(markdown(groups, comparisons, coverage), encoding="utf-8")
    with (args.out / "trials.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(trials[0]))
        writer.writeheader()
        writer.writerows(trials)
    print(args.out / "report.md")


if __name__ == "__main__":
    main()
