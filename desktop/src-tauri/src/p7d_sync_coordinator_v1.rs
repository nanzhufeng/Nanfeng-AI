//! P7-D Desktop coordinator contract. No HTTP, Tauri command, OAuth callback, or session storage.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Gate {
    pub configured: bool,
    pub verified_session: bool,
    pub ready: bool,
    pub recovery_confirmed: bool,
    pub direction_confirmed: bool,
}
impl Gate {
    pub fn allowed(self) -> bool {
        self.configured
            && self.verified_session
            && self.ready
            && self.recovery_confirmed
            && self.direction_confirmed
    }
}
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Decision {
    Disabled,
    ScheduleDelayed,
    EnsurePeriodic,
    Conflict,
    Cancelled,
}
pub fn on_business_mutation(gate: Gate) -> Decision {
    if gate.allowed() {
        Decision::ScheduleDelayed
    } else {
        Decision::Disabled
    }
}
pub fn on_cold_start_or_account_page(gate: Gate) -> Decision {
    if gate.allowed() {
        Decision::EnsurePeriodic
    } else {
        Decision::Disabled
    }
}
pub fn remote_changed_with_pending_local_work() -> Decision {
    Decision::Conflict
}
pub fn sign_out_or_switch() -> Decision {
    Decision::Cancelled
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn desktop_offline_entry_never_schedules_or_opens_network() {
        let off = Gate {
            configured: false,
            verified_session: false,
            ready: false,
            recovery_confirmed: false,
            direction_confirmed: false,
        };
        assert_eq!(on_business_mutation(off), Decision::Disabled);
        assert_eq!(on_cold_start_or_account_page(off), Decision::Disabled);
        assert_eq!(sign_out_or_switch(), Decision::Cancelled);
    }
}
