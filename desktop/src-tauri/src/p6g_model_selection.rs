//! P6-G local model-selection owner.  This module is intentionally credential-, prompt-,
//! transport- and invocation-free: it only validates a caller-supplied local catalog snapshot
//! and produces safe selection/route metadata.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ModelTier {
    Fast,
    Balanced,
    Deep,
    ApexReview,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ProviderFamily {
    Local,
    Anthropic,
    Openai,
    Other,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Capability {
    Text,
    Vision,
    Code,
    Tool,
    StructuredOutput,
    LongContext,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum RouteSource {
    ExactHistoricalCache,
    LocalSafe,
    ManualOverride,
    Auto,
    RequiresConfirmation,
    Rejected,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum RouteReason {
    ExactCacheHit,
    LocalSafetyGate,
    ManualOverride,
    AutoAnthropicPreferred,
    AutoExplicitFallback,
    UnknownCostRequiresConfirmation,
    NoEligibleCandidate,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum CandidateRejectionReason {
    Unavailable,
    Capability,
    ContextLimit,
    Budget,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct CandidateRejection {
    pub model_id: String,
    pub reason: CandidateRejectionReason,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct CatalogCandidate {
    pub provider_family: ProviderFamily,
    pub provider_id: String,
    pub model_id: String,
    pub display_name: String,
    pub tiers: Vec<ModelTier>,
    pub capabilities: Vec<Capability>,
    pub available: bool,
    pub known_cost_micros: Option<u64>,
    pub latency_rank: u32,
    pub context_window_tokens: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct CatalogSnapshot {
    pub catalog_version: String,
    pub policy_version: u64,
    pub candidates: Vec<CatalogCandidate>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct GlobalDefault {
    pub revision: u64,
    pub tier: Option<ModelTier>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ConversationOverride {
    pub conversation_id: String,
    pub revision: u64,
    pub model_id: Option<String>,
    #[serde(default)]
    pub tone_override: Option<String>,
    #[serde(default)]
    pub web_search_override: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct RouteRequest {
    pub tier: ModelTier,
    #[serde(default = "default_capabilities")]
    pub required_capabilities: Vec<Capability>,
    #[serde(default)]
    pub exact_historical_cache_hit: bool,
    #[serde(default)]
    pub local_safe_required: bool,
    #[serde(default)]
    pub unknown_cost_confirmed: bool,
    pub context_tokens: Option<u64>,
    pub budget_micros: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct RouteDecision {
    pub source: RouteSource,
    pub reason: RouteReason,
    pub candidate: Option<CatalogCandidate>,
    pub rejected_model_ids: Vec<String>,
    pub rejected_candidates: Vec<CandidateRejection>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct RouteMetadata {
    pub conversation_id: String,
    pub policy_version: u64,
    pub catalog_version: String,
    pub tier: ModelTier,
    pub source: RouteSource,
    pub reason: RouteReason,
    pub model_id: Option<String>,
    pub display_name: Option<String>,
    pub rejected_candidates: Vec<CandidateRejection>,
}

fn default_capabilities() -> Vec<Capability> {
    vec![Capability::Text]
}

pub fn empty_catalog() -> CatalogSnapshot {
    CatalogSnapshot {
        catalog_version: "local-unconfigured-v1".into(),
        policy_version: 1,
        candidates: vec![],
    }
}

pub fn validate_catalog(
    snapshot: &CatalogSnapshot,
    id_is_valid: impl Fn(&str) -> bool,
) -> Result<(), &'static str> {
    if snapshot.catalog_version.trim().is_empty()
        || snapshot.catalog_version.len() > 120
        || snapshot.policy_version == 0
    {
        return Err("catalog 版本无效");
    }
    let mut seen = std::collections::BTreeSet::new();
    for item in &snapshot.candidates {
        if !id_is_valid(&item.provider_id)
            || !id_is_valid(&item.model_id)
            || item.display_name.trim().is_empty()
            || item.display_name.chars().count() > 120
            || item.tiers.is_empty()
            || item.capabilities.is_empty()
            || !seen.insert(&item.model_id)
        {
            return Err("catalog candidate 无效");
        }
        if item.context_window_tokens == Some(0) {
            return Err("catalog context window 无效");
        }
    }
    Ok(())
}

pub fn route(
    request: &RouteRequest,
    manual_model_id: Option<&str>,
    catalog: &[CatalogCandidate],
) -> RouteDecision {
    if request.exact_historical_cache_hit {
        return decision(
            RouteSource::ExactHistoricalCache,
            RouteReason::ExactCacheHit,
            None,
            vec![],
            vec![],
        );
    }
    if request.local_safe_required {
        return decision(
            RouteSource::LocalSafe,
            RouteReason::LocalSafetyGate,
            None,
            vec![],
            vec![],
        );
    }
    let rejections = catalog
        .iter()
        .filter_map(|candidate| {
            let reason = if !candidate.available {
                Some(CandidateRejectionReason::Unavailable)
            } else if !request
                .required_capabilities
                .iter()
                .all(|capability| candidate.capabilities.contains(capability))
            {
                Some(CandidateRejectionReason::Capability)
            } else if request.context_tokens.is_some_and(|tokens| {
                candidate
                    .context_window_tokens
                    .is_none_or(|limit| limit < tokens)
            }) {
                Some(CandidateRejectionReason::ContextLimit)
            } else if request.budget_micros.is_some_and(|budget| {
                candidate
                    .known_cost_micros
                    .is_some_and(|cost| cost > budget)
            }) {
                Some(CandidateRejectionReason::Budget)
            } else {
                None
            };
            reason.map(|reason| CandidateRejection {
                model_id: candidate.model_id.clone(),
                reason,
            })
        })
        .collect::<Vec<_>>();
    let capable = catalog
        .iter()
        .filter(|candidate| {
            candidate.available
                && request
                    .required_capabilities
                    .iter()
                    .all(|capability| candidate.capabilities.contains(capability))
                && request.context_tokens.is_none_or(|tokens| {
                    candidate
                        .context_window_tokens
                        .is_some_and(|limit| limit >= tokens)
                })
                && request.budget_micros.is_none_or(|budget| {
                    candidate
                        .known_cost_micros
                        .is_none_or(|cost| cost <= budget)
                })
        })
        .cloned()
        .collect::<Vec<_>>();
    if let Some(manual) = manual_model_id {
        return capable
            .into_iter()
            .find(|item| item.model_id == manual)
            .map(|candidate| {
                decision(
                    RouteSource::ManualOverride,
                    RouteReason::ManualOverride,
                    Some(candidate),
                    vec![],
                    rejections.clone(),
                )
            })
            .unwrap_or_else(|| {
                decision(
                    RouteSource::Rejected,
                    RouteReason::NoEligibleCandidate,
                    None,
                    catalog.iter().map(|item| item.model_id.clone()).collect(),
                    rejections,
                )
            });
    }
    let tiered = capable
        .into_iter()
        .filter(|item| item.tiers.contains(&request.tier))
        .collect::<Vec<_>>();
    if tiered.is_empty() {
        return decision(
            RouteSource::Rejected,
            RouteReason::NoEligibleCandidate,
            None,
            catalog.iter().map(|item| item.model_id.clone()).collect(),
            rejections,
        );
    }
    let known = tiered
        .iter()
        .filter(|item| item.known_cost_micros.is_some())
        .cloned()
        .collect::<Vec<_>>();
    if known.is_empty() && !request.unknown_cost_confirmed {
        return decision(
            RouteSource::RequiresConfirmation,
            RouteReason::UnknownCostRequiresConfirmation,
            None,
            tiered.iter().map(|item| item.model_id.clone()).collect(),
            rejections,
        );
    }
    let eligible = if known.is_empty() { tiered } else { known };
    let preferred = eligible
        .iter()
        .filter(|item| item.provider_family == ProviderFamily::Anthropic)
        .cloned()
        .collect::<Vec<_>>();
    let mut choice = if preferred.is_empty() {
        eligible
    } else {
        preferred
    };
    choice.sort_by(|left, right| {
        left.known_cost_micros
            .unwrap_or(u64::MAX)
            .cmp(&right.known_cost_micros.unwrap_or(u64::MAX))
            .then(left.latency_rank.cmp(&right.latency_rank))
            .then(left.model_id.cmp(&right.model_id))
    });
    let candidate = choice.remove(0);
    let reason = if candidate.provider_family == ProviderFamily::Anthropic {
        RouteReason::AutoAnthropicPreferred
    } else {
        RouteReason::AutoExplicitFallback
    };
    decision(
        RouteSource::Auto,
        reason,
        Some(candidate),
        vec![],
        rejections,
    )
}

fn decision(
    source: RouteSource,
    reason: RouteReason,
    candidate: Option<CatalogCandidate>,
    rejected_model_ids: Vec<String>,
    rejected_candidates: Vec<CandidateRejection>,
) -> RouteDecision {
    RouteDecision {
        source,
        reason,
        candidate,
        rejected_model_ids,
        rejected_candidates,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn candidate(family: ProviderFamily, id: &str, cost: Option<u64>) -> CatalogCandidate {
        CatalogCandidate {
            provider_family: family,
            provider_id: "fixture".into(),
            model_id: id.into(),
            display_name: id.into(),
            tiers: vec![ModelTier::Balanced],
            capabilities: vec![Capability::Text],
            available: true,
            known_cost_micros: cost,
            latency_rank: 1,
            context_window_tokens: Some(8192),
        }
    }

    #[test]
    fn gates_and_manual_override_never_fall_through() {
        let catalog = vec![candidate(
            ProviderFamily::Anthropic,
            "anthropic.fixture",
            Some(3),
        )];
        assert_eq!(
            route(
                &RouteRequest {
                    tier: ModelTier::Balanced,
                    required_capabilities: vec![Capability::Text],
                    exact_historical_cache_hit: true,
                    local_safe_required: false,
                    unknown_cost_confirmed: false,
                    context_tokens: None,
                    budget_micros: None
                },
                None,
                &catalog
            )
            .reason,
            RouteReason::ExactCacheHit
        );
        assert_eq!(
            route(
                &RouteRequest {
                    tier: ModelTier::Balanced,
                    required_capabilities: vec![Capability::Text],
                    exact_historical_cache_hit: false,
                    local_safe_required: true,
                    unknown_cost_confirmed: false,
                    context_tokens: None,
                    budget_micros: None
                },
                None,
                &catalog
            )
            .reason,
            RouteReason::LocalSafetyGate
        );
        assert_eq!(
            route(
                &RouteRequest {
                    tier: ModelTier::Balanced,
                    required_capabilities: vec![Capability::Text],
                    exact_historical_cache_hit: false,
                    local_safe_required: false,
                    unknown_cost_confirmed: false,
                    context_tokens: None,
                    budget_micros: None
                },
                Some("missing"),
                &catalog
            )
            .source,
            RouteSource::Rejected
        );
    }

    #[test]
    fn anthropic_wins_and_unknown_cost_needs_confirmation() {
        let catalog = vec![
            candidate(ProviderFamily::Openai, "openai.fixture", Some(1)),
            candidate(ProviderFamily::Anthropic, "anthropic.fixture", Some(9)),
        ];
        let request = RouteRequest {
            tier: ModelTier::Balanced,
            required_capabilities: vec![Capability::Text],
            exact_historical_cache_hit: false,
            local_safe_required: false,
            unknown_cost_confirmed: false,
            context_tokens: None,
            budget_micros: None,
        };
        assert_eq!(
            route(&request, None, &catalog).candidate.unwrap().model_id,
            "anthropic.fixture"
        );
        assert_eq!(
            route(
                &request,
                None,
                &[candidate(
                    ProviderFamily::Anthropic,
                    "unknown.fixture",
                    None
                )]
            )
            .reason,
            RouteReason::UnknownCostRequiresConfirmation
        );
    }
}
