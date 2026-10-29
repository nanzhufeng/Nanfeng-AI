//! Constant-memory verification for a workspace-owned immutable private asset.
use sha2::{Digest, Sha256};
use std::{
    io::Read,
    path::Path,
    sync::atomic::{AtomicBool, Ordering},
};

pub(crate) fn verify(
    path: &Path,
    expected_size: u64,
    digest: &str,
    cancelled: &AtomicBool,
) -> Result<(), String> {
    check_cancelled(cancelled)?;
    let mut file = std::fs::File::open(path).map_err(|_| "本地视频附件缺失")?;
    if file.metadata().map_err(|_| "本地视频附件不可用")?.len() != expected_size {
        return Err("本地视频附件校验不一致".into());
    }
    verify_reader(&mut file, expected_size, digest, cancelled)
}

pub(crate) fn check_cancelled(cancelled: &AtomicBool) -> Result<(), String> {
    if cancelled.load(Ordering::SeqCst) {
        Err("视频缩略图已取消".into())
    } else {
        Ok(())
    }
}

fn verify_reader(
    reader: &mut impl Read,
    expected_size: u64,
    digest: &str,
    cancelled: &AtomicBool,
) -> Result<(), String> {
    let mut hash = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    let mut total = 0u64;
    loop {
        check_cancelled(cancelled)?;
        let read = reader
            .read(&mut buffer)
            .map_err(|_| "本地视频附件读取失败")?;
        if read == 0 {
            break;
        }
        total = total
            .checked_add(read as u64)
            .ok_or("本地视频附件大小无效")?;
        if total > expected_size {
            return Err("本地视频附件校验不一致".into());
        }
        hash.update(&buffer[..read]);
    }
    check_cancelled(cancelled)?;
    if total != expected_size || hex::encode(hash.finalize()) != digest {
        return Err("本地视频附件校验不一致".into());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    struct Blocks<'a> {
        remaining: usize,
        calls: usize,
        cancelled: &'a AtomicBool,
        cancel_after: Option<usize>,
    }
    impl Read for Blocks<'_> {
        fn read(&mut self, buffer: &mut [u8]) -> std::io::Result<usize> {
            assert!(buffer.len() <= 64 * 1024);
            let count = self.remaining.min(buffer.len());
            buffer[..count].fill(7);
            self.remaining -= count;
            self.calls += 1;
            if self.cancel_after == Some(self.calls) {
                self.cancelled.store(true, Ordering::SeqCst);
            }
            Ok(count)
        }
    }
    #[test]
    fn verifies_large_reader_with_fixed_buffer_and_detects_length_or_digest_change() {
        let cancelled = AtomicBool::new(false);
        let size = 32 * 1024 * 1024 + 13;
        let mut expected = Sha256::new();
        for _ in 0..512 {
            expected.update([7u8; 64 * 1024]);
        }
        expected.update([7u8; 13]);
        let digest = hex::encode(expected.finalize());
        let mut reader = Blocks {
            remaining: size,
            calls: 0,
            cancelled: &cancelled,
            cancel_after: None,
        };
        verify_reader(&mut reader, size as u64, &digest, &cancelled).unwrap();
        assert!(reader.calls > 512);
        assert!(verify_reader(&mut &b"changed"[..], 7, &digest, &cancelled).is_err());
        assert!(verify_reader(&mut &b"changed"[..], 6, &digest, &cancelled).is_err());
        assert!(verify_reader(&mut &b"changed"[..], 8, &digest, &cancelled).is_err());
    }
    #[test]
    fn cancellation_stops_before_next_chunk_and_before_opening_a_path() {
        let cancelled = AtomicBool::new(false);
        let mut reader = Blocks {
            remaining: 8 * 1024 * 1024,
            calls: 0,
            cancelled: &cancelled,
            cancel_after: Some(1),
        };
        assert!(
            verify_reader(&mut reader, 8 * 1024 * 1024, "unused", &cancelled)
                .unwrap_err()
                .contains("取消")
        );
        assert_eq!(reader.calls, 1);
        assert!(verify(Path::new("not-opened"), 1, "unused", &cancelled)
            .unwrap_err()
            .contains("取消"));
    }
}
