//! P7-C desktop boundary: no Tauri command, browser callback, HTTP client, or remote capability.
//! It only exposes typed disabled/configured state for a later explicitly-authorized OAuth owner.

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PublicConfig {
    pub supabase_url: String,
    pub publishable_key: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Availability {
    Disabled,
    Configured(PublicConfig),
}

pub fn resolve_private_config<F: Fn(&str) -> Option<String>>(read: F) -> Availability {
    let url = read("NANFENG_SUPABASE_URL").unwrap_or_default();
    let key = read("NANFENG_SUPABASE_PUBLISHABLE_KEY").unwrap_or_default();
    if url.starts_with("https://") && !url.contains('@') && !key.trim().is_empty() {
        Availability::Configured(PublicConfig {
            supabase_url: url,
            publishable_key: key,
        })
    } else {
        Availability::Disabled
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum GatewayResult<T> {
    Value(T),
    Disabled,
    Rejected(&'static str),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EnvelopeReceipt {
    pub revision: u64,
    pub payload_hash: String,
}

/// A future authenticated transport owns transient browser/OAuth session material. P7-C does not.
pub trait CloudGateway {
    fn read(&self, document_id: &str, minimum_revision: u64) -> GatewayResult<String>;
    fn commit(
        &self,
        expected_revision: u64,
        canonical_p7a_envelope: &str,
    ) -> GatewayResult<EnvelopeReceipt>;
}

pub struct DisabledCloudGateway;
impl CloudGateway for DisabledCloudGateway {
    fn read(&self, _document_id: &str, _minimum_revision: u64) -> GatewayResult<String> {
        GatewayResult::Disabled
    }
    fn commit(
        &self,
        _expected_revision: u64,
        _canonical_p7a_envelope: &str,
    ) -> GatewayResult<EnvelopeReceipt> {
        GatewayResult::Disabled
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn absent_private_config_and_gateway_are_offline_safe() {
        assert_eq!(resolve_private_config(|_| None), Availability::Disabled);
        assert_eq!(
            DisabledCloudGateway.commit(0, "not-read"),
            GatewayResult::Disabled
        );
    }

    #[test]
    fn only_complete_private_public_config_is_marked_configured() {
        let result = resolve_private_config(|name| match name {
            "NANFENG_SUPABASE_URL" => Some("https://example.invalid".into()),
            "NANFENG_SUPABASE_PUBLISHABLE_KEY" => Some("public-value".into()),
            _ => None,
        });
        assert!(matches!(result, Availability::Configured(_)));
    }
}
