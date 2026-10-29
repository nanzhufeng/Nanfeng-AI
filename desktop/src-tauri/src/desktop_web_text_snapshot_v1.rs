//! P4O: inert, bounded web-document interpretation. No DNS, HTTP, filesystem or business writes.
//! A transport must validate every DNS answer and pin those answers on each manually followed hop.
use crate::desktop_markdown_knowledge_v1::{validate_candidate, Candidate};
use html5ever::tendril::StrTendril;
use html5ever::{
    parse_document,
    tendril::TendrilSink,
    tree_builder::{ElementFlags, NodeOrText, QuirksMode, TreeSink},
    Attribute, ExpandedName, QualName,
};
use reqwest::Url;
use sha2::{Digest, Sha256};
use std::{
    borrow::Cow,
    cell::{Cell, RefCell},
    io::Read,
    net::IpAddr,
    panic::{catch_unwind, AssertUnwindSafe},
    rc::{Rc, Weak},
};
use std::{future::Future, pin::Pin, time::Duration};

pub(crate) const ADAPTER_VERSION: i64 = 1;
pub(crate) const MAX_REDIRECTS: usize = 3;
pub(crate) const MAX_WIRE_BYTES: usize = 512 * 1024;
pub(crate) const MAX_HTML_BYTES: usize = 1024 * 1024;
const MAX_HEADERS: usize = 16 * 1024;
const MAX_NODES: usize = 12_000;
const MAX_DEPTH: usize = 32;
const MAX_HTML_CHARS: usize = 120_000;
const MAX_TEXT_CHARS: usize = 60_000;
const MAX_TREE_OPERATIONS: usize = 500_000;

// Deliberately no Debug/Serialize: fetch URL may include a non-sensitive query but is ephemeral.
pub(crate) struct SafeUrl {
    url: Url,
    pub display_url: String,
    pub host: String,
}
impl SafeUrl {
    pub(crate) fn fetch_url(&self) -> &Url {
        &self.url
    }
}

pub(crate) fn validate_url(raw: &str) -> Result<SafeUrl, &'static str> {
    if raw.len() > 8192 || raw.trim().chars().any(|c| c.is_control() || c == '\\') {
        return Err("INVALID_URL");
    }
    let trimmed = raw.trim();
    let (_, authority) = trimmed.split_once("://").ok_or("INVALID_URL")?;
    if authority
        .split(['/', '?', '#'])
        .next()
        .is_none_or(|a| a.contains('@'))
    {
        return Err("INVALID_URL");
    }
    let url = Url::parse(raw.trim()).map_err(|_| "INVALID_URL")?;
    if url.scheme() != "https"
        || !url.username().is_empty()
        || url.password().is_some()
        || url.fragment().is_some()
        || url.port_or_known_default() != Some(443)
    {
        return Err("INVALID_URL");
    }
    // URL's canonical host rejects alternate numeric IPv4 notations as well as ordinary literals.
    let host = url.host_str().ok_or("INVALID_URL")?.to_lowercase();
    if host.parse::<IpAddr>().is_ok() {
        return Err("INVALID_URL");
    }
    if host.len() > 253
        || !host.contains('.')
        || host.ends_with('.')
        || host.split('.').any(|l| {
            l.is_empty()
                || l.len() > 63
                || l.starts_with('-')
                || l.ends_with('-')
                || !l.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'-')
        })
        || [
            "localhost",
            "local",
            "internal",
            "invalid",
            "test",
            "example",
            "onion",
        ]
        .iter()
        .any(|suffix| host == *suffix || host.ends_with(&format!(".{suffix}")))
    {
        return Err("INVALID_URL");
    }
    for (key, value) in url.query_pairs() {
        let key = key.to_lowercase().replace('-', "_");
        if [
            "token",
            "key",
            "api_key",
            "apikey",
            "authorization",
            "password",
            "secret",
            "session",
            "code",
            "access_token",
            "refresh_token",
        ]
        .contains(&key.as_str())
            || crate::require_safe_memory_body(&format!("{key}={value}")).is_err()
        {
            return Err("HIGH_SENSITIVITY_URL");
        }
    }
    let display_url = format!("https://{host}{}", url.path());
    crate::require_safe_memory_body(&display_url).map_err(|_| "HIGH_SENSITIVITY_URL")?;
    Ok(SafeUrl {
        url,
        display_url,
        host,
    })
}

pub(crate) fn redirect(
    current: &SafeUrl,
    location: &str,
    redirects_so_far: usize,
) -> Result<SafeUrl, &'static str> {
    if redirects_so_far >= MAX_REDIRECTS {
        return Err("TOO_MANY_REDIRECTS");
    }
    if location.len() > 8192 || location.chars().any(|c| c.is_control() || c == '\\') {
        return Err("REDIRECT_REJECTED");
    }
    if Url::parse(location).is_ok() {
        validate_url(location).map_err(|_| "REDIRECT_REJECTED")?;
    } else if location.strip_prefix("//").is_some_and(|a| {
        a.split(['/', '?', '#'])
            .next()
            .is_some_and(|a| a.contains('@'))
    }) {
        return Err("REDIRECT_REJECTED");
    }
    let next = current
        .url
        .join(location)
        .map_err(|_| "REDIRECT_REJECTED")?;
    validate_url(next.as_str()).map_err(|_| "REDIRECT_REJECTED")
}

pub(crate) fn public_address(ip: IpAddr) -> bool {
    // Conservatively reject special-purpose blocks, including translation/tunnel mechanisms.
    // These are policy decisions, not proof of the routed destination; transport must pin DNS.
    match ip {
        IpAddr::V4(ip) => {
            let [a, b, c, _] = ip.octets();
            !(matches!(a, 0 | 10 | 127)
                || a >= 224
                || (a == 100 && (64..=127).contains(&b))
                || (a == 169 && b == 254)
                || (a == 172 && (16..=31).contains(&b))
                || (a == 192
                    && ((b == 168) || (b == 0 && (c == 0 || c == 2)) || (b == 88 && c == 99)))
                || (a == 198 && ((18..=19).contains(&b) || (b == 51 && c == 100)))
                || (a == 203 && b == 0 && c == 113))
        }
        IpAddr::V6(ip) => {
            let s = ip.segments();
            (s[0] & 0xe000 == 0x2000)
                && !(s[0] == 0x2001 && (s[1] < 0x0200 || s[1] == 0x0db8))
                && s[0] != 0x2002
                && !(s[0] == 0x3fff && s[1] & 0xf000 == 0)
        }
    }
}
pub(crate) fn validate_addresses(addresses: &[IpAddr]) -> Result<(), &'static str> {
    if addresses.is_empty()
        || addresses.len() > 64
        || addresses.iter().any(|ip| !public_address(*ip))
    {
        Err("DNS_NOT_PUBLIC")
    } else {
        Ok(())
    }
}

pub(crate) struct Response {
    pub status: u16,
    pub headers: Vec<(String, String)>,
    pub body: Vec<u8>,
}
pub(crate) type Operation<'a, T> =
    Pin<Box<dyn Future<Output = Result<T, &'static str>> + Send + 'a>>;
/// Implementations must bind the HTTPS socket to these addresses, keep hostname TLS verification,
/// disable proxies/implicit redirects/decompression/cookies, and bound raw headers/body while reading.
/// No implementation is exposed to IPC until those requirements have transport evidence.
pub(crate) trait Transport {
    fn resolve<'a>(&'a mut self, host: &'a str) -> Operation<'a, Vec<IpAddr>>;
    fn request<'a>(
        &'a mut self,
        url: &'a SafeUrl,
        addresses: &'a [IpAddr],
    ) -> Operation<'a, Response>;
}
pub(crate) struct Fetched {
    pub display_url: String,
    pub host: String,
    pub html: Vec<u8>,
    pub snapshot: Snapshot,
}
pub(crate) async fn fetch(
    transport: &mut impl Transport,
    raw_url: &str,
    cancelled: impl Fn() -> bool,
) -> Result<Fetched, &'static str> {
    fetch_with_budget(transport, raw_url, cancelled, Duration::from_secs(12)).await
}
async fn fetch_with_budget(
    transport: &mut impl Transport,
    raw_url: &str,
    cancelled: impl Fn() -> bool,
    budget: Duration,
) -> Result<Fetched, &'static str> {
    fetch_observed(transport, raw_url, cancelled, budget, |_, _, _| Ok(())).await
}
pub(crate) async fn fetch_with_private_copy(
    transport: &mut impl Transport,
    raw_url: &str,
    cancelled: impl Fn() -> bool,
    copy: impl Fn(&str, &str, &[u8]) -> Result<(), &'static str>,
) -> Result<Fetched, &'static str> {
    fetch_observed(transport, raw_url, cancelled, Duration::from_secs(12), copy).await
}
async fn fetch_observed(
    transport: &mut impl Transport,
    raw_url: &str,
    cancelled: impl Fn() -> bool,
    budget: Duration,
    copy: impl Fn(&str, &str, &[u8]) -> Result<(), &'static str>,
) -> Result<Fetched, &'static str> {
    let mut url = validate_url(raw_url)?;
    if cancelled() {
        return Err("CANCELLED");
    }
    let work = async {
        let mut hops = 0;
        loop {
            let addresses = transport.resolve(&url.host).await?;
            if cancelled() {
                return Err("CANCELLED");
            }
            validate_addresses(&addresses)?;
            let response = transport.request(&url, &addresses).await?;
            if cancelled() {
                return Err("CANCELLED");
            }
            // Validate bounded response facts for redirect hops too, not only the final body.
            if response
                .headers
                .iter()
                .try_fold(64usize, |n, (k, v)| n.checked_add(k.len() + v.len() + 4))
                .is_none_or(|n| n > MAX_HEADERS)
                || response.body.len() > MAX_WIRE_BYTES
            {
                return Err("RESPONSE_TOO_LARGE");
            }
            if [301, 302, 303, 307, 308].contains(&response.status) {
                let locations: Vec<_> = response
                    .headers
                    .iter()
                    .filter(|(k, _)| k.eq_ignore_ascii_case("location"))
                    .map(|(_, v)| v.as_str())
                    .collect();
                if locations.len() != 1 {
                    return Err("REDIRECT_REJECTED");
                }
                url = redirect(&url, locations[0], hops)?;
                hops += 1;
                continue;
            }
            let html = response_html(response.status, &response.headers, response.body.as_slice())?;
            copy(&url.display_url, &url.host, &html)?;
            if cancelled() {
                return Err("CANCELLED");
            }
            let snapshot = extract(&html)?;
            if cancelled() {
                return Err("CANCELLED");
            }
            return Ok(Fetched {
                display_url: url.display_url,
                host: url.host,
                html,
                snapshot,
            });
        }
    };
    tokio::select! {
        result=tokio::time::timeout(budget,work)=>result.map_err(|_|"TIMEOUT")?,
        _=async {loop {if cancelled(){break;}tokio::time::sleep(Duration::from_millis(20)).await;}}=>Err("CANCELLED"),
    }
}

pub(crate) fn response_html(
    status: u16,
    headers: &[(String, String)],
    mut input: impl Read,
) -> Result<Vec<u8>, &'static str> {
    if !(200..300).contains(&status) {
        return Err("HTTP_STATUS");
    }
    if headers
        .iter()
        .try_fold(64usize, |n, (k, v)| n.checked_add(k.len() + v.len() + 4))
        .is_none_or(|n| n > MAX_HEADERS)
    {
        return Err("RESPONSE_TOO_LARGE");
    }
    let one = |name: &str| -> Result<Option<&str>, &'static str> {
        let values: Vec<_> = headers
            .iter()
            .filter(|(k, _)| k.eq_ignore_ascii_case(name))
            .map(|(_, v)| v.as_str())
            .collect();
        if values.len() > 1 {
            Err("MALFORMED_RESPONSE")
        } else {
            Ok(values.first().copied())
        }
    };
    let content_type = one("content-type")?.ok_or("CONTENT_TYPE")?;
    let mut parts = content_type.split(';');
    if !parts
        .next()
        .is_some_and(|v| v.trim().eq_ignore_ascii_case("text/html"))
    {
        return Err("CONTENT_TYPE");
    }
    for part in parts {
        let (key, value) = part.trim().split_once('=').ok_or("CONTENT_TYPE")?;
        if key.trim().eq_ignore_ascii_case("charset")
            && !["utf-8", "utf8"].contains(&value.trim().trim_matches('"').to_lowercase().as_str())
        {
            return Err("INVALID_UTF8");
        }
    }
    if let Some(len) = one("content-length")? {
        if len.parse::<usize>().map_err(|_| "MALFORMED_RESPONSE")? > MAX_WIRE_BYTES {
            return Err("RESPONSE_TOO_LARGE");
        }
    }
    let mut raw = Vec::new();
    input
        .by_ref()
        .take(MAX_WIRE_BYTES as u64 + 1)
        .read_to_end(&mut raw)
        .map_err(|_| "NETWORK")?;
    if raw.len() > MAX_WIRE_BYTES {
        return Err("RESPONSE_TOO_LARGE");
    }
    if let Some(len) = one("content-length")? {
        if len.parse::<usize>().map_err(|_| "MALFORMED_RESPONSE")? != raw.len() {
            return Err("MALFORMED_RESPONSE");
        }
    }
    let html = match one("content-encoding")?
        .unwrap_or("identity")
        .trim()
        .to_ascii_lowercase()
        .as_str()
    {
        "identity" => raw,
        "gzip" => {
            let mut decoded = Vec::new();
            flate2::read::MultiGzDecoder::new(raw.as_slice())
                .take(MAX_HTML_BYTES as u64 + 1)
                .read_to_end(&mut decoded)
                .map_err(|_| "CONTENT_ENCODING")?;
            if decoded.len() > MAX_HTML_BYTES {
                return Err("RESPONSE_TOO_LARGE");
            }
            decoded
        }
        _ => return Err("CONTENT_ENCODING"),
    };
    Ok(html)
}

type Handle = Rc<Node>;
struct Node {
    name: Option<QualName>,
    attrs: RefCell<Vec<Attribute>>,
    text: RefCell<String>,
    parent: RefCell<Weak<Node>>,
    children: RefCell<Vec<Handle>>,
    template: RefCell<Option<Handle>>,
}
impl Node {
    fn new(name: Option<QualName>, attrs: Vec<Attribute>, text: String) -> Handle {
        Rc::new(Self {
            name,
            attrs: RefCell::new(attrs),
            text: RefCell::new(text),
            parent: RefCell::new(Weak::new()),
            children: RefCell::new(vec![]),
            template: RefCell::new(None),
        })
    }
}
struct BoundedTree {
    document: Handle,
    nodes: Cell<usize>,
    operations: Cell<usize>,
    malformed: Cell<bool>,
}
impl BoundedTree {
    fn step(&self) {
        let n = self.operations.get() + 1;
        self.operations.set(n);
        assert!(n <= MAX_TREE_OPERATIONS, "WEB_HTML_COMPLEXITY");
    }
    fn node(&self, name: Option<QualName>, attrs: Vec<Attribute>, text: String) -> Handle {
        self.step();
        let n = self.nodes.get() + 1;
        self.nodes.set(n);
        assert!(n <= MAX_NODES, "WEB_HTML_COMPLEXITY");
        Node::new(name, attrs, text)
    }
    fn attach(&self, parent: &Handle, child: &Handle, index: Option<usize>) {
        self.step();
        let mut ancestor = Some(parent.clone());
        let mut depth = 0;
        while let Some(node) = ancestor {
            assert!(!Rc::ptr_eq(&node, child), "WEB_HTML_COMPLEXITY");
            depth += 1;
            assert!(depth <= MAX_DEPTH, "WEB_HTML_COMPLEXITY");
            ancestor = node.parent.borrow().upgrade();
        }
        // Reparenting a subtree can deepen existing descendants; bound them before mutation.
        let mut pending = vec![(child.clone(), depth)];
        while let Some((node, level)) = pending.pop() {
            self.step();
            assert!(level <= MAX_DEPTH, "WEB_HTML_COMPLEXITY");
            for descendant in node.children.borrow().iter() {
                pending.push((descendant.clone(), level + 1));
            }
        }
        self.remove_from_parent(child);
        *child.parent.borrow_mut() = Rc::downgrade(parent);
        let mut children = parent.children.borrow_mut();
        if let Some(index) = index {
            let index = index.min(children.len());
            children.insert(index, child.clone());
        } else {
            children.push(child.clone());
        }
    }
    fn materialize(&self, child: NodeOrText<Handle>) -> Handle {
        match child {
            NodeOrText::AppendNode(node) => node,
            NodeOrText::AppendText(text) => self.node(None, vec![], text.to_string()),
        }
    }
}
impl TreeSink for BoundedTree {
    type Handle = Handle;
    type Output = Self;
    type ElemName<'a> = ExpandedName<'a>;
    fn finish(self) -> Self {
        self
    }
    fn parse_error(&self, msg: Cow<'static, str>) {
        self.step();
        // A missing optional document doctype is harmless. All other recovery errors refuse
        // the complete snapshot so broken hidden/action markup cannot yield partial knowledge.
        if !msg.ends_with("in insertion mode Initial") {
            self.malformed.set(true);
        }
    }
    fn get_document(&self) -> Handle {
        self.document.clone()
    }
    fn elem_name<'a>(&'a self, target: &'a Handle) -> ExpandedName<'a> {
        target.name.as_ref().expect("HTML element").expanded()
    }
    fn create_element(&self, name: QualName, attrs: Vec<Attribute>, flags: ElementFlags) -> Handle {
        let node = self.node(Some(name), attrs, String::new());
        if flags.template {
            let fragment = self.node(None, vec![], String::new());
            // Template fragments are owned by the element; include that weak ancestry in
            // allocation/attachment depth so nested inert templates cannot evade the cap.
            *fragment.parent.borrow_mut() = Rc::downgrade(&node);
            *node.template.borrow_mut() = Some(fragment);
        }
        node
    }
    fn create_comment(&self, _: StrTendril) -> Handle {
        self.node(None, vec![], String::new())
    }
    fn create_pi(&self, _: StrTendril, _: StrTendril) -> Handle {
        self.node(None, vec![], String::new())
    }
    fn append(&self, parent: &Handle, child: NodeOrText<Handle>) {
        self.step();
        if let NodeOrText::AppendText(text) = &child {
            let previous = parent.children.borrow().last().cloned();
            if let Some(previous) =
                previous.filter(|n| n.name.is_none() && !n.text.borrow().is_empty())
            {
                previous.text.borrow_mut().push_str(text);
                return;
            }
        }
        let child = self.materialize(child);
        self.attach(parent, &child, None);
    }
    fn append_based_on_parent_node(
        &self,
        element: &Handle,
        prev: &Handle,
        child: NodeOrText<Handle>,
    ) {
        if element.parent.borrow().upgrade().is_some() {
            self.append_before_sibling(element, child)
        } else {
            self.append(prev, child)
        }
    }
    fn append_doctype_to_document(&self, _: StrTendril, _: StrTendril, _: StrTendril) {
        self.step();
    }
    fn get_template_contents(&self, target: &Handle) -> Handle {
        target
            .template
            .borrow()
            .as_ref()
            .expect("HTML template")
            .clone()
    }
    fn same_node(&self, x: &Handle, y: &Handle) -> bool {
        Rc::ptr_eq(x, y)
    }
    fn set_quirks_mode(&self, _: QuirksMode) {}
    fn append_before_sibling(&self, sibling: &Handle, child: NodeOrText<Handle>) {
        let parent = sibling
            .parent
            .borrow()
            .upgrade()
            .expect("HTML sibling parent");
        self.step();
        if let NodeOrText::AppendText(text) = &child {
            let children = parent.children.borrow();
            let index = children
                .iter()
                .position(|n| Rc::ptr_eq(n, sibling))
                .expect("HTML sibling");
            let previous = index.checked_sub(1).and_then(|i| children.get(i)).cloned();
            drop(children);
            if let Some(previous) =
                previous.filter(|n| n.name.is_none() && !n.text.borrow().is_empty())
            {
                previous.text.borrow_mut().push_str(text);
                return;
            }
        }
        let node = self.materialize(child);
        self.remove_from_parent(&node);
        let index = parent
            .children
            .borrow()
            .iter()
            .position(|n| Rc::ptr_eq(n, sibling))
            .expect("HTML sibling");
        self.attach(&parent, &node, Some(index));
    }
    fn add_attrs_if_missing(&self, target: &Handle, attrs: Vec<Attribute>) {
        self.step();
        let mut current = target.attrs.borrow_mut();
        for a in attrs {
            if !current.iter().any(|b| b.name == a.name) {
                current.push(a);
            }
        }
    }
    fn remove_from_parent(&self, target: &Handle) {
        self.step();
        if let Some(parent) = target.parent.borrow().upgrade() {
            parent
                .children
                .borrow_mut()
                .retain(|n| !Rc::ptr_eq(n, target));
        }
        *target.parent.borrow_mut() = Weak::new();
    }
    fn reparent_children(&self, node: &Handle, parent: &Handle) {
        let children = std::mem::take(&mut *node.children.borrow_mut());
        for child in children {
            *child.parent.borrow_mut() = Weak::new();
            self.attach(parent, &child, None);
        }
    }
}

pub(crate) struct Snapshot {
    pub raw_html_hash: String,
    pub extracted_hash: String,
    pub text: String,
    pub candidates: Vec<Candidate>,
    pub candidate_hashes: Vec<String>,
}
fn hash(text: &[u8]) -> String {
    hex::encode(Sha256::digest(text))
}
fn newline(text: &mut String) {
    if !text.ends_with('\n') && !text.is_empty() {
        text.push('\n');
    }
}
fn project(
    node: &Handle,
    depth: usize,
    body: &mut String,
    title: &mut String,
    in_title: bool,
) -> Result<(), &'static str> {
    if depth > MAX_DEPTH {
        return Err("HTML_TOO_COMPLEX");
    }
    let local = node.name.as_ref().map(|n| n.local.as_ref()).unwrap_or("");
    if node
        .name
        .as_ref()
        .is_some_and(|n| n.ns.as_ref() != "http://www.w3.org/1999/xhtml")
        || [
            "script", "style", "noscript", "template", "form", "iframe", "object", "embed", "svg",
            "math", "canvas", "button", "input", "select", "textarea", "option",
        ]
        .contains(&local)
        || node.attrs.borrow().iter().any(|a| {
            a.name.local.as_ref() == "hidden"
                || (a.name.local.as_ref() == "aria-hidden"
                    && a.value.trim().eq_ignore_ascii_case("true"))
                || (a.name.local.as_ref() == "style"
                    && a.value.to_ascii_lowercase().split(';').any(|p| {
                        p.split_once(':').is_some_and(|(k, v)| {
                            matches!(k.trim(), "display" | "visibility")
                                && matches!(v.trim(), "none" | "hidden" | "collapse")
                        })
                    }))
        })
    {
        return Ok(());
    }
    if local == "head" {
        for child in node.children.borrow().iter() {
            if child
                .name
                .as_ref()
                .is_some_and(|n| n.local.as_ref() == "title")
            {
                project(child, depth + 1, body, title, true)?;
            }
        }
        return Ok(());
    }
    let is_title = in_title || local == "title";
    let block = [
        "p",
        "div",
        "article",
        "main",
        "section",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "li",
        "ul",
        "ol",
        "blockquote",
        "pre",
        "table",
        "tr",
        "dl",
        "dt",
        "dd",
        "header",
        "footer",
        "hr",
        "br",
    ]
    .contains(&local);
    if block && !is_title {
        newline(body);
    }
    let text = node.text.borrow();
    let dest = if is_title { &mut *title } else { &mut *body };
    for c in text.chars() {
        if c.is_whitespace() {
            if !dest.is_empty() && !dest.ends_with([' ', '\n']) {
                dest.push(' ');
            }
        } else {
            dest.push(c);
        }
    }
    // O(1) provisional byte guard; exact code-point limit is checked once after normalization.
    if body.len() > MAX_TEXT_CHARS * 4 + MAX_NODES || title.len() > MAX_HTML_CHARS * 4 {
        return Err("TEXT_TOO_LARGE");
    }
    for child in node.children.borrow().iter() {
        project(child, depth + 1, body, title, is_title)?;
    }
    if block && !is_title {
        newline(body);
    } else if matches!(local, "td" | "th") && !is_title {
        body.push(' ');
    }
    Ok(())
}
pub(crate) fn extract(html: &[u8]) -> Result<Snapshot, &'static str> {
    if html.len() > MAX_HTML_BYTES {
        return Err("RESPONSE_TOO_LARGE");
    }
    let text = std::str::from_utf8(html).map_err(|_| "INVALID_UTF8")?;
    let text = text.strip_prefix('\u{feff}').unwrap_or(text);
    if text.contains('\0') {
        return Err("MALFORMED_HTML");
    }
    if text.chars().count() > MAX_HTML_CHARS {
        return Err("HTML_TOO_COMPLEX");
    }
    let mut options = html5ever::ParseOpts::default();
    options.tree_builder.exact_errors = true;
    let tree = catch_unwind(AssertUnwindSafe(|| {
        parse_document(
            BoundedTree {
                document: Node::new(None, vec![], String::new()),
                nodes: Cell::new(1),
                operations: Cell::new(0),
                malformed: Cell::new(false),
            },
            options,
        )
        .one(text)
    }))
    .map_err(|_| "HTML_TOO_COMPLEX")?;
    if tree.malformed.get() {
        return Err("MALFORMED_HTML");
    }
    let mut body = String::new();
    let mut title = String::new();
    project(&tree.document, 0, &mut body, &mut title, false)?;
    let body = body
        .lines()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .collect::<Vec<_>>()
        .join("\n");
    if body.is_empty() {
        return Err("NO_VISIBLE_TEXT");
    }
    if body.chars().count() > MAX_TEXT_CHARS {
        return Err("TEXT_TOO_LARGE");
    }
    let title = title.trim();
    let title = if title.is_empty() {
        body.lines().next().expect("nonempty body")
    } else {
        title
    };
    crate::require_safe_memory_body(&format!("{title}\n{body}")).map_err(|_| "HIGH_SENSITIVITY")?;
    let title = title.chars().take(120).collect::<String>();
    let chars = body.chars().collect::<Vec<_>>();
    let mut candidates = vec![];
    for (index, chunk) in chars.chunks(12_000).enumerate() {
        let part_title = if index == 0 {
            title.clone()
        } else {
            format!(
                "{} · {}",
                title.chars().take(110).collect::<String>(),
                index + 1
            )
        };
        let part = chunk.iter().collect::<String>();
        if !part.trim().is_empty() {
            candidates.push(
                validate_candidate(Candidate {
                    title: part_title,
                    body: part,
                    tags: vec![],
                })
                .map_err(|_| "KNOWLEDGE_REJECTED")?,
            );
        }
    }
    let candidate_hashes = candidates
        .iter()
        .map(|c| hash(format!("{}\n{}", c.title, c.body).as_bytes()))
        .collect();
    Ok(Snapshot {
        raw_html_hash: hash(html),
        extracted_hash: hash(body.as_bytes()),
        text: body,
        candidates,
        candidate_hashes,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn web_snapshot_url_secrets_literals_and_nonpublic_names_are_not_persistable() {
        for raw in [
            "http://public.example.org/",
            "https:public.example.org/",
            "https://127.1/",
            "https://2130706433/",
            "https://0x7f000001/",
            "https://[::1]/",
            "https://u:p@public.example.org/",
            "https://@public.example.org/",
            "https://public.example.org:444/",
            "https://public.example.org/#fragment",
            "https://local/",
            "https://foo.local/",
            "https://foo.test/",
            "https://public.example.org./",
            "https://public.example.org/?%74oken=value",
            "https://public.example.org/?API-KEY=value",
            "https://public.example.org/?code=value",
        ] {
            assert!(validate_url(raw).is_err(), "{raw}");
        }
        let safe = validate_url("https://Public.example.org/a%20b?q=ordinary").unwrap();
        assert_eq!(safe.host, "public.example.org");
        assert_eq!(safe.display_url, "https://public.example.org/a%20b");
        assert!(safe.fetch_url().as_str().contains("q=ordinary"));
    }
    #[test]
    fn web_snapshot_redirects_validate_every_target_without_implicit_follow() {
        let u = validate_url("https://public.example.org/one?a=1").unwrap();
        assert_eq!(
            redirect(&u, "../two?q=2", 2).unwrap().display_url,
            "https://public.example.org/two"
        );
        for target in [
            "http://public.example.org/",
            "//127.0.0.1/",
            "// @public.example.org/",
            "https://@public.example.org/",
            "https:public.example.org/",
            "https://public.example.org/#f",
            "/two?password=bad",
        ] {
            assert!(redirect(&u, target, 0).is_err());
        }
        assert_eq!(redirect(&u, "/two", 3).err(), Some("TOO_MANY_REDIRECTS"));
    }
    #[test]
    fn web_snapshot_dns_all_answers_and_special_ranges_fail_closed() {
        for ip in [
            "0.1.2.3",
            "10.1.2.3",
            "127.0.0.1",
            "100.64.0.1",
            "169.254.1.1",
            "172.31.2.1",
            "192.168.0.1",
            "192.0.0.9",
            "192.0.2.1",
            "192.88.99.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "224.0.0.1",
            "255.255.255.255",
            "::1",
            "::ffff:8.8.8.8",
            "64:ff9b::808:808",
            "fc00::1",
            "fe80::1",
            "2001:db8::1",
            "2001::1",
            "2002:808:808::1",
            "3fff::1",
            "ff02::1",
        ] {
            assert!(!public_address(ip.parse().unwrap()), "{ip}");
        }
        let public = "8.8.8.8".parse().unwrap();
        assert!(public_address("2606:4700:4700::1111".parse().unwrap()));
        assert!(validate_addresses(&[public]).is_ok());
        assert!(validate_addresses(&[]).is_err());
        assert!(validate_addresses(&[public, "127.0.0.1".parse().unwrap()]).is_err());
    }
    fn headers() -> Vec<(String, String)> {
        vec![("Content-Type".into(), "text/html; charset=UTF-8".into())]
    }
    #[test]
    fn web_snapshot_http_header_encoding_and_stream_limits_are_enforced() {
        assert_eq!(
            response_html(200, &headers(), b"<p>ok</p>".as_slice()).unwrap(),
            b"<p>ok</p>"
        );
        assert_eq!(
            response_html(302, &headers(), b"".as_slice()).err(),
            Some("HTTP_STATUS")
        );
        let mut h = headers();
        h.push(("content-type".into(), "text/html".into()));
        assert_eq!(
            response_html(200, &h, b"".as_slice()).err(),
            Some("MALFORMED_RESPONSE")
        );
        h = headers();
        h[0].1 = "text/htmlx".into();
        assert_eq!(
            response_html(200, &h, b"".as_slice()).err(),
            Some("CONTENT_TYPE")
        );
        h[0].1 = "text/html; charset=latin1".into();
        assert_eq!(
            response_html(200, &h, b"".as_slice()).err(),
            Some("INVALID_UTF8")
        );
        h = headers();
        h.push(("X-Large".into(), "x".repeat(MAX_HEADERS)));
        assert_eq!(
            response_html(200, &h, b"".as_slice()).err(),
            Some("RESPONSE_TOO_LARGE")
        );
        assert_eq!(
            response_html(200, &headers(), vec![b'x'; MAX_WIRE_BYTES + 1].as_slice()).err(),
            Some("RESPONSE_TOO_LARGE")
        );
        h = headers();
        h.push(("content-length".into(), "2".into()));
        assert_eq!(
            response_html(200, &h, b"x".as_slice()).err(),
            Some("MALFORMED_RESPONSE")
        );
    }
    #[test]
    fn web_snapshot_gzip_decode_is_bounded_and_checks_all_members() {
        use std::io::Write;
        fn gzip(b: &[u8]) -> Vec<u8> {
            let mut z = flate2::write::GzEncoder::new(Vec::new(), flate2::Compression::default());
            z.write_all(b).unwrap();
            z.finish().unwrap()
        }
        let mut h = headers();
        h.push(("Content-Encoding".into(), "gzip".into()));
        let raw = gzip(b"<p>Chinese: \xe4\xbd\xa0</p>");
        assert_eq!(
            response_html(200, &h, raw.as_slice()).unwrap(),
            b"<p>Chinese: \xe4\xbd\xa0</p>"
        );
        let mut members = gzip(b"<p>one</p>");
        members.extend(gzip(b"<p>two</p>"));
        assert_eq!(
            response_html(200, &h, members.as_slice()).unwrap(),
            b"<p>one</p><p>two</p>"
        );
        let raw = gzip(&vec![b'x'; MAX_HTML_BYTES + 1]);
        assert_eq!(
            response_html(200, &h, raw.as_slice()).err(),
            Some("RESPONSE_TOO_LARGE")
        );
        assert_eq!(
            response_html(200, &h, b"not gzip".as_slice()).err(),
            Some("CONTENT_ENCODING")
        );
        h[1].1 = "br".into();
        assert_eq!(
            response_html(200, &h, b"x".as_slice()).err(),
            Some("CONTENT_ENCODING")
        );
    }
    #[test]
    fn web_snapshot_html_tree_projects_visible_entities_optional_tags_and_inert_links() {
        let raw=br#"<!doctype html><title>Title &amp; facts</title><main><h1>Heading</h1><p>Hello <b>world</b> &lt;x&gt;<p>Second<br>line<ul><li>one<li>two</ul><a href="https://other.example.org/">Link text</a></main>"#;
        let s = extract(raw).unwrap();
        assert_eq!(
            s.text,
            "Heading\nHello world <x>\nSecond\nline\none\ntwo\nLink text"
        );
        assert_eq!(s.candidates[0].title, "Title & facts");
        assert_eq!(
            s.candidate_hashes[0],
            hash(format!("{}\n{}", s.candidates[0].title, s.text).as_bytes())
        );
        assert_eq!(s.raw_html_hash, hash(raw));
        assert_eq!(s.extracted_hash, hash(s.text.as_bytes()));
    }
    #[test]
    fn web_snapshot_hidden_nested_action_and_foreign_subtrees_are_inert() {
        let s=extract(br#"<p>Visible</p><div hidden><p>Never</p><span>hidden descendant</span></div><p aria-hidden='TRUE'>Never</p><div style='display: none'><p>Never</p></div><script>location='x';</script><style>x</style><noscript>x</noscript><template><p>x</p></template><form><p>x</p></form><iframe>x</iframe><svg><text>x</text></svg><button>x</button><p>End</p>"#).unwrap();
        assert_eq!(s.text, "Visible\nEnd");
        assert_eq!(
            extract(b"<script>evil</script>").err(),
            Some("NO_VISIBLE_TEXT")
        );
    }
    #[test]
    fn web_snapshot_invalid_utf8_nul_high_sensitivity_and_size_refuse_whole_bundle() {
        assert_eq!(extract(&[255]).err(), Some("INVALID_UTF8"));
        assert_eq!(extract(b"<p>hello\0</p>").err(), Some("MALFORMED_HTML"));
        assert_eq!(
            extract(b"<p>safe</p><p>password: synthetic-only</p>").err(),
            Some("HIGH_SENSITIVITY")
        );
        assert_eq!(
            extract(vec![b' '; MAX_HTML_BYTES + 1].as_slice()).err(),
            Some("RESPONSE_TOO_LARGE")
        );
        assert_eq!(
            extract(format!("<p>{}</p>", "x".repeat(MAX_TEXT_CHARS + 1)).as_bytes()).err(),
            Some("TEXT_TOO_LARGE")
        );
        assert_eq!(
            extract(format!("<p>{}</p>", "x".repeat(MAX_HTML_CHARS)).as_bytes()).err(),
            Some("HTML_TOO_COMPLEX")
        );
    }
    #[test]
    fn web_snapshot_depth_and_node_budgets_are_real_parser_guards() {
        assert_eq!(
            extract(format!("{}x{}", "<div>".repeat(40), "</div>".repeat(40)).as_bytes()).err(),
            Some("HTML_TOO_COMPLEX")
        );
        assert_eq!(
            extract("<br>".repeat(MAX_NODES).as_bytes()).err(),
            Some("HTML_TOO_COMPLEX")
        );
        assert!(
            extract(format!("{}ok{}", "<div>".repeat(20), "</div>".repeat(20)).as_bytes()).is_ok()
        );
    }
    #[test]
    fn web_snapshot_broken_action_markup_refuses_even_after_valid_visible_content() {
        for html in [
            "<p>before</p><script>unterminated",
            "<p>before</p><div hidden><p>broken",
            "<p>before</p><form><form>x</form></form>",
        ] {
            assert_eq!(extract(html.as_bytes()).err(), Some("MALFORMED_HTML"));
        }
    }
    #[test]
    fn web_snapshot_long_unicode_text_is_chunked_only_into_review_candidates() {
        let text = "你".repeat(25_000);
        let s = extract(format!("<title>资料</title><p>{text}</p>").as_bytes()).unwrap();
        assert_eq!(s.text, text);
        assert_eq!(s.candidates.len(), 3);
        assert_eq!(
            s.candidates
                .iter()
                .map(|c| c.body.chars().count())
                .collect::<Vec<_>>(),
            [12_000, 12_000, 1_000]
        );
        assert_eq!(
            s.candidates
                .iter()
                .map(|c| c.body.as_str())
                .collect::<String>(),
            text
        );
        assert_eq!(s.candidate_hashes.len(), 3);
    }
    #[test]
    fn web_snapshot_tree_hardening_template_fragments_count_toward_depth() {
        let html = format!(
            "<p>visible</p>{}hidden{}",
            "<template>".repeat(35),
            "</template>".repeat(35)
        );
        assert_eq!(extract(html.as_bytes()).err(), Some("HTML_TOO_COMPLEX"));
    }
    #[test]
    fn web_snapshot_tree_hardening_adjacent_entities_are_one_text_node() {
        let html = format!("<p>{}</p>", "&amp;".repeat(14_000));
        let snapshot = extract(html.as_bytes()).unwrap();
        assert_eq!(snapshot.text, "&".repeat(14_000));
    }
    struct FakeTransport {
        dns: std::collections::VecDeque<Vec<IpAddr>>,
        responses: std::collections::VecDeque<Response>,
        calls: Vec<String>,
        wait: Duration,
        cancel_after_response: Option<std::sync::Arc<std::sync::atomic::AtomicBool>>,
    }
    impl Transport for FakeTransport {
        fn resolve<'a>(&'a mut self, host: &'a str) -> Operation<'a, Vec<IpAddr>> {
            self.calls.push(format!("DNS:{host}"));
            Box::pin(async move {
                tokio::time::sleep(self.wait).await;
                self.dns.pop_front().ok_or("DNS_NOT_PUBLIC")
            })
        }
        fn request<'a>(
            &'a mut self,
            url: &'a SafeUrl,
            addresses: &'a [IpAddr],
        ) -> Operation<'a, Response> {
            assert!(validate_addresses(addresses).is_ok());
            self.calls.push(format!("GET:{}", url.display_url));
            Box::pin(async move {
                tokio::time::sleep(self.wait).await;
                let response = self.responses.pop_front().ok_or("NETWORK");
                if let Some(cancel) = &self.cancel_after_response {
                    cancel.store(true, std::sync::atomic::Ordering::SeqCst);
                }
                response
            })
        }
    }
    fn transport(responses: Vec<Response>) -> FakeTransport {
        FakeTransport {
            dns: (0..responses.len())
                .map(|_| vec!["8.8.8.8".parse().unwrap()])
                .collect(),
            responses: responses.into(),
            calls: vec![],
            wait: Duration::ZERO,
            cancel_after_response: None,
        }
    }
    fn to(location: &str) -> Response {
        Response {
            status: 302,
            headers: vec![("location".into(), location.into())],
            body: vec![],
        }
    }
    fn final_page() -> Response {
        Response {
            status: 200,
            headers: headers(),
            body: b"<title>snapshot</title><p>inert body</p>".to_vec(),
        }
    }
    #[tokio::test]
    async fn web_snapshot_transport_port_pins_validated_answers_and_rechecks_redirect_dns() {
        let mut t = transport(vec![
            to("https://next.example.org/second?q=ordinary"),
            final_page(),
        ]);
        let result = fetch(&mut t, "https://public.example.org/one", || false)
            .await
            .unwrap();
        assert_eq!(result.display_url, "https://next.example.org/second");
        assert_eq!(result.snapshot.text, "inert body");
        assert_eq!(
            t.calls,
            [
                "DNS:public.example.org",
                "GET:https://public.example.org/one",
                "DNS:next.example.org",
                "GET:https://next.example.org/second"
            ]
        );
        let mut t = transport(vec![to("https://next.example.org/second"), final_page()]);
        t.dns[1].push("127.0.0.1".parse().unwrap());
        assert_eq!(
            fetch(&mut t, "https://public.example.org/", || false)
                .await
                .err(),
            Some("DNS_NOT_PUBLIC")
        );
        assert_eq!(t.calls.len(), 3);
        assert_eq!(t.responses.len(), 1);
    }
    #[tokio::test]
    async fn web_snapshot_transport_redirect_loops_duplicate_locations_and_query_secrets_refuse() {
        let mut t = transport(vec![
            to("/one"),
            to("/two"),
            to("/three"),
            to("/four"),
            final_page(),
        ]);
        assert_eq!(
            fetch(&mut t, "https://public.example.org/", || false)
                .await
                .err(),
            Some("TOO_MANY_REDIRECTS")
        );
        assert_eq!(t.calls.len(), 8);
        assert_eq!(t.responses.len(), 1);
        let mut t = transport(vec![to("/next?%74oken=x"), final_page()]);
        assert_eq!(
            fetch(&mut t, "https://public.example.org/", || false)
                .await
                .err(),
            Some("REDIRECT_REJECTED")
        );
        assert_eq!(t.calls.len(), 2);
        let mut bad = to("/one");
        bad.headers.push(("Location".into(), "/two".into()));
        let mut t = transport(vec![bad]);
        assert_eq!(
            fetch(&mut t, "https://public.example.org/", || false)
                .await
                .err(),
            Some("REDIRECT_REJECTED")
        );
    }
    #[tokio::test]
    async fn web_snapshot_transport_total_budget_and_cancellation_do_not_start_later_requests() {
        let mut t = transport(vec![final_page()]);
        t.wait = Duration::from_millis(50);
        assert_eq!(
            fetch_with_budget(
                &mut t,
                "https://public.example.org/",
                || false,
                Duration::from_millis(5)
            )
            .await
            .err(),
            Some("TIMEOUT")
        );
        assert_eq!(t.calls, ["DNS:public.example.org"]);
        let mut t = transport(vec![final_page()]);
        assert_eq!(
            fetch(&mut t, "https://public.example.org/", || true)
                .await
                .err(),
            Some("CANCELLED")
        );
        assert!(t.calls.is_empty());
        let start = std::time::Instant::now();
        let mut t = transport(vec![final_page()]);
        t.wait = Duration::from_millis(100);
        assert_eq!(
            fetch(&mut t, "https://public.example.org/", || start.elapsed()
                > Duration::from_millis(5))
            .await
            .err(),
            Some("CANCELLED")
        );
        assert_eq!(t.calls, ["DNS:public.example.org"]);
    }
    #[tokio::test]
    async fn web_snapshot_tree_hardening_cancelled_final_response_has_no_candidates() {
        let flag = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
        let mut t = transport(vec![final_page()]);
        t.cancel_after_response = Some(flag.clone());
        assert_eq!(
            fetch(&mut t, "https://public.example.org/", || flag
                .load(std::sync::atomic::Ordering::SeqCst))
            .await
            .err(),
            Some("CANCELLED")
        );
    }
}
