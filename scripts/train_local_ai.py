#!/usr/bin/env python3
"""
Train the v9 local model from training-data CSV and export
scripts/scam-screener-local-ai-model.json format.

Usage:
  python scripts/train_local_ai.py --data scripts/scam-screener-training-data.csv --out scripts/scam-screener-local-ai-model.json
"""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

from sklearn.linear_model import LogisticRegression


DENSE_FEATURE_NAMES = [
    "kw_payment",
    "kw_account",
    "kw_urgency",
    "kw_trust",
    "kw_too_good",
    "kw_platform",
    "has_link",
    "has_suspicious_punctuation",
    "ctx_pushes_external_platform",
    "ctx_demands_upfront_payment",
    "ctx_requests_sensitive_data",
    "ctx_claims_middleman_without_proof",
    "ctx_too_good_to_be_true",
    "ctx_repeated_contact_3plus",
    "ctx_is_spam",
    "ctx_asks_for_stuff",
    "ctx_advertising",
    "intent_offer",
    "intent_rep",
    "intent_redirect",
    "intent_instruction",
    "intent_payment",
    "intent_anchor",
    "funnel_step_norm",
    "funnel_sequence_norm",
    "funnel_full_chain",
    "funnel_partial_chain",
    "rapid_followup",
    "channel_pm",
    "channel_party",
    "channel_public",
    "rule_hits_norm",
    "similarity_hits_norm",
    "behavior_hits_norm",
    "trend_hits_norm",
    "funnel_hits_norm",
]

FUNNEL_DENSE_FEATURE_NAMES = [
    "ctx_pushes_external_platform",
    "ctx_repeated_contact_3plus",
    "intent_offer",
    "intent_rep",
    "intent_redirect",
    "intent_instruction",
    "intent_payment",
    "intent_anchor",
    "funnel_step_norm",
    "funnel_sequence_norm",
    "funnel_full_chain",
    "funnel_partial_chain",
    "rapid_followup",
    "funnel_hits_norm",
]
FUNNEL_DENSE_INDEXES = [DENSE_FEATURE_NAMES.index(name) for name in FUNNEL_DENSE_FEATURE_NAMES]

PAYMENT_WORDS = ("pay", "payment", "vorkasse", "coins", "money", "btc", "crypto")
ACCOUNT_WORDS = ("password", "passwort", "2fa", "code", "email", "login")
URGENCY_WORDS = ("now", "quick", "fast", "urgent", "sofort", "jetzt")
TRUST_WORDS = ("trust", "legit", "safe", "trusted", "middleman")
TOO_GOOD_WORDS = ("free", "100%", "guaranteed", "garantiert", "dupe", "rank")
PLATFORM_WORDS = ("discord", "telegram", "t.me", "server", "dm", "vc", "voice")


def _bool(v: str) -> float:
    if v is None:
        return 0.0
    t = v.strip().lower()
    if not t:
        return 0.0
    if t in ("true", "yes"):
        return 1.0
    try:
        return 1.0 if int(t) > 0 else 0.0
    except ValueError:
        return 0.0


def _int(v: str, fallback: int = 0) -> int:
    try:
        return int((v or "").strip())
    except ValueError:
        return fallback


def _float(v: str, fallback: float = 0.0) -> float:
    try:
        return float((v or "").strip())
    except ValueError:
        return fallback


def _has_any(msg: str, words: tuple[str, ...]) -> float:
    return 1.0 if any(w in msg for w in words) else 0.0


def _norm(value: float, cap: float) -> float:
    if cap <= 0:
        return 0.0
    out = value / cap
    if out < 0:
        return 0.0
    if out > 1:
        return 1.0
    return out


def _features(row: dict[str, str]) -> list[float]:
    msg = (row.get("message") or "").lower()
    delta_ms = _float(row.get("delta_ms"), 0.0)
    channel = (row.get("channel") or "unknown").strip().lower()
    repeated = _int(row.get("repeated_contact_attempts"), 0)

    rapid_followup = 0.0 if delta_ms <= 0 else 1.0 - _norm(delta_ms, 120000.0)

    return [
        _has_any(msg, PAYMENT_WORDS),
        _has_any(msg, ACCOUNT_WORDS),
        _has_any(msg, URGENCY_WORDS),
        _has_any(msg, TRUST_WORDS),
        _has_any(msg, TOO_GOOD_WORDS),
        _has_any(msg, PLATFORM_WORDS),
        1.0 if ("http://" in msg or "https://" in msg or "www." in msg) else 0.0,
        1.0 if ("!!!" in msg or "??" in msg or "$$" in msg) else 0.0,
        _bool(row.get("pushes_external_platform")),
        _bool(row.get("demands_upfront_payment")),
        _bool(row.get("requests_sensitive_data")),
        _bool(row.get("claims_middleman_without_proof")),
        _bool(row.get("too_good_to_be_true")),
        1.0 if repeated >= 3 else 0.0,
        _bool(row.get("is_spam")),
        _bool(row.get("asks_for_stuff")),
        _bool(row.get("advertising")),
        _bool(row.get("intent_offer")),
        _bool(row.get("intent_rep")),
        _bool(row.get("intent_redirect")),
        _bool(row.get("intent_instruction")),
        _bool(row.get("intent_payment")),
        _bool(row.get("intent_anchor")),
        _norm(_float(row.get("funnel_step_index"), 0.0), 4.0),
        _norm(_float(row.get("funnel_sequence_score"), 0.0), 40.0),
        _bool(row.get("funnel_full_chain")),
        _bool(row.get("funnel_partial_chain")),
        rapid_followup,
        1.0 if channel == "pm" else 0.0,
        1.0 if channel == "party" else 0.0,
        1.0 if channel == "public" else 0.0,
        _norm(_float(row.get("rule_hits"), 0.0), 3.0),
        _norm(_float(row.get("similarity_hits"), 0.0), 2.0),
        _norm(_float(row.get("behavior_hits"), 0.0), 3.0),
        _norm(_float(row.get("trend_hits"), 0.0), 2.0),
        _norm(_float(row.get("funnel_hits"), 0.0), 2.0),
    ]


def _funnel_label(row: dict[str, str]) -> int:
    explicit = row.get("funnel_label")
    if explicit is not None and str(explicit).strip() != "":
        return 1 if _bool(str(explicit)) > 0.0 else 0

    if _bool(row.get("funnel_full_chain")) > 0.0 or _bool(row.get("funnel_partial_chain")) > 0.0:
        return 1
    if _float(row.get("funnel_step_index"), 0.0) > 0.0:
        return 1
    if _float(row.get("funnel_sequence_score"), 0.0) > 0.0:
        return 1
    if _float(row.get("funnel_hits"), 0.0) > 0.0:
        return 1
    return 0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    with args.data.open("r", encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))

    if not rows:
        raise SystemExit("No rows found in training data.")

    x: list[list[float]] = []
    y: list[int] = []
    funnel_y: list[int] = []
    sample_weight: list[float] = []

    for row in rows:
        label_raw = (row.get("label") or "").strip()
        if label_raw not in ("0", "1"):
            continue
        x.append(_features(row))
        y.append(int(label_raw))
        funnel_y.append(_funnel_label(row))
        sample_weight.append(_float(row.get("sample_weight"), 1.0))

    if len(x) < 12:
        raise SystemExit("Not enough usable rows (need at least 12).")
    if not (0 in y and 1 in y):
        raise SystemExit("Training data must contain both labels 0 and 1.")

    model = LogisticRegression(max_iter=2500)
    model.fit(x, y, sample_weight=sample_weight)
    funnel_x = [[row[idx] for idx in FUNNEL_DENSE_INDEXES] for row in x]
    funnel_target = funnel_y if (0 in funnel_y and 1 in funnel_y) else y
    funnel_model = LogisticRegression(max_iter=2500)
    funnel_model.fit(funnel_x, funnel_target, sample_weight=sample_weight)

    dense_weights = {
        name: float(weight) for name, weight in zip(DENSE_FEATURE_NAMES, model.coef_[0])
    }
    funnel_dense_weights = {
        name: float(weight) for name, weight in zip(FUNNEL_DENSE_FEATURE_NAMES, funnel_model.coef_[0])
    }

    out = {
        "version": 9,
        "intercept": float(model.intercept_[0]),
        "denseFeatureWeights": dense_weights,
        "tokenWeights": {},
        "funnelHead": {
            "intercept": float(funnel_model.intercept_[0]),
            "denseFeatureWeights": funnel_dense_weights,
        },
    }

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(out, indent=2), encoding="utf-8")
    print(f"Wrote model to {args.out}")


if __name__ == "__main__":
    main()
