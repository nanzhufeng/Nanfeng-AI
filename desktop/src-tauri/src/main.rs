#![cfg_attr(all(windows, not(debug_assertions)), windows_subsystem = "windows")]

fn main() {
    if nanfeng_ai_desktop_spike::run_pdf_text_worker_if_requested() { return; }
    if nanfeng_ai_desktop_spike::run_web_dns_worker_if_requested() { return; }
    nanfeng_ai_desktop_spike::run()
}
