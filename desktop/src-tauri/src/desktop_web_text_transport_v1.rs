//! P4O HTTPS only. Direct validated-IP TCP, hostname-verifying TLS, bounded raw HTTP/1.
use crate::desktop_web_text_snapshot_v1::{
    validate_addresses, Operation, Response, SafeUrl, Transport, MAX_WIRE_BYTES,
};
use http_body_util::{BodyExt, Empty};
use hyper::{body::Bytes, Request};
use hyper_util::rt::TokioIo;
use std::{
    io,
    net::{IpAddr, SocketAddr},
    pin::Pin,
    sync::{Arc, Mutex},
    task::{Context, Poll},
    time::Duration,
};
use tokio::{
    io::{AsyncRead, AsyncWrite, ReadBuf},
    net::TcpStream,
    task::JoinHandle,
};
use tokio_rustls::{
    rustls::{self, pki_types::ServerName},
    TlsConnector,
};

const MAX_HEAD: usize = 16 * 1024;
pub(crate) struct HttpsTransport {
    tls: Arc<rustls::ClientConfig>,
    #[cfg(all(windows, debug_assertions))]
    dns_input_pause: Option<std::path::PathBuf>,
}
impl HttpsTransport {
    pub(crate) fn new() -> Result<Self, &'static str> {
        let mut roots = rustls::RootCertStore::empty();
        roots.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
        Ok(Self {
            tls: client_config(roots)?,
            #[cfg(all(windows, debug_assertions))]
            dns_input_pause: None,
        })
    }
    pub(crate) fn new_for_root(_root: &std::path::Path) -> Result<Self, &'static str> {
        let transport = Self::new()?;
        #[cfg(all(windows, debug_assertions))]
        {
            let mut transport = transport;
            transport.dns_input_pause =
                crate::desktop_web_dns_worker_v1::acceptance_pause_root(_root)?;
            return Ok(transport);
        }
        #[cfg(not(all(windows, debug_assertions)))]
        Ok(transport)
    }
    // Private low-level connection helper. Only Transport::request accepts production addresses.
    async fn request_socket(
        &self,
        url: &SafeUrl,
        addresses: &[SocketAddr],
    ) -> Result<Response, &'static str> {
        let mut connected = None;
        for address in addresses {
            if let Ok(Ok(stream)) =
                tokio::time::timeout(Duration::from_secs(4), TcpStream::connect(*address)).await
            {
                connected = Some(stream);
                break;
            }
        }
        let stream = connected.ok_or("NETWORK")?;
        let name = ServerName::try_from(url.host.clone()).map_err(|_| "INVALID_URL")?;
        let tls = TlsConnector::from(self.tls.clone())
            .connect(name, stream)
            .await
            .map_err(|e| {
                if e.get_ref().is_some_and(|e| {
                    e.downcast_ref::<rustls::Error>()
                        .is_some_and(|e| matches!(e, rustls::Error::InvalidCertificate(_)))
                }) {
                    "CERTIFICATE"
                } else {
                    "NETWORK"
                }
            })?;
        let state = Arc::new(Mutex::new(RawHead::default()));
        let io = TokioIo::new(GuardedIo {
            inner: tls,
            state: state.clone(),
        });
        let mut builder = hyper::client::conn::http1::Builder::new();
        builder
            .max_buf_size(MAX_HEAD)
            .max_headers(1024)
            .http09_responses(false)
            .allow_spaces_after_header_name_in_responses(false)
            .allow_obsolete_multiline_headers_in_responses(false)
            .ignore_invalid_headers_in_responses(false);
        let (mut sender, connection) = builder
            .handshake::<_, Empty<Bytes>>(io)
            .await
            .map_err(|_| "NETWORK")?;
        let mut driver = Driver(Some(tokio::spawn(async move {
            let _ = connection.await;
        })));
        let result = async {
            let mut target = url.fetch_url().path().to_owned();
            if let Some(query) = url.fetch_url().query() {
                target.push('?');
                target.push_str(query);
            }
            let request = Request::builder()
                .method("GET")
                .uri(target)
                .header("Host", &url.host)
                .header("Accept", "text/html")
                .header("Accept-Encoding", "gzip, identity")
                .header("User-Agent", "NanfengAI-WebTextSnapshot/1")
                .header("Connection", "close")
                .body(Empty::<Bytes>::new())
                .map_err(|_| "INVALID_URL")?;
            let response = sender
                .send_request(request)
                .await
                .map_err(|e| network_error(&state, e))?;
            let status = response.status().as_u16();
            let headers = {
                let state = state.lock().map_err(|_| "NETWORK")?;
                if let Some(error) = state.failure {
                    return Err(error);
                }
                state.headers.clone()
            };
            let expected_length = if let Some((_, value)) = headers
                .iter()
                .find(|(k, _)| k.eq_ignore_ascii_case("content-length"))
            {
                let length = value.parse::<usize>().map_err(|_| "MALFORMED_RESPONSE")?;
                if length > MAX_WIRE_BYTES {
                    return Err("RESPONSE_TOO_LARGE");
                }
                Some(length)
            } else {
                None
            };
            let mut incoming = response.into_body();
            let mut body = Vec::new();
            while let Some(frame) = incoming.frame().await {
                let frame = frame.map_err(|e| {
                    if expected_length.is_some_and(|n| body.len() < n) {
                        "MALFORMED_RESPONSE"
                    } else {
                        network_error(&state, e)
                    }
                })?;
                if let Some(data) = frame.data_ref() {
                    if data.len() > MAX_WIRE_BYTES - body.len() {
                        return Err("RESPONSE_TOO_LARGE");
                    }
                    body.extend_from_slice(data);
                } else if frame.trailers_ref().is_some() {
                    return Err("MALFORMED_RESPONSE");
                }
            }
            if expected_length.is_some_and(|n| body.len() != n) {
                return Err("MALFORMED_RESPONSE");
            }
            Ok(Response {
                status,
                headers,
                body,
            })
        }
        .await;
        drop(sender);
        driver.close().await;
        result
    }
}
fn client_config(roots: rustls::RootCertStore) -> Result<Arc<rustls::ClientConfig>, &'static str> {
    let mut config = rustls::ClientConfig::builder_with_provider(Arc::new(
        rustls::crypto::ring::default_provider(),
    ))
    .with_safe_default_protocol_versions()
    .map_err(|_| "CERTIFICATE")?
    .with_root_certificates(roots)
    .with_no_client_auth();
    config.alpn_protocols = vec![b"http/1.1".to_vec()];
    Ok(Arc::new(config))
}
impl Transport for HttpsTransport {
    fn resolve<'a>(&'a mut self, host: &'a str) -> Operation<'a, Vec<IpAddr>> {
        #[cfg(all(windows, debug_assertions))]
        if let Some(root) = self.dns_input_pause.clone() {
            return Box::pin(crate::desktop_web_dns_worker_v1::resolve_paused(host, root));
        }
        Box::pin(crate::desktop_web_dns_worker_v1::resolve(host))
    }
    fn request<'a>(
        &'a mut self,
        url: &'a SafeUrl,
        addresses: &'a [IpAddr],
    ) -> Operation<'a, Response> {
        Box::pin(async move {
            validate_addresses(addresses)?;
            let sockets = addresses
                .iter()
                .map(|ip| SocketAddr::new(*ip, 443))
                .collect::<Vec<_>>();
            tokio::time::timeout(Duration::from_secs(12), self.request_socket(url, &sockets))
                .await
                .map_err(|_| "TIMEOUT")?
        })
    }
}
struct Driver(Option<JoinHandle<()>>);
impl Driver {
    async fn close(&mut self) {
        if let Some(handle) = self.0.take() {
            handle.abort();
            let _ = handle.await;
        }
    }
}
impl Drop for Driver {
    fn drop(&mut self) {
        if let Some(handle) = self.0.take() {
            handle.abort();
        }
    }
}
fn network_error(state: &Arc<Mutex<RawHead>>, error: hyper::Error) -> &'static str {
    if let Ok(s) = state.lock() {
        if let Some(error) = s.failure {
            return error;
        }
        if !s.done && s.bytes >= MAX_HEAD && error.is_parse() {
            return "RESPONSE_TOO_LARGE";
        }
    }
    if error.is_parse() || error.is_incomplete_message() {
        "MALFORMED_RESPONSE"
    } else {
        "NETWORK"
    }
}

// This gate observes TLS plaintext before Hyper can normalize/drop duplicate header fields.
// The aggregate budget also includes informational responses and the exact raw status lines.
#[derive(Default)]
struct RawHead {
    bytes: usize,
    pending: Vec<u8>,
    done: bool,
    headers: Vec<(String, String)>,
    failure: Option<&'static str>,
}
impl RawHead {
    fn feed(&mut self, bytes: &[u8]) -> Result<(), &'static str> {
        for &byte in bytes {
            if self.done {
                break;
            }
            self.bytes += 1;
            if self.bytes > MAX_HEAD {
                return self.fail("RESPONSE_TOO_LARGE");
            }
            self.pending.push(byte);
            if self.pending.ends_with(b"\r\n\r\n") {
                let raw = std::str::from_utf8(&self.pending).map_err(|_| "MALFORMED_RESPONSE")?;
                let mut lines = raw.split("\r\n");
                let status = lines.next().ok_or("MALFORMED_RESPONSE")?;
                let mut status = status.splitn(3, ' ');
                if !matches!(status.next(), Some("HTTP/1.0" | "HTTP/1.1")) {
                    return self.fail("MALFORMED_RESPONSE");
                }
                let code = status
                    .next()
                    .and_then(|s| s.parse::<u16>().ok())
                    .ok_or("MALFORMED_RESPONSE")?;
                let mut headers = vec![];
                let mut critical = std::collections::BTreeSet::new();
                for line in lines.filter(|s| !s.is_empty()) {
                    let (name, value) = line.split_once(':').ok_or("MALFORMED_RESPONSE")?;
                    if name.is_empty()
                        || !name
                            .bytes()
                            .all(|b| b.is_ascii_alphanumeric() || b"!#$%&'*+-.^_`|~".contains(&b))
                    {
                        return self.fail("MALFORMED_RESPONSE");
                    }
                    let lower = name.to_ascii_lowercase();
                    if [
                        "content-type",
                        "content-encoding",
                        "content-length",
                        "transfer-encoding",
                        "location",
                    ]
                    .contains(&lower.as_str())
                        && !critical.insert(lower.clone())
                    {
                        return self.fail("MALFORMED_RESPONSE");
                    }
                    headers.push((lower, value.trim_matches([' ', '\t']).to_owned()));
                }
                if critical.contains("content-length") && critical.contains("transfer-encoding") {
                    return self.fail("MALFORMED_RESPONSE");
                }
                if let Some((_, value)) = headers.iter().find(|(k, _)| k == "transfer-encoding") {
                    if !value.eq_ignore_ascii_case("chunked") {
                        return self.fail("MALFORMED_RESPONSE");
                    }
                }
                if (100..200).contains(&code) {
                    if code == 101 {
                        return self.fail("HTTP_STATUS");
                    }
                    self.pending.clear();
                } else {
                    self.headers = headers;
                    self.done = true;
                    self.pending.clear();
                }
            }
        }
        Ok(())
    }
    fn fail<T>(&mut self, error: &'static str) -> Result<T, &'static str> {
        self.failure = Some(error);
        Err(error)
    }
}
struct GuardedIo<T> {
    inner: T,
    state: Arc<Mutex<RawHead>>,
}
impl<T: AsyncRead + Unpin> AsyncRead for GuardedIo<T> {
    fn poll_read(
        self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buffer: &mut ReadBuf<'_>,
    ) -> Poll<io::Result<()>> {
        let this = self.get_mut();
        let before = buffer.filled().len();
        match Pin::new(&mut this.inner).poll_read(cx, buffer) {
            Poll::Ready(Ok(())) => {
                let mut state = match this.state.lock() {
                    Ok(s) => s,
                    Err(_) => return Poll::Ready(Err(io::Error::other("WEB_NETWORK"))),
                };
                if let Err(error) = state.feed(&buffer.filled()[before..]) {
                    state.failure = Some(error);
                    return Poll::Ready(Err(io::Error::other("WEB_RESPONSE_REFUSED")));
                }
                Poll::Ready(Ok(()))
            }
            other => other,
        }
    }
}
impl<T: AsyncWrite + Unpin> AsyncWrite for GuardedIo<T> {
    fn poll_write(
        self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buffer: &[u8],
    ) -> Poll<io::Result<usize>> {
        Pin::new(&mut self.get_mut().inner).poll_write(cx, buffer)
    }
    fn poll_flush(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.get_mut().inner).poll_flush(cx)
    }
    fn poll_shutdown(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.get_mut().inner).poll_shutdown(cx)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::desktop_web_text_snapshot_v1::{extract, response_html, validate_url};
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    const CA: &[u8] = include_bytes!("../tests/fixtures/web-text-tls/ca.der");
    const CERT: &[u8] = include_bytes!("../tests/fixtures/web-text-tls/server.der");
    const KEY: &[u8] = include_bytes!("../tests/fixtures/web-text-tls/synthetic-server-key.der");
    fn trusted() -> HttpsTransport {
        let mut roots = rustls::RootCertStore::empty();
        roots
            .add(rustls::pki_types::CertificateDer::from(CA.to_vec()))
            .unwrap();
        HttpsTransport {
            tls: client_config(roots).unwrap(),
            #[cfg(all(windows, debug_assertions))]
            dns_input_pause: None,
        }
    }
    async fn fixture(raw: Vec<u8>, stall: bool) -> (SocketAddr, JoinHandle<Vec<u8>>) {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let config = rustls::ServerConfig::builder_with_provider(Arc::new(
            rustls::crypto::ring::default_provider(),
        ))
        .with_safe_default_protocol_versions()
        .unwrap()
        .with_no_client_auth()
        .with_single_cert(
            vec![rustls::pki_types::CertificateDer::from(CERT.to_vec())],
            rustls::pki_types::PrivatePkcs8KeyDer::from(KEY.to_vec()).into(),
        )
        .unwrap();
        let acceptor = tokio_rustls::TlsAcceptor::from(Arc::new(config));
        let handle = tokio::spawn(async move {
            let (tcp, _) = listener.accept().await.unwrap();
            let Ok(mut stream) = acceptor.accept(tcp).await else {
                return vec![];
            };
            let mut request = vec![];
            let mut byte = [0u8; 1];
            while request.len() < 8192 {
                if stream.read_exact(&mut byte).await.is_err() {
                    return request;
                }
                request.push(byte[0]);
                if request.ends_with(b"\r\n\r\n") {
                    break;
                }
            }
            if !stall {
                let _ = stream.write_all(&raw).await;
                let _ = stream.flush().await;
            }
            let mut left = [0u8; 1];
            let _ = tokio::time::timeout(Duration::from_secs(1), stream.read(&mut left)).await;
            request
        });
        (address, handle)
    }
    fn page() -> Vec<u8> {
        let b = b"<title>TLS fixture</title><p>actual inert body</p>";
        format!("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",b.len()).into_bytes().into_iter().chain(b.iter().copied()).collect()
    }
    #[tokio::test]
    async fn web_transport_actual_tls_and_literal_socket_keep_host_and_request_scope() {
        let (address, server) = fixture(page(), false).await;
        let url = validate_url("https://fixture.nanfeng.example.org/path?q=ordinary").unwrap();
        let response = trusted().request_socket(&url, &[address]).await.unwrap();
        assert_eq!(
            extract(
                &response_html(response.status, &response.headers, response.body.as_slice())
                    .unwrap()
            )
            .unwrap()
            .text,
            "actual inert body"
        );
        let request = String::from_utf8(server.await.unwrap())
            .unwrap()
            .to_lowercase();
        assert!(request.starts_with("get /path?q=ordinary http/1.1\r\n"));
        assert!(request.contains("host: fixture.nanfeng.example.org\r\n"));
        for name in [
            "cookie:",
            "authorization:",
            "referer:",
            "proxy-authorization:",
        ] {
            assert!(!request.contains(name));
        }
        // Production address API still refuses loopback even with the fixture's trusted CA.
        assert_eq!(
            trusted().request(&url, &[address.ip()]).await.err(),
            Some("DNS_NOT_PUBLIC")
        );
    }
    #[tokio::test]
    async fn web_transport_untrusted_ca_and_wrong_sni_refuse_before_get() {
        let (address, server) = fixture(page(), false).await;
        let url = validate_url("https://fixture.nanfeng.example.org/").unwrap();
        assert_eq!(
            HttpsTransport::new()
                .unwrap()
                .request_socket(&url, &[address])
                .await
                .err(),
            Some("CERTIFICATE")
        );
        assert!(server.await.unwrap().is_empty());
        let (address, server) = fixture(page(), false).await;
        let url = validate_url("https://wrong.example.org/").unwrap();
        assert_eq!(
            trusted().request_socket(&url, &[address]).await.err(),
            Some("CERTIFICATE")
        );
        assert!(server.await.unwrap().is_empty());
    }
    #[tokio::test]
    async fn web_transport_raw_headers_and_ambiguous_framing_are_refused_before_body() {
        for raw in [
            format!(
                "HTTP/1.1 200 OK\r\nX-Large: {}\r\n\r\n",
                "a".repeat(MAX_HEAD)
            )
            .into_bytes(),
            b"HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Length: 1\r\n\r\nx".to_vec(),
            b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: 1\r\n\r\nx".to_vec(),
        ] {
            let (address, server) = fixture(raw, false).await;
            let url = validate_url("https://fixture.nanfeng.example.org/").unwrap();
            assert!(matches!(
                trusted().request_socket(&url, &[address]).await.err(),
                Some("RESPONSE_TOO_LARGE" | "MALFORMED_RESPONSE")
            ));
            server.await.unwrap();
        }
    }
    #[tokio::test]
    async fn web_transport_chunked_body_limit_truncation_and_no_implicit_redirect() {
        let url = validate_url("https://fixture.nanfeng.example.org/").unwrap();
        let raw=format!("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nTransfer-Encoding: chunked\r\n\r\n{:x}\r\n{}\r\n0\r\n\r\n",MAX_WIRE_BYTES+1,"x".repeat(MAX_WIRE_BYTES+1)).into_bytes();
        let (address, server) = fixture(raw, false).await;
        assert_eq!(
            trusted().request_socket(&url, &[address]).await.err(),
            Some("RESPONSE_TOO_LARGE")
        );
        server.await.unwrap();
        let (address, server) = fixture(
            b"HTTP/1.1 200 OK\r\nContent-Length: 20\r\n\r\nshort".to_vec(),
            false,
        )
        .await;
        // Server closes after its bounded one-second wait; client must refuse partial bytes.
        assert_eq!(
            trusted().request_socket(&url, &[address]).await.err(),
            Some("MALFORMED_RESPONSE")
        );
        server.await.unwrap();
        let (address,server)=fixture(b"HTTP/1.1 302 Found\r\nLocation: https://127.0.0.1/private\r\nContent-Length: 0\r\n\r\n".to_vec(),false).await;
        let r = trusted().request_socket(&url, &[address]).await.unwrap();
        assert_eq!(r.status, 302);
        assert_eq!(
            r.headers.iter().find(|(k, _)| k == "location").unwrap().1,
            "https://127.0.0.1/private"
        );
        server.await.unwrap();
    }
    #[tokio::test]
    async fn web_transport_dropped_stalled_request_closes_owned_driver_and_socket() {
        let (address, server) = fixture(vec![], true).await;
        let url = validate_url("https://fixture.nanfeng.example.org/").unwrap();
        assert!(tokio::time::timeout(
            Duration::from_millis(100),
            trusted().request_socket(&url, &[address])
        )
        .await
        .is_err());
        let request = tokio::time::timeout(Duration::from_millis(500), server)
            .await
            .unwrap()
            .unwrap();
        assert!(!request.is_empty());
    }
    #[test]
    fn web_transport_raw_head_exact_budget_informational_and_duplicate_locations() {
        let prefix = "HTTP/1.1 200 OK\r\nX: ";
        let tail = "\r\n\r\n";
        let exact = format!(
            "{prefix}{}{tail}",
            "a".repeat(MAX_HEAD - prefix.len() - tail.len())
        );
        let mut gate = RawHead::default();
        for chunk in exact.as_bytes().chunks(7) {
            gate.feed(chunk).unwrap();
        }
        assert!(gate.done);
        assert_eq!(gate.bytes, MAX_HEAD);
        let mut gate = RawHead::default();
        assert_eq!(
            gate.feed(format!("{prefix}{}{tail}", "a".repeat(MAX_HEAD)).as_bytes())
                .err(),
            Some("RESPONSE_TOO_LARGE")
        );
        let mut gate = RawHead::default();
        gate.feed(b"HTTP/1.1 103 Early Hints\r\nLink: inert\r\n\r\nHTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n").unwrap();
        assert!(gate.done);
        assert_eq!(
            gate.headers,
            [("content-length".to_owned(), "0".to_owned())]
        );
        let mut gate = RawHead::default();
        assert_eq!(
            gate.feed(b"HTTP/1.1 302 Found\r\nLocation: /one\r\nLOCATION: /two\r\n\r\n")
                .err(),
            Some("MALFORMED_RESPONSE")
        );
    }
    #[tokio::test]
    async fn web_transport_actual_tls_gzip_flows_through_the_complete_adapter() {
        use std::io::Write;
        let html=b"<title>Actual TLS snapshot</title><main><p>Kept text</p><div hidden>Hidden</div><script>inert</script></main>";
        let mut gzip = flate2::write::GzEncoder::new(Vec::new(), flate2::Compression::default());
        gzip.write_all(html).unwrap();
        let gzip = gzip.finish().unwrap();
        let mut raw=format!("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Encoding: gzip\r\nContent-Length: {}\r\n\r\n",gzip.len()).into_bytes();
        raw.extend(gzip);
        let (address, server) = fixture(raw, false).await;
        // Loopback mapping and synthetic DNS are exclusive to this cfg(test) bridge. The same
        // private TLS/socket/header/body implementation runs; production DNS/public gates remain.
        struct FixtureBridge {
            inner: HttpsTransport,
            address: SocketAddr,
        }
        impl Transport for FixtureBridge {
            fn resolve<'a>(&'a mut self, _: &'a str) -> Operation<'a, Vec<IpAddr>> {
                Box::pin(async { Ok(vec!["8.8.8.8".parse().unwrap()]) })
            }
            fn request<'a>(
                &'a mut self,
                url: &'a SafeUrl,
                ips: &'a [IpAddr],
            ) -> Operation<'a, Response> {
                Box::pin(async move {
                    validate_addresses(ips)?;
                    self.inner.request_socket(url, &[self.address]).await
                })
            }
        }
        let mut bridge = FixtureBridge {
            inner: trusted(),
            address,
        };
        let fetched = crate::desktop_web_text_snapshot_v1::fetch(
            &mut bridge,
            "https://fixture.nanfeng.example.org/",
            || false,
        )
        .await
        .unwrap();
        assert_eq!(fetched.html, html);
        assert_eq!(fetched.snapshot.text, "Kept text");
        assert_eq!(fetched.snapshot.candidates[0].title, "Actual TLS snapshot");
        assert_eq!(fetched.snapshot.candidates.len(), 1);
        server.await.unwrap();
    }
}
