use std::io::{Cursor, Read};
use quick_xml::{Reader, events::Event};

pub fn archive_directory(bytes: &[u8]) -> Result<String, String> {
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "ZIP 文件结构无效")?;
    if zip.len() > 2000 { return Err("ZIP 条目超过预览限制".into()); }
    let mut lines = Vec::new();
    for index in 0..zip.len() {
        let item = zip.by_index(index).map_err(|_| "ZIP 目录读取失败")?;
        if item.enclosed_name().is_none() { return Err("ZIP 包含不安全路径".into()); }
        lines.push(format!("{}{}", item.name(), if item.is_dir() { String::new() } else { format!("  ({} bytes)", item.size()) }));
    }
    lines.sort();
    Ok(lines.join("\n"))
}

pub fn extract(bytes: &[u8]) -> Result<String, String> {
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(|_| "DOCX 文件结构无效")?;
    if zip.len() > 2000 { return Err("DOCX 包含过多条目".into()); }
    let mut parts = Vec::new();
    for index in 0..zip.len() {
        let item = zip.by_index(index).map_err(|_| "DOCX 条目不可读")?;
        if item.enclosed_name().is_none() { return Err("DOCX 包含不安全路径".into()); }
        let name = item.name();
        if name == "word/document.xml" || (name.starts_with("word/header") || name.starts_with("word/footer")) && name.ends_with(".xml") {
            parts.push(name.to_owned());
        }
    }
    parts.sort_by_key(|name| (name != "word/document.xml", name.clone()));
    let mut output = String::new();
    let mut total = 0;
    for name in parts {
        let item = zip.by_name(&name).map_err(|_| "DOCX 正文缺失")?;
        let mut xml = Vec::new();
        item.take(4 * 1024 * 1024 + 1).read_to_end(&mut xml).map_err(|_| "DOCX 正文读取失败")?;
        total += xml.len();
        if xml.len() > 4 * 1024 * 1024 || total > 12 * 1024 * 1024 { return Err("DOCX 正文超出预览限制".into()); }
        let mut reader = Reader::from_reader(xml.as_slice());
        let mut in_text = false;
        loop {
            match reader.read_event().map_err(|_| "DOCX XML 无效")? {
                Event::DocType(_) => return Err("DOCX 不允许外部实体".into()),
                Event::Start(e) => { in_text = e.local_name().as_ref() == b"t"; },
                Event::Text(e) if in_text => output.push_str(&e.unescape().map_err(|_| "DOCX 文本编码无效")?),
                Event::End(e) => { if e.local_name().as_ref() == b"p" { output.push('\n'); } in_text = false; },
                Event::Empty(e) => match e.local_name().as_ref() { b"tab" => output.push('\t'), b"br" | b"cr" => output.push('\n'), _ => {} },
                Event::Eof => break,
                _ => {},
            }
        }
    }
    if output.trim().is_empty() { return Err("DOCX 没有可读取的正文".into()); }
    Ok(output)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    fn archive(xml: &str) -> Vec<u8> {
        let mut writer = zip::ZipWriter::new(Cursor::new(Vec::new()));
        writer.start_file("word/document.xml", zip::write::SimpleFileOptions::default()).unwrap();
        writer.write_all(xml.as_bytes()).unwrap();
        writer.finish().unwrap().into_inner()
    }
    #[test]
    fn docx_reads_inert_text_and_zip_lists_without_extraction() {
        let bytes = archive("<w:document xmlns:w='urn:test'><w:p><w:r><w:t>正文 &amp; 内容</w:t></w:r></w:p></w:document>");
        assert_eq!(extract(&bytes).unwrap(), "正文 & 内容\n");
        assert!(archive_directory(&bytes).unwrap().contains("word/document.xml"));
        assert!(extract(&archive("<!DOCTYPE x SYSTEM 'file:///etc/passwd'><x/>" )).is_err());
        assert!(extract(b"not a zip").is_err());
    }
}
