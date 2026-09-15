use quick_xml::{events::Event, Reader};
use std::io::{Cursor, Read};

pub const MAX_ARCHIVE_ENTRIES: usize = 2000;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ArchiveEntry {
    pub path: String,
    pub is_directory: bool,
    pub byte_count: u64,
}

pub fn archive_entries(bytes: &[u8]) -> Result<Vec<ArchiveEntry>, String> {
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "ZIP 文件结构无效")?;
    if zip.len() > MAX_ARCHIVE_ENTRIES {
        return Err("ZIP 条目超过预览限制".into());
    }
    let mut entries = Vec::with_capacity(zip.len());
    for index in 0..zip.len() {
        let item = zip.by_index(index).map_err(|_| "ZIP 目录读取失败")?;
        let safe_path = item
            .enclosed_name()
            .ok_or_else(|| "ZIP 包含不安全路径".to_owned())?
            .to_string_lossy()
            .into_owned();
        entries.push(ArchiveEntry {
            path: safe_path,
            is_directory: item.is_dir(),
            byte_count: item.size(),
        });
    }
    entries.sort_by(|left, right| left.path.cmp(&right.path));
    Ok(entries)
}

pub fn read_archive_entry(
    bytes: &[u8],
    requested_path: &str,
    maximum_bytes: u64,
) -> Result<(ArchiveEntry, Vec<u8>), String> {
    if requested_path.is_empty() || requested_path.len() > 1024 || requested_path.contains('\0') {
        return Err("ZIP 内文件引用无效".into());
    }
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "ZIP 文件结构无效")?;
    if zip.len() > MAX_ARCHIVE_ENTRIES {
        return Err("ZIP 条目超过预览限制".into());
    }
    for index in 0..zip.len() {
        let item = zip.by_index(index).map_err(|_| "ZIP 目录读取失败")?;
        let safe_path = item
            .enclosed_name()
            .ok_or_else(|| "ZIP 包含不安全路径".to_owned())?
            .to_string_lossy()
            .into_owned();
        if safe_path != requested_path {
            continue;
        }
        if item.is_dir() {
            return Err("ZIP 内目录不能直接预览".into());
        }
        if item.size() > maximum_bytes {
            return Err("ZIP 内文件超出安全预览上限".into());
        }
        let entry = ArchiveEntry {
            path: safe_path,
            is_directory: false,
            byte_count: item.size(),
        };
        let mut output = Vec::with_capacity(item.size() as usize);
        item.take(maximum_bytes.saturating_add(1))
            .read_to_end(&mut output)
            .map_err(|_| "ZIP 内文件读取失败")?;
        if output.len() as u64 > maximum_bytes {
            return Err("ZIP 内文件超出安全预览上限".into());
        }
        return Ok((entry, output));
    }
    Err("ZIP 内文件不存在或已变化".into())
}

pub fn archive_directory(bytes: &[u8]) -> Result<String, String> {
    let lines = archive_entries(bytes)?
        .into_iter()
        .map(|item| {
            format!(
                "{}{}",
                item.path,
                if item.is_directory {
                    String::new()
                } else {
                    format!("  ({} bytes)", item.byte_count)
                }
            )
        })
        .collect::<Vec<_>>();
    Ok(lines.join("\n"))
}

pub fn extract(bytes: &[u8]) -> Result<String, String> {
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "DOCX 文件结构无效")?;
    if zip.len() > 2000 {
        return Err("DOCX 包含过多条目".into());
    }
    let mut parts = Vec::new();
    for index in 0..zip.len() {
        let item = zip.by_index(index).map_err(|_| "DOCX 条目不可读")?;
        if item.enclosed_name().is_none() {
            return Err("DOCX 包含不安全路径".into());
        }
        let name = item.name();
        if name == "word/document.xml"
            || (name.starts_with("word/header") || name.starts_with("word/footer"))
                && name.ends_with(".xml")
        {
            parts.push(name.to_owned());
        }
    }
    parts.sort_by_key(|name| (name != "word/document.xml", name.clone()));
    let mut output = String::new();
    let mut total = 0;
    for name in parts {
        let item = zip.by_name(&name).map_err(|_| "DOCX 正文缺失")?;
        let mut xml = Vec::new();
        item.take(4 * 1024 * 1024 + 1)
            .read_to_end(&mut xml)
            .map_err(|_| "DOCX 正文读取失败")?;
        total += xml.len();
        if xml.len() > 4 * 1024 * 1024 || total > 12 * 1024 * 1024 {
            return Err("DOCX 正文超出预览限制".into());
        }
        let mut reader = Reader::from_reader(xml.as_slice());
        let mut in_text = false;
        loop {
            match reader.read_event().map_err(|_| "DOCX XML 无效")? {
                Event::DocType(_) => return Err("DOCX 不允许外部实体".into()),
                Event::Start(e) => {
                    in_text = e.local_name().as_ref() == b"t";
                }
                Event::Text(e) if in_text => {
                    output.push_str(&e.unescape().map_err(|_| "DOCX 文本编码无效")?)
                }
                Event::End(e) => {
                    if e.local_name().as_ref() == b"p" {
                        output.push('\n');
                    }
                    in_text = false;
                }
                Event::Empty(e) => match e.local_name().as_ref() {
                    b"tab" => output.push('\t'),
                    b"br" | b"cr" => output.push('\n'),
                    _ => {}
                },
                Event::Eof => break,
                _ => {}
            }
        }
    }
    if output.trim().is_empty() {
        return Err("DOCX 没有可读取的正文".into());
    }
    Ok(output)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    fn archive(xml: &str) -> Vec<u8> {
        let mut writer = zip::ZipWriter::new(Cursor::new(Vec::new()));
        writer
            .start_file(
                "word/document.xml",
                zip::write::SimpleFileOptions::default(),
            )
            .unwrap();
        writer.write_all(xml.as_bytes()).unwrap();
        writer.finish().unwrap().into_inner()
    }
    #[test]
    fn docx_reads_inert_text_and_zip_lists_without_extraction() {
        let bytes = archive("<w:document xmlns:w='urn:test'><w:p><w:r><w:t>正文 &amp; 内容</w:t></w:r></w:p></w:document>");
        assert_eq!(extract(&bytes).unwrap(), "正文 & 内容\n");
        assert!(archive_directory(&bytes)
            .unwrap()
            .contains("word/document.xml"));
        assert!(extract(&archive("<!DOCTYPE x SYSTEM 'file:///etc/passwd'><x/>")).is_err());
        assert!(extract(b"not a zip").is_err());
    }

    #[test]
    fn archive_entries_keep_safe_paths_and_bound_entry_reads() {
        let bytes = archive("local");
        let entries = archive_entries(&bytes).unwrap();
        assert_eq!(entries[0].path, "word/document.xml");
        assert_eq!(
            read_archive_entry(&bytes, "word/document.xml", 5)
                .unwrap()
                .1,
            b"local"
        );
        assert!(read_archive_entry(&bytes, "word/document.xml", 4)
            .unwrap_err()
            .contains("上限"));
        assert!(read_archive_entry(&bytes, "../word/document.xml", 8).is_err());
    }
}
